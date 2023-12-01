package mission.impossibl.bots

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, Behavior}

object GarbageOrchestrator {
  def apply(instance: Instance): Behavior[Command] = {
    val initialState = State(List.empty[ActorRef[GarbageCollector.Command]])
    orchestrator(instance, initialState)
  }

  private def orchestrator(instance: Instance, state: State): Behavior[Command] =
    Behaviors.receive {
      (context, message) => {
        message match {
          case GarbageCollectionRequest(sourceId, sourceLocation, _, garbageAmount) =>
            context.log.info("Orchestrator{} received request to collect garbage from Source{}", instance.id, sourceId)
            for (gc <- state.garbageCollectors) {
              gc ! GarbageCollector.GarbageCollectionCallForProposal(sourceId, sourceLocation, garbageAmount)
            }
            Behaviors.same
          case GarbageCollectorRegistered(garbageCollector) =>
            val newState = state.copy(garbageCollectors=state.garbageCollectors :+ garbageCollector)
            orchestrator(instance, newState)
        }
      }
    }

  sealed trait Command

  final case class Instance(id: Int)

  final case class State(garbageCollectors: List[ActorRef[GarbageCollector.Command]])

  final case class GarbageCollectionRequest(sourceId: Int, sourceLocation: (Int, Int), sourceRef: ActorRef[WasteSource.Command], garbageAmount: Int) extends Command

  final case class GarbageCollectorRegistered(garbageCollector: ActorRef[GarbageCollector.Command]) extends Command
}
