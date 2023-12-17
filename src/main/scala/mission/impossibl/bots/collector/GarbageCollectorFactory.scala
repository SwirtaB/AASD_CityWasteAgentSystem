package mission.impossibl.bots.collector

import akka.actor.typed.ActorRef
import akka.actor.typed.scaladsl.ActorContext
import mission.impossibl.bots.collector.GarbageCollector.Move

import scala.concurrent.ExecutionContextExecutor
import scala.concurrent.duration.{FiniteDuration, SECONDS}

class GarbageCollectorFactory[T](val context: ActorContext[T]) {
  def spawn(id: Int, capacity: Int, initialLocation: (Int, Int)): ActorRef[GarbageCollector.Command] = {
    val gcInstance = Instance(id, capacity, null)
    val newGarbageCollector = context.spawn(GarbageCollector(gcInstance, initialLocation), s"Collector$id")

    implicit val ec: ExecutionContextExecutor = context.system.executionContext
    context.system.scheduler.scheduleAtFixedRate(FiniteDuration(1, SECONDS),
      FiniteDuration(1, SECONDS))(() => newGarbageCollector ! Move())

    newGarbageCollector
  }
}