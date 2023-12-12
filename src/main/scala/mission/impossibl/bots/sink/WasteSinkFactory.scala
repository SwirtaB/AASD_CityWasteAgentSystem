package mission.impossibl.bots.sink

import akka.actor.typed.ActorRef
import akka.actor.typed.scaladsl.ActorContext
import mission.impossibl.bots.orchestrator.GarbageOrchestrator
class WasteSinkFactory[T](val context: ActorContext[T]) {
  def spawn(id: Int,
            location: (Int, Int),
            processing_power: Float,
            storage_capacity: Float,
            orchestrator: ActorRef[GarbageOrchestrator.Command]): ActorRef[WasteSink.Command] = {
    val wasteSinkInstance = WasteSink.Instance(id, location, storage_capacity, orchestrator)
    context.spawn(WasteSink(wasteSinkInstance, processing_power), s"Sink$id")
  }
}
