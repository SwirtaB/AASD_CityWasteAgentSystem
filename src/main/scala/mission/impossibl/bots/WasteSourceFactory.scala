package mission.impossibl.bots

import akka.actor.typed.ActorRef
import akka.actor.typed.scaladsl.ActorContext
import mission.impossibl.bots.orchestrator.GarbageOrchestrator

class WasteSourceFactory[T](val context: ActorContext[T]) {
  def spawn(id: Int, location: (Int, Int), capacity: Int, orchestrator: ActorRef[GarbageOrchestrator.Command]): ActorRef[WasteSource.Command] = {
    val wsInstance = WasteSource.Instance(id, location, capacity, orchestrator)
    context.spawn(WasteSource(wsInstance), s"Source$id")
  }
}
