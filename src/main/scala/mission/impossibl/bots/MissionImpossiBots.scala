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
      val gcFactory = new GarbageCollectorFactory[Jumpstart](context)
      val goFactory = new GarbageOrchestratorFactory[Jumpstart](context)
      val wsFactory = new WasteSourceFactory[Jumpstart](context)

      val collector1 = gcFactory.spawn(1, 30, (5, 5))
      val orchestrator1 = goFactory.spawn(1)
      val source1 = wsFactory.spawn(1, (1, 1), 20, orchestrator1)

      collector1 ! GarbageCollector.AttachOrchestrator(1, orchestrator1) // TODO: GC should automatically find the closest GO

      val random = new Random()
      implicit val ec = context.system.executionContext
      context.system.scheduler.scheduleAtFixedRate(FiniteDuration(1, SECONDS),
        FiniteDuration(1, SECONDS))(() => source1 ! ProduceGarbage(Math.abs(random.nextInt() % 10)))
      Behaviors.same
    }

  final case class Jumpstart()
}

object MissionImpossiBots extends App {
  val cityWasteAgentSystem: ActorSystem[CityWasteAgentSystem.Jumpstart] = ActorSystem(CityWasteAgentSystem(), "CityWasteAgentSystem")

  cityWasteAgentSystem ! Jumpstart()
}
