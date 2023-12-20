package mission.impossibl.bots

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorSystem, Behavior}
import mission.impossibl.bots.CityWasteAgentSystem.Jumpstart
import mission.impossibl.bots.collector.{GarbageCollector, GarbageCollectorFactory}
import mission.impossibl.bots.orchestrator.GarbageOrchestratorFactory
import mission.impossibl.bots.sink.WasteSink.{ProcessGarbage, ReceiveGarbage}
import mission.impossibl.bots.sink.{GarbagePacket, GarbagePacketRecord, WasteSink, WasteSinkFactory}
import mission.impossibl.bots.source.WasteSourceFactory

import java.util.UUID

object CityWasteAgentSystem {
  def apply(): Behavior[Jumpstart] =
    Behaviors.setup { context =>
      val garbageCollectorFactory    = new GarbageCollectorFactory[Jumpstart](context)
      val garbageOrchestratorFactory = new GarbageOrchestratorFactory[Jumpstart](context)
      val wasteSourceFactory         = new WasteSourceFactory[Jumpstart](context)

      val collector1    = garbageCollectorFactory.spawn(300, (5, 5))
      val collector2    = garbageCollectorFactory.spawn(100, (1, 1))
      val collector3    = garbageCollectorFactory.spawn(150, (50, 50))
      val orchestrator1 = garbageOrchestratorFactory.spawn()
      val sources       = wasteSourceFactory.spawnRandom((100, 100), List(100, 200, 50, 50, 50, 100, 200, 20), orchestrator1)

      // Sink test
      val wasteSinkFactory = new WasteSinkFactory[Jumpstart](context)

      val sink1 = wasteSinkFactory.spawn((1, 1), 10.0f, 100.0f)

      // TODO: garbage collectors and waste sinks should automatically find the closest orchestrators
      collector1 ! GarbageCollector.AttachOrchestrator(1, orchestrator1)
      collector2 ! GarbageCollector.AttachOrchestrator(1, orchestrator1)
      collector3 ! GarbageCollector.AttachOrchestrator(1, orchestrator1)

      sink1 ! WasteSink.AttachOrchestrator(1, orchestrator1)

      // NOTE: for testing only
      val garbage_packet = GarbagePacket(List(GarbagePacketRecord(1, 1, 10.0f), GarbagePacketRecord(2, 1, 30.0f)), 40.0f)
      sink1 ! ReceiveGarbage(garbage_packet, UUID.randomUUID())
      sink1 ! ProcessGarbage(1)

      Behaviors.same
    }

  final case class Jumpstart()
}

object MissionImpossiBots extends App {
  val cityWasteAgentSystem: ActorSystem[CityWasteAgentSystem.Jumpstart] = ActorSystem(CityWasteAgentSystem(), "CityWasteAgentSystem")

  cityWasteAgentSystem ! Jumpstart()
}
