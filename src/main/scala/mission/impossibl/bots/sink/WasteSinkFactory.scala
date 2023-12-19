package mission.impossibl.bots.sink

import akka.actor.typed.ActorRef
import akka.actor.typed.scaladsl.ActorContext

class WasteSinkFactory[T](val context: ActorContext[T]) {
  def spawn(id: Int,
            location: (Int, Int),
            processing_power: Float,
            storage_capacity: Float): ActorRef[WasteSink.Command] = {
    val wasteSinkInstance = Instance(id, location, storage_capacity, null)
    context.spawn(WasteSink(wasteSinkInstance, processing_power), s"Sink$id")
  }
}
