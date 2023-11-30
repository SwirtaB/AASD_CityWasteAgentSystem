package mission.impossibl.bots

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorSystem, Behavior}
import mission.impossibl.bots.CityWasteAgentSystem.Jumpstart
import mission.impossibl.bots.WasteSource.ProduceGarbage

import java.util.Random
import scala.concurrent.duration.{FiniteDuration, SECONDS}

object CityWasteAgentSystem {
  def apply(): Behavior[Jumpstart] =
    Behaviors.setup { context =>
      val collector1 = context.spawn(GarbageCollector(GarbageCollector.Instance(1, 30, null), (5, 5)), "Collector1")
      val orchestrator1 = context.spawn(GarbageOrchestrator(GarbageOrchestrator.Instance(1, collector1 :: Nil)), "Orchestrator1")
      val source1 = context.spawn(WasteSource(WasteSource.Instance(1, (1, 1), 20, orchestrator1)), "Source1")

      orchestrator1 ! GarbageOrchestrator.LateInitialize()

      val random = new Random()
      implicit val ec = context.system.executionContext
      context.system.scheduler.scheduleAtFixedRate(FiniteDuration(1, SECONDS),
        FiniteDuration(5, SECONDS))(() => source1 ! ProduceGarbage(Math.abs(random.nextInt() % 10)))
      Behaviors.same
    }

  final case class Jumpstart()
}

object MissionImpossiBots extends App {
  val cityWasteAgentSystem: ActorSystem[CityWasteAgentSystem.Jumpstart] = ActorSystem(CityWasteAgentSystem(), "CityWasteAgentSystem")

  cityWasteAgentSystem ! Jumpstart()
}
