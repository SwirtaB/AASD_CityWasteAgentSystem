package mission.impossibl.bots.collector

import akka.actor.typed.ActorRef
import akka.actor.typed.scaladsl.ActorContext
import mission.impossibl.bots.collector.GarbageCollector.Move

import java.util.UUID
import scala.concurrent.ExecutionContextExecutor
import scala.concurrent.duration._

class GarbageCollectorFactory[T](val context: ActorContext[T]) {
  def spawn(capacity: Int, initialLocation: (Int, Int)): ActorRef[GarbageCollector.Command] = {
    val gcInstance = Instance(UUID.randomUUID(), capacity)
    val newGarbageCollector = context.spawn(GarbageCollector(gcInstance, initialLocation), s"Collector${gcInstance.id}")

    implicit val ec: ExecutionContextExecutor = context.system.executionContext
    context.system.scheduler.scheduleAtFixedRate(
      1.second,
      1.second)(() => newGarbageCollector ! Move())

    newGarbageCollector
  }
}
