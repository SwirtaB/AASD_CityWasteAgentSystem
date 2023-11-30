package mission.impossibl.bots

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, Behavior}

object GarbageOrchestrator {
  def apply(instance: Instance): Behavior[Command] = {
    orchestrator(instance)
  }

  private def orchestrator(instance: Instance): Behavior[Command] =
    Behaviors.receive {
      (context, message) => {
        message match {
          case GarbageCollectionRequest() =>
            context.log.info("Received request to collect garbage")
            for (gc <- instance.garbageCollectors) {
              gc ! GarbageCollector.GarbageCollectionCallForProposal()
            }
            orchestrator(instance)
          case LateInitialize() =>
            context.log.info("Orchestrator{} late initialize", instance.id)
            for (gc <- instance.garbageCollectors) {
              gc ! GarbageCollector.AttachOrchestrator(context.self)
            }
            orchestrator(instance)
        }
      }
    }

  sealed trait Command

  final case class Instance(id: Int, garbageCollectors: List[ActorRef[GarbageCollector.Command]])

  final case class GarbageCollectionRequest() extends Command

  final case class LateInitialize() extends Command
}
