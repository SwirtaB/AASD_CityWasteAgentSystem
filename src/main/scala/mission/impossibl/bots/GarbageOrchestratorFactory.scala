package mission.impossibl.bots

import akka.actor.typed.ActorRef
import akka.actor.typed.scaladsl.ActorContext

class GarbageOrchestratorFactory[T](val context: ActorContext[T]) {
  def spawn(id: Int, garbageCollectors: List[ActorRef[GarbageCollector.Command]]): ActorRef[GarbageOrchestrator.Command] = {
    val goInstance = GarbageOrchestrator.Instance(id, garbageCollectors)
    context.spawn(GarbageOrchestrator(goInstance), s"Orchestrator$id")
  }
}
