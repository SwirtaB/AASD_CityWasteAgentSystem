package mission.impossibl.bots.orchestrator

import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
import akka.actor.typed.{ActorRef, Behavior}
import mission.impossibl.bots.GarbageCollector.{GarbageCollectionAccepted, GarbageCollectionRejected}
import mission.impossibl.bots.{GarbageCollector, WasteSource}

import java.util.UUID
import scala.concurrent.duration._

object GarbageOrchestrator {

  val AuctionTimeoutVal = 1.seconds

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
            val auction = initAuction(GarbageCollectionInfo(sourceId, sourceLocation, garbageAmount, sourceRef), state.garbageCollectors)
            orchestrator(instance, state.copy(auctionsInProgress = state.auctionsInProgress.updated(auction.auctionId, auction)))
          case GarbageCollectionProposal(auctionId, auctionOffer) =>
            context.log.info("Received proposal for auction {} from {}", auctionId, auctionOffer.gcRef)
            progressAuction(auctionId, auctionOffer, state, instance)

          case AuctionTimeout(auctionId) =>
            context.log.info("Auction timeout {}", auctionId)
            state.auctionsInProgress.get(auctionId) match {
              case Some(auction) =>
                resolveAuction(auction)
                orchestrator(instance, state.copy(auctionsInProgress = state.auctionsInProgress.removed(auctionId)))
              case None => Behaviors.same
            }
        }
      }
    }


  private def initAuction(gcInfo: GarbageCollectionInfo,
                          gcs: List[ActorRef[GarbageCollector.Command]])
                         (implicit context: ActorContext[Command]): Auction = {
    val auctionId = UUID.randomUUID()
    val scheduledTimeout = context.scheduleOnce(AuctionTimeoutVal, context.self, AuctionTimeout(auctionId))

    gcs.foreach(_ ! GarbageCollector.GarbageCollectionCallForProposal(auctionId, gcInfo.sourceId, gcInfo.location, gcInfo.garbageAmount))
    context.log.info("Initing auction {}, expecting {} offers ", auctionId, gcs.length)
    Auction(auctionId, gcs.length, List.empty[AuctionOffer], scheduledTimeout, gcInfo)
  }

  private def progressAuction(auctionId: UUID, auctionOffer: AuctionOffer, state: State, instance: Instance)(implicit context: ActorContext[Command]): Behavior[Command] =
    state.auctionsInProgress.get(auctionId) match {
      case Some(auction) =>
        val updatedAuction = auction.copy(received = auction.received.appended(auctionOffer))
        if (updatedAuction.received.length == auction.expected) {
          //all offers received
          context.log.info("All offers received for auction {}", auctionId)
          updatedAuction.timeoutRef.cancel()
          resolveAuction(updatedAuction)
          orchestrator(instance, state.copy(auctionsInProgress = state.auctionsInProgress.removed(auctionId)))
        } else {
          orchestrator(instance, state.copy(auctionsInProgress = state.auctionsInProgress.updated(auctionId, updatedAuction)))
        }
      case None =>
        context.log.info("Offer for {} does not match any auction", auctionId)
        auctionOffer.gcRef ! GarbageCollectionRejected(auctionId)
        Behaviors.same
    }

  private def resolveAuction(auction: Auction)(implicit context: ActorContext[Command]): Unit = {
    if(auction.received.nonEmpty){
      //todo proper winning offer choice algorithm
      context.log.info("Winning offer for auction {} from {}", auction.auctionId, auction.received.head.gcRef)
      auction.received.head.gcRef ! GarbageCollectionAccepted(auction.auctionId, auction.collectionInfo.sourceRef)
      context.log.info("Rejecting offers for auction {} from {}", auction.auctionId, auction.received.drop(1))
      auction.received.drop(1).foreach(_.gcRef ! GarbageCollectionRejected(auction.auctionId))
    }
  }

  sealed trait Command

  final case class AuctionTimeout(auctionId: UUID) extends Command

  final case class GarbageCollectionRequest(sourceId: Int, sourceLocation: (Int, Int), sourceRef: ActorRef[WasteSource.Command], garbageAmount: Int) extends Command

  final case class GarbageCollectorRegistered(garbageCollector: ActorRef[GarbageCollector.Command]) extends Command

  final case class GarbageCollectionProposal(auctionId: UUID, auctionOffer: AuctionOffer) extends Command
}
