package mission.impossibl.bots

import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.Behaviors

object GarbageOrchestrator {
  sealed trait Command

  final case class GarbageCollectionRequest() extends Command

  def apply(): Behavior[Command] = orchestrator()

  private def orchestrator(): Behavior[Command] =
    Behaviors.receive {
      (context, message) => {
        message match {
          case GarbageCollectionRequest() =>
            context.log.info("Receive request to collect garbage")
            orchestrator()
        }
      }
    }
}
