package mission.impossibl.bots.orchestrator

import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
import akka.actor.typed.{ActorRef, Behavior}
import mission.impossibl.bots.collector.GarbageCollector
import mission.impossibl.bots.collector.GarbageCollector.{GarbageCollectionAccepted, GarbageCollectionRejected}
import mission.impossibl.bots.sink.WasteSink
import mission.impossibl.bots.sink.WasteSink.{GarbageDisposalAccepted, GarbageDisposalRejected}
import mission.impossibl.bots.source.WasteSource
import mission.impossibl.bots.source.WasteSource.GarbageScoreSummary

import java.util.UUID
import scala.concurrent.duration._

object GarbageOrchestrator {

  private val AuctionTimeoutVal = 1.seconds

  def apply(instance: Instance): Behavior[Command] = {
    val initialState = State(
      List.empty[ActorRef[GarbageCollector.Command]]
      )
    orchestrator(instance, initialState)
  }

  private def orchestrator(instance: Instance, state: State): Behavior[Command] =
    Behaviors.receive {
      (context, message) => {
        implicit val ac: ActorContext[Command] = context
        message match {
          case GarbageCollectorRegistered(garbageCollector) =>
            val newState = state.copy(garbageCollectors = state.garbageCollectors :+ garbageCollector)
            orchestrator(instance, newState)
          case WasteSourceRegistered(wasteSource, sourceId) =>
            val newState = state.copy(wasteSources = state.wasteSources.updated(sourceId, wasteSource))
            orchestrator(instance, newState)

          case WasteSinkRegistered(wasteSink) =>
            val newState = state.copy(wasteSinks = state.wasteSinks :+ wasteSink)
            orchestrator(instance, newState)

          case GarbageCollectionRequest(sourceId, sourceLocation, sourceRef, garbageAmount) =>
            context.log.info("Orchestrator {} received request to collect garbage from Source {}", instance.id, sourceId)
            val auction = initAuction(GarbageToCollect(garbageAmount, sourceLocation, sourceId, sourceRef), state.garbageCollectors)
            orchestrator(instance, state.copy(auctionsInProgress = state.auctionsInProgress.updated(auction.auctionId, auction), wasteSources = state.wasteSources.updated(sourceId, sourceRef)))

          case GarbageCollectionProposal(auctionId, auctionOffer) =>
            context.log.info("Received proposal for auction {} from {}", auctionId, auctionOffer.gcRef)
            progressCollectionAuction(auctionId, auctionOffer, state, instance)

          case GarbageDisposalProposal(auctionId, auctionOffer) =>
            context.log.info("Received proposal for auction {} from {}", auctionId, auctionOffer.wasteSink)
            progressDisposalAuction(auctionId, auctionOffer, state, instance)

          case AuctionTimeout(auctionId) =>
            context.log.info("Auction timeout {}", auctionId)
            state.collectionAuctionsInProgress.get(auctionId) match {
              case Some(auction) =>
                resolveCollectionAuction(auction)
                orchestrator(instance, state.copy(collectionAuctionsInProgress = state.collectionAuctionsInProgress.removed(auctionId)))
              case None => Behaviors.same
            }

          case GarbageScore(sourceId: Int, garbage_score: Int) =>
            context.log.info("Waste source with id {} got score {}", sourceId, garbage_score)
            state.wasteSources.get(sourceId).map(_ ! GarbageScoreSummary(garbage_score))
            Behaviors.same
        }
      }
    }

  private def initCollectionAuction(collectionDetails: CollectionDetails,
                                    garbageCollectorList: List[ActorRef[GarbageCollector.Command]])
                                   (implicit context: ActorContext[Command]): CollectionAuction = {
    val auctionId = UUID.randomUUID()
    val scheduledTimeout = context.scheduleOnce(AuctionTimeoutVal, context.self, AuctionTimeout(auctionId))

    garbageCollectorList.foreach(_ ! GarbageCollector.GarbageCollectionCallForProposal(auctionId, collectionDetails.sourceId, collectionDetails.location, collectionDetails.garbageAmount))
    context.log.info("Starting auction {}, expecting {} offers ", auctionId, garbageCollectorList.length)
    CollectionAuction(auctionId, garbageCollectorList.length, List.empty[CollectionAuctionOffer], scheduledTimeout, collectionDetails)
  }

  private def initDisposalAuction(disposalDetails: DisposalDetails,
                                  wasteSinkList: List[ActorRef[WasteSink.Command]])
                                 (implicit context: ActorContext[Command]): DisposalAuction = {
    val auctionId = UUID.randomUUID()
    val scheduledTimeout = context.scheduleOnce(AuctionTimeoutVal, context.self, AuctionTimeout(auctionId))

    wasteSinkList.foreach(_ ! WasteSink.GarbageDisposalCallForProposal(auctionId, disposalDetails.collectorId, disposalDetails.garbageAmount))
    context.log.info("Starting auction {}, expecting {} offers ", auctionId, wasteSinkList.length)
    DisposalAuction(auctionId, wasteSinkList.length, List.empty[DisposalAuctionOffer], scheduledTimeout, disposalDetails)
  }

