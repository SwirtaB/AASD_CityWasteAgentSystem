package mission.impossibl.bots

import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.Behaviors

object GarbageCollector {
  sealed trait Command

  final case class GarbageCollectionCallForProposal() extends Command

  def apply(): Behavior[Command] = collector()

  private def collector(): Behavior[Command] =
    Behaviors.receive {
      (context, message) => {
        message match {
          case GarbageCollectionCallForProposal() =>
            context.log.info("Received garbage collection CFP")
            collector()
        }
      }
    }
}
