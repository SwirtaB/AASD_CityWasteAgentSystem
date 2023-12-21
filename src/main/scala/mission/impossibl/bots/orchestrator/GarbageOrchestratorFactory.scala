package mission.impossibl.bots.orchestrator

import akka.actor.typed.ActorRef
import akka.actor.typed.scaladsl.ActorContext

import java.util.UUID

class GarbageOrchestratorFactory[T](val context: ActorContext[T]) {
  def spawn(): ActorRef[GarbageOrchestrator.Command] = {
    val id = UUID.randomUUID()
    context.spawn(GarbageOrchestrator(Instance(id)), s"Orchestrator_$id")
  }
}
