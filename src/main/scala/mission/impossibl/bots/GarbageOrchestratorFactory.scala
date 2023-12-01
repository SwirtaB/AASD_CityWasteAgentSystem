package mission.impossibl.bots

import akka.actor.typed.ActorRef
import akka.actor.typed.scaladsl.ActorContext

class GarbageOrchestratorFactory[T](val context: ActorContext[T]) {
  def spawn(id: Int): ActorRef[GarbageOrchestrator.Command] = {
    val goInstance = GarbageOrchestrator.Instance(id)
    context.spawn(GarbageOrchestrator(goInstance), s"Orchestrator$id")
  }
}
