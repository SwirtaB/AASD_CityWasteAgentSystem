package mission.impossibl.bots

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, Behavior}
import mission.impossibl.bots.orchestrator.{AuctionOffer, GarbageOrchestrator}
import mission.impossibl.bots.orchestrator.GarbageOrchestrator.GarbageCollectionProposal

import java.util.UUID

object GarbageCollector {
  def apply(instance: Instance, initialLocation: (Int, Int)): Behavior[Command] = {
    collector(instance, State(initialLocation))
  }

  private def collector(instance: Instance, state: State): Behavior[Command] =
    Behaviors.receive {
      (context, message) => {
        message match {
          case AttachOrchestrator(orchestratorId, orchestratorRef) =>
            context.log.info("Collector{} attached to Orchestrator{}", instance.id, orchestratorId)
            orchestratorRef ! GarbageOrchestrator.GarbageCollectorRegistered(context.self)
            collector(instance.copy(orchestrator = orchestratorRef), state)

          case GarbageCollectionCallForProposal(auctionId, sourceId, sourceLocation, garbageAmount) =>
            context.log.info("Received Garbage Collection CFP for source {} and amount {}", sourceId, garbageAmount)
            //TODO should update state to "reserve" space for this garbage
            //TODO add logic for offer creation
            val auctionOffer = AuctionOffer(context.self)
            instance.orchestrator ! GarbageCollectionProposal(auctionId, auctionOffer)
            Behaviors.same
          case GarbageCollectionAccepted(auctionId, _) =>
            context.log.info("GC Accepted for auction id {}", auctionId)
            Behaviors.same
          case GarbageCollectionRejected(auctionId) =>
            context.log.info("GC Rejected for auction id {}", auctionId)
            Behaviors.same
        }
      }
    }

  sealed trait Command

  final case class Instance(id: Int, capacity: Int, orchestrator: ActorRef[GarbageOrchestrator.Command])

  final case class State(currentLocation: (Int, Int))

  final case class GarbageCollectionCallForProposal(auctionId: UUID, sourceId: Int, sourceLocation: (Int, Int), garbageAmount: Int) extends Command

  final case class AttachOrchestrator(orchestratorId: Int, orchestratorRef: ActorRef[GarbageOrchestrator.Command]) extends Command

  //todo extend with any other needed info
  final case class GarbageCollectionAccepted(auctionId: UUID, sourceRef: ActorRef[WasteSource.Command]) extends Command
  final case class GarbageCollectionRejected(auctionId: UUID) extends Command
}
