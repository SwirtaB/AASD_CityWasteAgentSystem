package mission.impossibl.bots.sink

import akka.actor.typed.ActorRef
import akka.actor.typed.scaladsl.ActorContext

class WasteSinkFactory[T](val context: ActorContext[T]) {
  def spawn(id: Int,
            location: (Int, Int),
            efficiency: Int,
            storageCapacity: Int,
            orchestrator: ActorRef[GarbageOrchestrator.Command]): ActorRef[WasteSink.Command] = {
    val wasteSinkInstance = WasteSink.Instance(id, location, storageCapacity, orchestrator)
    context.spawn(WasteSink(wasteSinkInstance, efficiency), s"Sink$id")

  }
}
