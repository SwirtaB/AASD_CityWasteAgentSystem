package mission.impossibl.bots.collector

import akka.actor.typed.ActorRef
import akka.actor.typed.scaladsl.ActorContext

import java.util.UUID

class GarbageCollectorFactory[T](val context: ActorContext[T]) {
  def spawn(capacity: Int, initialLocation: (Int, Int), speed: Int): ActorRef[GarbageCollector.Command] = {
    val gcInstance          = Instance(UUID.randomUUID(), capacity, speed)
    val newGarbageCollector = context.spawn(GarbageCollector(gcInstance, initialLocation), s"Collector_${gcInstance.id}")

    newGarbageCollector
  }
}
