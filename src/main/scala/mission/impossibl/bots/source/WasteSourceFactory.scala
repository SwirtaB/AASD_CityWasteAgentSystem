package mission.impossibl.bots.source

import akka.actor.typed.ActorRef
import akka.actor.typed.scaladsl.ActorContext
import mission.impossibl.bots.orchestrator.GarbageOrchestrator
import mission.impossibl.bots.source.WasteSource.ProduceGarbage

import java.util.Random
import scala.concurrent.ExecutionContextExecutor
import scala.concurrent.duration.{FiniteDuration, SECONDS}

class WasteSourceFactory[T](val context: ActorContext[T]) {
  def spawn(id: Int, location: (Int, Int), capacity: Int, orchestrator: ActorRef[GarbageOrchestrator.Command]): ActorRef[WasteSource.Command] = {
    val wsInstance = Instance(id, location, capacity, orchestrator)
    val newSource = context.spawn(WasteSource(wsInstance), s"Source$id")

//    val random = new Random()
//    implicit val ec: ExecutionContextExecutor = context.system.executionContext
//    context.system.scheduler.scheduleAtFixedRate(FiniteDuration(1, SECONDS),
//      FiniteDuration(1, SECONDS))(() => newSource ! ProduceGarbage(Math.abs(random.nextInt() % 10)))

    newSource
  }
}
