package mission.impossibl.bots.orchestrator

import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
import akka.actor.typed.{ActorRef, Behavior}
import mission.impossibl.bots.Utils.dist
import mission.impossibl.bots.collector.{DisposalPoint, GarbageCollector}
import mission.impossibl.bots.collector.GarbageCollector.{DisposalAuctionResponse, GarbageCollectionAccepted, GarbageCollectionRejected}
import mission.impossibl.bots.http.{AuctionStatus, CollectionDetailsResponse, DisposalDetailsResponse, OrchestratorStatus}
import mission.impossibl.bots.sink.WasteSink
import mission.impossibl.bots.sink.WasteSink.{GarbageDisposalAccepted, GarbageDisposalRejected}
import mission.impossibl.bots.source.WasteSource
import mission.impossibl.bots.source.WasteSource.GarbageScoreSummary

import java.util.UUID
import scala.concurrent.duration._

object GarbageOrchestrator {

  private val AuctionTimeoutVal = 1.seconds

  def apply(instance: Instance): Behavior[Command] =
    orchestrator(instance, State())

  private def orchestrator(instance: Instance, state: State): Behavior[Command] =
    Behaviors.receive { (context, message) =>
      implicit val ac: ActorContext[Command] = context
      message match {
        case GarbageCollectorRegistered(garbageCollector) =>
          val newState = state.copy(garbageCollectors = state.garbageCollectors :+ garbageCollector)
          orchestrator(instance, newState)
        case WasteSourceRegistered(wasteSource, sourceId) =>
          val newState = state.copy(wasteSources = state.wasteSources.updated(sourceId, wasteSource))
          orchestrator(instance, newState)

        case WasteSinkRegistered(wasteSink, _) =>
          val newState = state.copy(wasteSinks = state.wasteSinks :+ wasteSink)
          orchestrator(instance, newState)

        case GarbageCollectionRequest(sourceId, sourceLocation, sourceRef, garbageAmount) =>
          context.log.info("[Collection] received request to collect garbage from Source{}", sourceId)
          val auction = initCollectionAuction(CollectionDetails(garbageAmount, sourceLocation, sourceId, sourceRef), state.garbageCollectors)
          orchestrator(instance, state.copy(auctionsInProgress = state.auctionsInProgress.updated(auction.auctionId, auction)))

        case GarbageDisposalRequest(collectorId, garbageAmount, collectorRef, location) =>
          context.log.info("[Disposal]  received request to dispose garbage from Collector{}", collectorId)
          val auction = initDisposalAuction(DisposalDetails(garbageAmount, collectorId, location), collectorRef, state.wasteSinks)
          orchestrator(instance, state.copy(auctionsInProgress = state.auctionsInProgress.updated(auction.auctionId, auction)))

        case GarbageCollectionProposal(auctionId, auctionOffer) =>
          context.log.info("[Collection] Received proposal for auction {} from {}", auctionId, auctionOffer.collectorRef)
          progressCollectionAuction(auctionId, auctionOffer, state, instance)

        case GarbageDisposalProposal(auctionId, auctionOffer) =>
          context.log.info("[Disposal] Received proposal for auction {} from {}", auctionId, auctionOffer.wasteSink)
          progressDisposalAuction(auctionId, auctionOffer, state, instance)

        case AuctionTimeout(auctionId) =>
          context.log.warn("[Any] Auction timeout {}", auctionId)
          state.auctionsInProgress.get(auctionId).orElse(state.auctionsInProgress.get(auctionId)) match {
            case Some(auction: CollectionAuction) =>
              resolveCollectionAuction(auction)
              orchestrator(instance, state.copy(auctionsInProgress = state.auctionsInProgress.removed(auctionId)))
            case Some(auction: DisposalAuction) =>
              resolveDisposalAuction(auction)
              orchestrator(instance, state.copy(auctionsInProgress = state.auctionsInProgress.removed(auctionId)))
            case None => Behaviors.same
          }

        case GarbageScore(sourceId, garbageScore) =>
          context.log.info("Waste source with id {} got score {}", sourceId, garbageScore)
          state.wasteSources.get(sourceId).foreach(_ ! GarbageScoreSummary(garbageScore))
          Behaviors.same

        case Status(replyTo) =>
          replyTo ! OrchestratorStatus(
            instance.id,
            state.auctionsInProgress.values.map {
              case DisposalAuction(uuid, expected, received, _, details, _) =>
                AuctionStatus(
                  "Disposal",
                  uuid,
                  expected,
                  received.map(d => Left(d.location)),
                  DisposalDetailsResponse(details.garbageAmount, details.collectorId)
                )
              case CollectionAuction(uuid, expected, received, _, details) =>
                AuctionStatus(
                  "Disposal",
                  uuid,
                  expected,
                  received.map(d => Right(d.estimatedArrival)),
                  CollectionDetailsResponse(details.garbageAmount, details.location, details.sourceId)
                )

            }.toList
          )
          Behaviors.same
      }
    }

