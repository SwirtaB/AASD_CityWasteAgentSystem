package mission.impossibl.bots

import akka.actor.ActorRef
import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.Behaviors

object WasteSource {
  sealed trait SourceCommands;

  final case class GarbageCollectionScore(score: Int) extends SourceCommands

  final case class GetGarbage(maxAmount: Int, garbageCollector: ActorRef) extends SourceCommands

  final case class ProduceGarbage(amount: Int) extends SourceCommands

  sealed trait SourceResponses

  final case class SendGarbage(actualAmount: Int, sourceId: Int) extends SourceResponses


  def apply(location: (Int, Int), orchestrator: ActorRef): Behavior[SourceCommands] = source(0, location, 0, orchestrator, 1)


  def source(garbage: Int, location: (Int, Int), score: Int, orchestrator: ActorRef, sourceId: Int): Behavior[SourceCommands] =
    Behaviors.receive {
      (context, message) => {
        context.log.info("Message received {}", message)
        message match {
          case GarbageCollectionScore(newScore) =>
            source(garbage, location, score + newScore / 2, orchestrator, sourceId)
          case GetGarbage(maxAmount, garbageCollector) =>
            context.log.info("Garbage received {}", maxAmount)
            val garbageLeft = if (maxAmount >= garbage) 0 else garbage - maxAmount
            garbageCollector ! SendGarbage(garbage - garbageLeft, sourceId)
            source(garbageLeft, location, score, orchestrator, sourceId)

          //simulate garbage production
          case ProduceGarbage(amount) =>
            context.log.info(s"Garbage collected {}", amount)
            source(garbage + amount, location, score, orchestrator, sourceId)
        }
      }
    }
}
