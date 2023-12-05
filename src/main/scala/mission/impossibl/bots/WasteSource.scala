package mission.impossibl.bots

import akka.actor.Cancellable
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, Behavior}
import mission.impossibl.bots.orchestrator.GarbageOrchestrator
import scala.concurrent.duration._

object WasteSource {
  private val DisposalPercentFull = 0.7
  private val DisposalAuctionTimeout = 3.seconds

  def apply(instance: Instance): Behavior[Command] = {
    source(instance, State())
  }

  private def source(instance: Instance, state: State): Behavior[Command] =
    Behaviors.receive {
      (context, message) => {
        message match {
          case CheckGarbageLevel() =>
            context.log.info("Checking garbage level")
            if (state.timeoutRef.isEmpty && state.garbage > DisposalPercentFull * instance.capacity) {

              instance.orchestrator ! GarbageOrchestrator.GarbageCollectionRequest(
                instance.id, instance.location, context.self, state.garbage
              )
              val auctionTimeout = context.scheduleOnce(DisposalAuctionTimeout, context.self, AuctionTimeout())
              source(instance, state.copy(timeoutRef = Some(auctionTimeout)))
            }else{
              Behaviors.same
            }
          case ProduceGarbage(amount) => // simulate garbage production
            context.log.info(
              s"New garbage in town! {}, current amount: {}",
              amount, state.garbage + amount
            )
            context.self ! CheckGarbageLevel()
            source(instance, state.copy(state.garbage + amount))
          case AuctionTimeout() =>
            context.log.info("Timed out but no auction result")
            context.self ! CheckGarbageLevel()
            source(instance, state.copy(timeoutRef = None))
        }
      }
    }

  sealed trait Command

  final case class Instance(id: Int, location: (Int, Int), capacity: Int, orchestrator: ActorRef[GarbageOrchestrator.Command])

  final case class State(garbage: Int = 0, score: Int = 0, timeoutRef: Option[Cancellable] = None)

  final case class ProduceGarbage(amount: Int) extends Command

  private final case class CheckGarbageLevel() extends Command

  private final case class AuctionTimeout() extends Command
}
