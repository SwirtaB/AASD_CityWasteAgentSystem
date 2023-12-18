package mission.impossibl.bots.orchestrator

import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
import akka.actor.typed.{ActorRef, Behavior}
import mission.impossibl.bots.collector.GarbageCollector
import mission.impossibl.bots.collector.GarbageCollector.{GarbageCollectionAccepted, GarbageCollectionRejected}
import mission.impossibl.bots.source.WasteSource

import java.util.UUID
import scala.concurrent.duration._

object GarbageOrchestrator {

  private val AuctionTimeoutVal = 1.seconds

  def apply(instance: Instance): Behavior[Command] = {
    val initialState = State(List.empty[ActorRef[GarbageCollector.Command]])
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

          case GarbageCollectionRequest(sourceId, sourceLocation, sourceRef, garbageAmount) =>
            context.log.info("Orchestrator {} received request to collect garbage from Source {}", instance.id, sourceId)
            val auction = initCollectionAuction(GarbageToCollect(garbageAmount, sourceLocation, sourceId, sourceRef), state.garbageCollectors)
            orchestrator(instance, state.copy(collectionAuctionsInProgress = state.collectionAuctionsInProgress.updated(auction.auctionId, auction)))

          case GarbageCollectionProposal(auctionId, auctionOffer) =>
            context.log.info("Received proposal for auction {} from {}", auctionId, auctionOffer.gcRef)
            progressCollectionAuction(auctionId, auctionOffer, state, instance)

          case CollectionAuctionTimeout(auctionId) =>
            context.log.info("Auction timeout {}", auctionId)
            state.collectionAuctionsInProgress.get(auctionId) match {
              case Some(auction) =>
                resolveCollectionAuction(auction)
                orchestrator(instance, state.copy(collectionAuctionsInProgress = state.collectionAuctionsInProgress.removed(auctionId)))
              case None => Behaviors.same
            }
        }
      }
    }

  private def initCollectionAuction(gcInfo: GarbageToCollect,
                                    gcs: List[ActorRef[GarbageCollector.Command]])
                                   (implicit context: ActorContext[Command]): CollectionAuction = {
    val auctionId = UUID.randomUUID()
    val scheduledTimeout = context.scheduleOnce(AuctionTimeoutVal, context.self, CollectionAuctionTimeout(auctionId))

    gcs.foreach(_ ! GarbageCollector.GarbageCollectionCallForProposal(auctionId, gcInfo.sourceId, gcInfo.location, gcInfo.garbageAmount))
    context.log.info("Starting auction {}, expecting {} offers ", auctionId, gcs.length)
    CollectionAuction(auctionId, gcs.length, List.empty[CollectionAuctionOffer], scheduledTimeout, gcInfo)
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

  private def resolveCollectionAuction(auction: CollectionAuction)(implicit context: ActorContext[Command]): Unit = {
    if (auction.received.nonEmpty) {
      //todo proper winning offer choice algorithm
      context.log.info("Winning offer for auction {} from {}", auction.auctionId, auction.received.head.gcRef)
      auction.received.head.gcRef ! GarbageCollectionAccepted(auction.auctionId, auction.collectionInfo.sourceRef)
      context.log.info("Rejecting offers for auction {} from {}", auction.auctionId, auction.received.drop(1))
      auction.received.drop(1).foreach(_.gcRef ! GarbageCollectionRejected(auction.auctionId))
    }
  }

  sealed trait Command

  final case class GarbageCollectionRequest(sourceId: Int, sourceLocation: (Int, Int), sourceRef: ActorRef[WasteSource.Command], garbageAmount: Int) extends Command

  final case class GarbageCollectorRegistered(garbageCollector: ActorRef[GarbageCollector.Command]) extends Command

  final case class GarbageCollectionProposal(auctionId: UUID, auctionOffer: CollectionAuctionOffer) extends Command

  private final case class CollectionAuctionTimeout(auctionId: UUID) extends Command
}