  private def progressCollectionAuction(auctionId: UUID, auctionOffer: CollectionAuctionOffer, state: State, instance: Instance)(implicit context: ActorContext[Command]): Behavior[Command] =
    state.collectionAuctionsInProgress.get(auctionId) match {
      case Some(auction) =>
        val updatedAuction = auction.copy(received = auction.received.appended(auctionOffer))
        if (updatedAuction.received.length == auction.expected) {
          //all offers received
          context.log.info("All offers received for auction {}", auctionId)
          updatedAuction.timeoutRef.cancel()
          resolveCollectionAuction(updatedAuction)
          orchestrator(instance, state.copy(collectionAuctionsInProgress = state.collectionAuctionsInProgress.removed(auctionId)))
        } else {
          orchestrator(instance, state.copy(collectionAuctionsInProgress = state.collectionAuctionsInProgress.updated(auctionId, updatedAuction)))
        }
      case None =>
        context.log.info("Offer for {} does not match any auction", auctionId)
        auctionOffer.gcRef ! GarbageCollectionRejected(auctionId)
        Behaviors.same
    }

  private def progressDisposalAuction(auctionId: UUID, auctionOffer: DisposalAuctionOffer, state: State, instance: Instance)(implicit context: ActorContext[Command]): Behavior[Command] =
    state.disposalAuctionsInProgress.get(auctionId) match {
      case Some(auction) =>
        val updatedAuction = auction.copy(received = auction.received.appended(auctionOffer))
        if (updatedAuction.received.length == auction.expected) {
          //all offers received
          context.log.info("All offers received for auction {}", auctionId)
          updatedAuction.timeoutRef.cancel()
          resolveDisposalAuction(updatedAuction)
          orchestrator(instance, state.copy(disposalAuctionsInProgress = state.disposalAuctionsInProgress.removed(auctionId)))
        } else {
          orchestrator(instance, state.copy(disposalAuctionsInProgress = state.disposalAuctionsInProgress.updated(auctionId, updatedAuction)))
        }
      case None =>
        context.log.info("Offer for {} does not match any auction", auctionId)
        auctionOffer.wasteSink ! GarbageDisposalRejected(auctionId)
        Behaviors.same
    }

  private def resolveCollectionAuction(auction: CollectionAuction)(implicit context: ActorContext[Command]): Unit = {
    if (auction.received.isEmpty)
      return
    //todo proper winning offer choice algorithm
    context.log.info("Winning offer for auction {} from {}", auction.auctionId, auction.received.head.gcRef)
    auction.received.head.gcRef ! GarbageCollectionAccepted(auction.auctionId, auction.collectionDetails.sourceRef)
    context.log.info("Rejecting offers for auction {} from {}", auction.auctionId, auction.received.drop(1))
    auction.received.drop(1).foreach(_.gcRef ! GarbageCollectionRejected(auction.auctionId))
  }

  private def resolveDisposalAuction(auction: DisposalAuction)(implicit context: ActorContext[Command]): Unit = {
    if (auction.received.isEmpty)
      return
    //todo proper winning offer choice algorithm
    context.log.info("Winning offer for auction {} from {}", auction.auctionId, auction.received.head.wasteSink)
    auction.received.head.wasteSink ! GarbageDisposalAccepted(auction.auctionId)
    context.log.info("Rejecting offers for auction {} from {}", auction.auctionId, auction.received.drop(1))
    auction.received.drop(1).foreach(_.wasteSink ! GarbageDisposalRejected(auction.auctionId))
  }

  sealed trait Command

  final case class GarbageCollectionRequest(sourceId: Int, sourceLocation: (Int, Int), sourceRef: ActorRef[WasteSource.Command], garbageAmount: Int) extends Command

  final case class GarbageDisposalRequest(collectorId: Int, garbageAmount: Int) extends Command

  final case class GarbageCollectorRegistered(garbageCollector: ActorRef[GarbageCollector.Command]) extends Command

  final case class WasteSourceRegistered(wasteSource: ActorRef[WasteSource.Command], sourceId: Int) extends Command

  final case class GarbageCollectionProposal(auctionId: UUID, auctionOffer: AuctionOffer) extends Command

  final case class GarbageScore(sourceId: Int, garbage_score: Int) extends Command

  private final case class AuctionTimeout(auctionId: UUID) extends Command
}