  private def initCollectionAuction(collectionDetails: CollectionDetails, garbageCollectorList: List[ActorRef[GarbageCollector.Command]])(implicit context: ActorContext[Command]): CollectionAuction = {
    val auctionId        = UUID.randomUUID()
    val scheduledTimeout = context.scheduleOnce(AuctionTimeoutVal, context.self, AuctionTimeout(auctionId))

    garbageCollectorList.foreach(_ ! GarbageCollector.GarbageCollectionCallForProposal(auctionId, collectionDetails.sourceId, collectionDetails.location, collectionDetails.garbageAmount))
    context.log.info("[Collection] Starting auction {}, expecting {} offers ", auctionId, garbageCollectorList.length)
    CollectionAuction(auctionId, garbageCollectorList.length, List.empty[CollectionAuctionOffer], scheduledTimeout, collectionDetails)
  }

  private def initDisposalAuction(disposalDetails: DisposalDetails, collectorRef: ActorRef[GarbageCollector.Command], wasteSinkList: List[ActorRef[WasteSink.Command]])(implicit context: ActorContext[Command]): DisposalAuction = {
    val auctionId        = UUID.randomUUID()
    val scheduledTimeout = context.scheduleOnce(AuctionTimeoutVal, context.self, AuctionTimeout(auctionId))

    wasteSinkList.foreach(_ ! WasteSink.GarbageDisposalCallForProposal(auctionId, disposalDetails.collectorId, disposalDetails.garbageAmount))
    context.log.info("[Disposal] Starting auction {}, expecting {} offers ", auctionId, wasteSinkList.length)
    DisposalAuction(auctionId, wasteSinkList.length, List.empty[DisposalAuctionOffer], scheduledTimeout, disposalDetails, collectorRef)
  }

  private def progressCollectionAuction(auctionId: UUID, auctionOffer: CollectionAuctionOffer, state: State, instance: Instance)(implicit context: ActorContext[Command]): Behavior[Command] =
    state.auctionsInProgress.get(auctionId) match {
      case Some(auction: CollectionAuction) =>
        val updatedAuction = auction.copy(received = auction.received.appended(auctionOffer))
        if (updatedAuction.received.length == auction.expected) {
          // all offers received
          context.log.info("[Collection] All offers received for auction {}", auctionId)
          updatedAuction.timeoutRef.cancel()
          resolveCollectionAuction(updatedAuction)
          orchestrator(instance, state.copy(auctionsInProgress = state.auctionsInProgress.removed(auctionId)))
        } else {
          orchestrator(instance, state.copy(auctionsInProgress = state.auctionsInProgress.updated(auctionId, updatedAuction)))
        }
      case None | Some(_: DisposalAuction) =>
        context.log.info("[Collection] Offer for {} does not match any auction", auctionId)
        auctionOffer.collectorRef ! GarbageCollectionRejected(auctionId)
        Behaviors.same
    }

