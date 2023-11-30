package mission.impossibl.bots

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, Behavior}

object GarbageOrchestrator {
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

  sealed trait Command

  final case class Instance(garbageCollectors: List[ActorRef[GarbageCollector.Command]])

  final case class GarbageCollectionRequest() extends Command
}
