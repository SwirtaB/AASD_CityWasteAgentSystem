package mission.impossibl.bots.sink

import akka.actor.typed.ActorRef
import akka.actor.typed.scaladsl.ActorContext
import mission.impossibl.bots.orchestrator.GarbageOrchestrator

import java.util.UUID

class WasteSinkFactory[T](val context: ActorContext[T]) {
  def spawn(location: (Int, Int), processingPower: Int, storageCapacity: Int, orchestrator: ActorRef[GarbageOrchestrator.Command]): ActorRef[WasteSink.Command] = {
    val id                = UUID.randomUUID()
    val wasteSinkInstance = Instance(id, location, storageCapacity, orchestrator)
    context.spawn(WasteSink(wasteSinkInstance, processingPower), s"Sink_$id")
  }
}