  private def progressDisposalAuction(auctionId: UUID, auctionOffer: DisposalAuctionOffer, state: State, instance: Instance)(implicit context: ActorContext[Command]): Behavior[Command] =
    state.auctionsInProgress.get(auctionId) match {
      case Some(auction: DisposalAuction) =>
        val updatedAuction = auction.copy(received = auction.received.appended(auctionOffer))
        if (updatedAuction.received.length == auction.expected) {
          // all offers received
          context.log.info("[Disposal] All offers received for auction {}", auctionId)
          updatedAuction.timeoutRef.cancel()
          if (updatedAuction.received.nonEmpty) {
            resolveDisposalAuction(updatedAuction)
          }
          orchestrator(instance, state.copy(auctionsInProgress = state.auctionsInProgress.removed(auctionId)))
        } else {
          context.log.info("[Disposal] Received auction offer from {} ", auctionOffer.wasteSink)
          orchestrator(instance, state.copy(auctionsInProgress = state.auctionsInProgress.updated(auctionId, updatedAuction)))
        }
      case _ =>
        context.log.warn("[Disposal] Offer for {} does not match any auction", auctionId)
        auctionOffer.wasteSink ! GarbageDisposalRejected(auctionId)
        Behaviors.same
    }

  private def resolveCollectionAuction(auction: CollectionAuction)(implicit context: ActorContext[Command]): Unit =
    if (auction.received.nonEmpty) {
      var garbageLeft = auction.collectionDetails.garbageAmount
      val (accepted, rejected) = auction.received.partition { offer =>
        if (garbageLeft > 0) {
          garbageLeft -= offer.capacity
          true
        } else {
          false
        }
      }
      context.log.info("[Collection] Winning offers for auction {} from {}", auction.auctionId, accepted.map(_.collectorRef))
      if (accepted.size > 1) {
        context.log.warn("[Collection] More than one offer chosen")
      }
      accepted.foreach(_.collectorRef ! GarbageCollectionAccepted(auction.auctionId, auction.collectionDetails.sourceId, auction.collectionDetails.sourceRef))
      context.log.info("[Collection] Rejecting offers for auction {} from {}", auction.auctionId, auction.received.drop(1))
      rejected.foreach(_.collectorRef ! GarbageCollectionRejected(auction.auctionId))
    }

  private def resolveDisposalAuction(auction: DisposalAuction)(implicit context: ActorContext[Command]): Unit =
    if (auction.received.nonEmpty) {
      val sorted = auction.received.sortBy(offer => -dist(offer.location, auction.disposalDetails.location))
      context.log.info("[Disposal] Winning offer for auction {} from {}", auction.auctionId, sorted.head.wasteSink)
      val winningOffer = auction.received.head
      winningOffer.wasteSink ! GarbageDisposalAccepted(auction.auctionId)
      auction.collectorRef ! DisposalAuctionResponse(DisposalPoint(winningOffer.location, winningOffer.wasteSink, auction.auctionId))
      context.log.info("[Disposal] Rejecting offers for auction {} from {}", auction.auctionId, sorted.drop(1).map(_.wasteSink))
      sorted.drop(1).foreach(_.wasteSink ! GarbageDisposalRejected(auction.auctionId))
    }

  sealed trait Command

  final case class GarbageCollectionRequest(sourceId: UUID, sourceLocation: (Int, Int), sourceRef: ActorRef[WasteSource.Command], garbageAmount: Int) extends Command

  final case class GarbageDisposalRequest(collectorId: UUID, garbageAmount: Int, collectorRef: ActorRef[GarbageCollector.Command], location: (Int, Int)) extends Command

  final case class GarbageCollectorRegistered(garbageCollector: ActorRef[GarbageCollector.Command]) extends Command

  final case class WasteSourceRegistered(wasteSource: ActorRef[WasteSource.Command], sourceId: UUID) extends Command
  final case class WasteSinkRegistered(wasteSink: ActorRef[WasteSink.Command], sinkId: UUID)         extends Command

  final case class GarbageCollectionProposal(auctionId: UUID, auctionOffer: CollectionAuctionOffer) extends Command

  final case class GarbageDisposalProposal(auctionId: UUID, auctionOffer: DisposalAuctionOffer) extends Command

  final case class GarbageScore(sourceId: UUID, garbageScore: Int) extends Command

  private final case class AuctionTimeout(auctionId: UUID) extends Command

  final case class Status(replyTo: ActorRef[OrchestratorStatus]) extends Command
}
