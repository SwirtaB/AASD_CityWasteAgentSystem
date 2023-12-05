package mission.impossibl.bots.orchestrator

import akka.actor.typed.ActorRef
import akka.actor.typed.scaladsl.ActorContext

class GarbageOrchestratorFactory[T](val context: ActorContext[T]) {
  def spawn(id: Int): ActorRef[GarbageOrchestrator.Command] = {
    context.spawn(GarbageOrchestrator(Instance(id)), s"Orchestrator$id")
  }
}
