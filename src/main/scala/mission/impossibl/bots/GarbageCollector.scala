package mission.impossibl.bots

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, Behavior}

object GarbageCollector {
  def apply(instance: Instance, initialLocation: (Int, Int)): Behavior[Command] = collector(instance, State(initialLocation))

  private def collector(instance: Instance, state: State): Behavior[Command] =
    Behaviors.receive {
      (context, message) => {
        message match {
          case GarbageCollectionCallForProposal() =>
            context.log.info("Received garbage collection CFP")
            collector(instance, state)
          case AttachOrchestrator(orchestrator) =>
            context.log.info("Collector{} attached to Orchestrator {}", instance.id, orchestrator)
            collector(Instance(instance.id, instance.capacity, orchestrator), state)
        }
      }
    }

  sealed trait Command

  final case class Instance(id: Int, capacity: Int, orchestrator: ActorRef[GarbageOrchestrator.Command])

  final case class State(currentLocation: (Int, Int))

  final case class GarbageCollectionCallForProposal() extends Command

  final case class AttachOrchestrator(orchestrator: ActorRef[GarbageOrchestrator.Command]) extends Command
}
