package mission.impossibl.bots

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, Behavior}

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
            context.log.info("Source{}: Checking garbage level", instance.id)
            if (state.garbage > DisposalPercentFull * instance.capacity) {
              instance.orchestrator ! GarbageOrchestrator.GarbageCollectionRequest(
                instance.id, instance.location, context.self, state.garbage
              )
            }
            Behaviors.same
          case ProduceGarbage(amount) => // simulate garbage production
            context.log.info(
              s"Source{}: New garbage in town! {}, current amount: {}",
              instance.id, amount, state.garbage + amount
            )
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
