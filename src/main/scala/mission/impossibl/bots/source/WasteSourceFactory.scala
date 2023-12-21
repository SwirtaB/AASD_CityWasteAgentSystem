package mission.impossibl.bots.source

import akka.actor.typed.ActorRef
import akka.actor.typed.scaladsl.ActorContext
import mission.impossibl.bots.orchestrator.GarbageOrchestrator
import mission.impossibl.bots.source.WasteSource.ProduceGarbage

import java.util.{Random, UUID}
import scala.concurrent.ExecutionContextExecutor
import scala.concurrent.duration.{FiniteDuration, SECONDS}

class WasteSourceFactory[T](val context: ActorContext[T]) {
  private val rand = new Random()
  def spawnRandom(gridMax: (Int, Int), sizes: List[Int], orchestrator: ActorRef[GarbageOrchestrator.Command]): List[ActorRef[WasteSource.Command]] =
    sizes.map { size =>
      val x = rand.nextInt(gridMax._1)
      val y = rand.nextInt(gridMax._2)
      spawn((x, y), size, orchestrator)
    }
  def spawn(location: (Int, Int), capacity: Int, orchestrator: ActorRef[GarbageOrchestrator.Command]): ActorRef[WasteSource.Command] = {
    val id         = UUID.randomUUID()
    val wsInstance = Instance(id, location, capacity, orchestrator)
    val newSource  = context.spawn(WasteSource(wsInstance), s"Source_$id")


    newSource
  }
}
