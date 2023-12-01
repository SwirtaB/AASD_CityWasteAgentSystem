package mission.impossibl.bots

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, Behavior}

object GarbageCollector {
  def apply(instance: Instance, initialLocation: (Int, Int)): Behavior[Command] = {
    collector(instance, State(initialLocation))
  }

  private def collector(instance: Instance, state: State): Behavior[Command] =
    Behaviors.receive {
      (context, message) => {
        message match {
          case GarbageCollectionCallForProposal(sourceId, _, garbageAmount) =>
            context.log.info(
              "Collector{} received CFP for collection of {} units of garbage from Source{}",
              instance.id, garbageAmount, sourceId
            )
            Behaviors.same
          case AttachOrchestrator(orchestratorId, orchestratorRef) =>
            context.log.info("Collector{} attached to Orchestrator{}", instance.id, orchestratorId)
            orchestratorRef ! GarbageOrchestrator.GarbageCollectorRegistered(context.self)
            collector(instance.copy(orchestrator = orchestratorRef), state)
        }
      }
    }

  sealed trait Command

  final case class Instance(id: Int, capacity: Int, orchestrator: ActorRef[GarbageOrchestrator.Command])

  final case class State(currentLocation: (Int, Int))

  final case class GarbageCollectionCallForProposal(sourceId: Int, sourceLocation: (Int, Int), garbageAmount: Int) extends Command

  final case class AttachOrchestrator(orchestratorId: Int, orchestratorRef: ActorRef[GarbageOrchestrator.Command]) extends Command
}
