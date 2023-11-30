package mission.impossibl.bots

import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.scaladsl.Behaviors

object WasteSource {
  val DisposalPercentFull = 0.7

  def apply(instance: Instance): Behavior[Command] = {
    source(instance, State(0, 0))
  }

  private def source(instance: Instance, state: State): Behavior[Command] =
    Behaviors.receive {
      (context, message) => {
        message match {
          case CheckGarbageLevel() =>
            context.log.info("Checking garbage level")
            if (state.garbage > DisposalPercentFull * instance.capacity) {
              instance.orchestrator ! GarbageOrchestrator.GarbageCollectionRequest()
            }
            source(instance, state)
          case ProduceGarbage(amount) => // simulate garbage production
            context.log.info(s"New garbage in town! {}, current amount: {}", amount, state.garbage + amount)
            context.self ! CheckGarbageLevel()
            source(instance, State(state.garbage + amount, state.score))
        }
      }
    }

  sealed trait Command

  final case class Instance(id: Int, location: (Int, Int), capacity: Int, orchestrator: ActorRef[GarbageOrchestrator.Command])

  final case class State(garbage: Int, score: Int)

  final case class ProduceGarbage(amount: Int) extends Command

  final case class CheckGarbageLevel() extends Command
}
