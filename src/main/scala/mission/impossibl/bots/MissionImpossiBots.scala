package mission.impossibl.bots

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorSystem, Behavior}
import mission.impossibl.bots.CityWasteAgentSystem.Jumpstart
import mission.impossibl.bots.WasteSource.ProduceGarbage

import java.util.Random
import scala.concurrent.ExecutionContextExecutor
import scala.concurrent.duration.{FiniteDuration, SECONDS}

object CityWasteAgentSystem {
  final case class Jumpstart()

  def apply(): Behavior[Jumpstart] =
    Behaviors.setup { context =>
      val orchestrator = context.spawn(GarbageOrchestrator(), "orchestrator1")
      val random = new Random()
      val wasteSource: ActorSystem[WasteSource.Command] = ActorSystem(
        WasteSource(WasteSource.Instance(1, (1, 1), 20, orchestrator)),
        "wasteSourceActorSystem"
      )

      implicit val ec: ExecutionContextExecutor = wasteSource.executionContext
      wasteSource.scheduler.scheduleAtFixedRate(FiniteDuration(1, SECONDS),
        FiniteDuration(5, SECONDS))(() => wasteSource ! ProduceGarbage(Math.abs(random.nextInt() % 10)))
      Behaviors.same
    }
}

object MissionImpossiBots extends App {
  val cityWasteAgentSystem: ActorSystem[CityWasteAgentSystem.Jumpstart] = ActorSystem(CityWasteAgentSystem(), "CityWasteAgentSystem")

  cityWasteAgentSystem ! Jumpstart()
}
