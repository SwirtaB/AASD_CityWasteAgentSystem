package mission.impossibl.bots.collector

import akka.actor.typed.ActorRef
import akka.actor.typed.scaladsl.ActorContext

class GarbageCollectorFactory[T](val context: ActorContext[T]) {
  def spawn(id: Int, capacity: Int, initialLocation: (Int, Int)): ActorRef[GarbageCollector.Command] = {
    val gcInstance = GarbageCollector.Instance(id, capacity, null)
    context.spawn(GarbageCollector(gcInstance, initialLocation), s"Collector$id")
  }
}
