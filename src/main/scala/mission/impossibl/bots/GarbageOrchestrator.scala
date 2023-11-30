package mission.impossibl.bots

import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.scaladsl.Behaviors

object GarbageOrchestrator {
  final case class Instance(garbageCollectors: List[ActorRef[GarbageCollector.Command]])

  sealed trait Command

  final case class GarbageCollectionRequest() extends Command

  def apply(instance: Instance): Behavior[Command] = orchestrator(instance)

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
        }
      }
    }
}
