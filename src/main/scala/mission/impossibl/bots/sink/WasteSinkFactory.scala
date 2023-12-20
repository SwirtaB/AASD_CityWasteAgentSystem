package mission.impossibl.bots.sink

import akka.actor.typed.ActorRef
import akka.actor.typed.scaladsl.ActorContext

import java.util.UUID

class WasteSinkFactory[T](val context: ActorContext[T]) {
  def spawn(location: (Int, Int),
            processingPower: Float,
            storageCapacity: Float): ActorRef[WasteSink.Command] = {
    val id = UUID.randomUUID()
    val wasteSinkInstance = Instance(id, location, storageCapacity, null)
    context.spawn(WasteSink(wasteSinkInstance, processingPower), s"Sink_$id")
  }
}
