package mission.impossibl.bots

import akka.actor.typed.ActorRef
import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.Behaviors

object WasteSource {
  val Capacity = 20
  val DisposalPercentFull = 0.7

  sealed trait Command;

  final case class ProduceGarbage(amount: Int) extends Command

  final case class CheckGarbageLevel() extends Command

  def apply(location: (Int, Int), orchestrator: ActorRef[GarbageOrchestrator.Command]): Behavior[Command] = source(0, location, 0, orchestrator, 1)

  private def source(garbage: Int, location: (Int, Int), score: Int, orchestrator: ActorRef[GarbageOrchestrator.Command], sourceId: Int): Behavior[Command] =
    Behaviors.receive {
      (context, message) => {
        message match {
          case CheckGarbageLevel() =>
            context.log.info("Checking garbage level")
            if (garbage > DisposalPercentFull * Capacity) {
              orchestrator ! GarbageOrchestrator.GarbageCollectionRequest()
            }
            source(garbage, location, score, orchestrator, sourceId)
          case ProduceGarbage(amount) => // simulate garbage production
            context.log.info(s"New garbage in town! {}, current amount: {}", amount, garbage + amount)
            context.self ! CheckGarbageLevel()
            source(garbage + amount, location, score, orchestrator, sourceId)
        }
      }
    }
}
