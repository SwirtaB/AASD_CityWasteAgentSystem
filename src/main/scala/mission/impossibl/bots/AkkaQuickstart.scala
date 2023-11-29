package mission.impossibl.bots

import akka.actor.typed.ActorSystem
import mission.impossibl.bots.WasteSource.ProduceGarbage

import java.util.Random
import scala.concurrent.ExecutionContextExecutor
import scala.concurrent.duration.{FiniteDuration, SECONDS}

object AkkaQuickstart extends App {
  private val random = new Random()
  private val sampleSource: ActorSystem[WasteSource.SourceCommands] = ActorSystem(WasteSource((1, 1), null), "AkkaQuickStart")

  implicit val ec: ExecutionContextExecutor = sampleSource.executionContext
  sampleSource.scheduler.scheduleAtFixedRate(FiniteDuration(1, SECONDS),
    FiniteDuration(5, SECONDS))(() => sampleSource ! ProduceGarbage(Math.abs(random.nextInt() % 10)))
}
