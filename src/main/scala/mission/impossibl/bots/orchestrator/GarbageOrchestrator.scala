package mission.impossibl.bots.orchestrator

import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
import akka.actor.typed.{ActorRef, Behavior}
import mission.impossibl.bots.collector.{DisposalPoint, GarbageCollector}
import mission.impossibl.bots.collector.GarbageCollector.{DisposalAuctionResponse, GarbageCollectionAccepted, GarbageCollectionRejected}
import mission.impossibl.bots.sink.WasteSink
import mission.impossibl.bots.sink.WasteSink.{GarbageDisposalAccepted, GarbageDisposalRejected}
import mission.impossibl.bots.source.WasteSource

import java.util.UUID
import scala.concurrent.duration._

object GarbageOrchestrator {
  //todo:
  // - ensure collector proritizes disposal when possible
  // - should not take part in collection auctions if there is disposal point in state

  private val AuctionTimeoutVal = 1.seconds

  def apply(instance: Instance): Behavior[Command] = {
    val initialState = State(
      List.empty[ActorRef[GarbageCollector.Command]],
      List.empty[ActorRef[WasteSink.Command]]
    )
    orchestrator(instance, initialState)
  }

  private def orchestrator(instance: Instance, state: State): Behavior[Command] =
    Behaviors.receive { (context, message) =>
      implicit val ac: ActorContext[Command] = context
      message match {
        case GarbageCollectorRegistered(garbageCollector) =>
          val newState = state.copy(garbageCollectors = state.garbageCollectors :+ garbageCollector)
          orchestrator(instance, newState)

        case WasteSinkRegistered(wasteSink) =>
          val newState = state.copy(wasteSinks = state.wasteSinks :+ wasteSink)
          orchestrator(instance, newState)

        case GarbageCollectionRequest(sourceId, sourceLocation, sourceRef, garbageAmount) =>
          context.log.info("[Collection] received request to collect garbage from Source{}", sourceId)
          val auction = initCollectionAuction(CollectionDetails(garbageAmount, sourceLocation, sourceId, sourceRef), state.garbageCollectors)
          orchestrator(instance, state.copy(collectionAuctionsInProgress = state.collectionAuctionsInProgress.updated(auction.auctionId, auction)))

        case GarbageDisposalRequest(collectorId, garbageAmount, collectorRef) =>
          context.log.info("[Disposal]  received request to dispose garbage from Collector{}", collectorId)
          val auction = initDisposalAuction(DisposalDetails(garbageAmount, collectorId), collectorRef, state.wasteSinks)
          orchestrator(instance, state.copy(disposalAuctionsInProgress = state.disposalAuctionsInProgress.updated(auction.auctionId, auction)))

        case GarbageCollectionProposal(auctionId, auctionOffer) =>
          context.log.info("[Collection] Received proposal for auction {} from {}", auctionId, auctionOffer.gcRef)
          progressCollectionAuction(auctionId, auctionOffer, state, instance)

        case GarbageDisposalProposal(auctionId, auctionOffer) =>
          context.log.info("[Disposal] Received proposal for auction {} from {}", auctionId, auctionOffer.wasteSink)
          progressDisposalAuction(auctionId, auctionOffer, state, instance)

        case AuctionTimeout(auctionId) =>
          context.log.info("[Any] Auction timeout {}", auctionId)
          state.collectionAuctionsInProgress.get(auctionId).orElse(state.disposalAuctionsInProgress.get(auctionId)) match {
            case Some(auction: CollectionAuction) =>
              resolveCollectionAuction(auction)
              orchestrator(instance, state.copy(collectionAuctionsInProgress = state.collectionAuctionsInProgress.removed(auctionId)))
            case Some(auction: DisposalAuction) =>
              resolveDisposalAuction(auction)
              orchestrator(instance, state.copy(disposalAuctionsInProgress = state.disposalAuctionsInProgress.removed(auctionId)))
            case None => Behaviors.same
          }
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
    state.collectionAuctionsInProgress.get(auctionId) match {
      case Some(auction) =>
        val updatedAuction = auction.copy(received = auction.received.appended(auctionOffer))
        if (updatedAuction.received.length == auction.expected) {
          // all offers received
          context.log.info("[Collection] All offers received for auction {}", auctionId)
          updatedAuction.timeoutRef.cancel()
          resolveCollectionAuction(updatedAuction)
          orchestrator(instance, state.copy(collectionAuctionsInProgress = state.collectionAuctionsInProgress.removed(auctionId)))
        } else {
          orchestrator(instance, state.copy(collectionAuctionsInProgress = state.collectionAuctionsInProgress.updated(auctionId, updatedAuction)))
        }
      case None =>
        context.log.info("[Collection] Offer for {} does not match any auction", auctionId)
        auctionOffer.gcRef ! GarbageCollectionRejected(auctionId)
        Behaviors.same
    }

  private def progressDisposalAuction(auctionId: UUID, auctionOffer: DisposalAuctionOffer, state: State, instance: Instance)(implicit context: ActorContext[Command]): Behavior[Command] =
    state.disposalAuctionsInProgress.get(auctionId) match {
      case Some(auction) =>
        val updatedAuction = auction.copy(received = auction.received.appended(auctionOffer))
        if (updatedAuction.received.length == auction.expected) {
          // all offers received
          context.log.info("[Disposal] All offers received for auction {}", auctionId)
          updatedAuction.timeoutRef.cancel()
          if (updatedAuction.received.nonEmpty) {
            resolveDisposalAuction(updatedAuction)
          }
          orchestrator(instance, state.copy(disposalAuctionsInProgress = state.disposalAuctionsInProgress.removed(auctionId)))
        } else {
          context.log.info("[Disposal] Received auction offer from {} ", auctionOffer.wasteSink)
          orchestrator(instance, state.copy(disposalAuctionsInProgress = state.disposalAuctionsInProgress.updated(auctionId, updatedAuction)))
        }
      case None =>
        context.log.info("[Disposal] Offer for {} does not match any auction", auctionId)
        auctionOffer.wasteSink ! GarbageDisposalRejected(auctionId)
        Behaviors.same
    }

  private def resolveCollectionAuction(auction: CollectionAuction)(implicit context: ActorContext[Command]): Unit = {
    if (auction.received.isEmpty)
      return
    val sortedOffers = auction.received.sortBy(-_.when)
    context.log.info("[Collection] Winning offer for auction {} from {}", auction.auctionId, sortedOffers.head.gcRef)
    sortedOffers.head.gcRef ! GarbageCollectionAccepted(auction.auctionId, auction.collectionDetails.sourceId, auction.collectionDetails.sourceRef)
    context.log.info("[Collection] Rejecting offers for auction {} from {}", auction.auctionId, auction.received.drop(1))
    sortedOffers.drop(1).foreach(_.gcRef ! GarbageCollectionRejected(auction.auctionId))
  }

  private def resolveDisposalAuction(auction: DisposalAuction)(implicit context: ActorContext[Command]): Unit = {
    // todo proper winning offer choice algorithm
    context.log.info("[Disposal] Winning offer for auction {} from {}", auction.auctionId, auction.received.head.wasteSink)
    val winningOffer = auction.received.head
    winningOffer.wasteSink ! GarbageDisposalAccepted(auction.auctionId)
    auction.collectorRef ! DisposalAuctionResponse(DisposalPoint(winningOffer.location, winningOffer.wasteSink))
    context.log.info("[Disposal] Rejecting offers for auction {} from {}", auction.auctionId, auction.received.drop(1))
    auction.received.drop(1).foreach(_.wasteSink ! GarbageDisposalRejected(auction.auctionId))
  }

  sealed trait Command

  final case class GarbageCollectionRequest(sourceId: UUID, sourceLocation: (Int, Int), sourceRef: ActorRef[WasteSource.Command], garbageAmount: Int) extends Command

  final case class GarbageDisposalRequest(collectorId: UUID, garbageAmount: Int, collectorRef: ActorRef[GarbageCollector.Command]) extends Command

  final case class GarbageCollectorRegistered(garbageCollector: ActorRef[GarbageCollector.Command]) extends Command

  final case class WasteSinkRegistered(wasteSink: ActorRef[WasteSink.Command]) extends Command

  final case class GarbageCollectionProposal(auctionId: UUID, auctionOffer: CollectionAuctionOffer) extends Command

  final case class GarbageDisposalProposal(auctionId: UUID, auctionOffer: DisposalAuctionOffer) extends Command

  private final case class AuctionTimeout(auctionId: UUID) extends Command
}
