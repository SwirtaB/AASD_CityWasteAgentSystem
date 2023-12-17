package mission.impossibl.bots

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorSystem, Behavior}
import mission.impossibl.bots.CityWasteAgentSystem.Jumpstart
import mission.impossibl.bots.collector.{GarbageCollector, GarbageCollectorFactory}
import mission.impossibl.bots.orchestrator.GarbageOrchestratorFactory
import mission.impossibl.bots.sink.WasteSink.{GarbagePacket, GarbagePacketRecord, ProcessGarbage, ReceiveGarbage}
import mission.impossibl.bots.sink.WasteSinkFactory
import mission.impossibl.bots.source.WasteSourceFactory


object CityWasteAgentSystem {
  def apply(): Behavior[Jumpstart] =
    Behaviors.setup { context =>
      val garbageCollectorFactory = new GarbageCollectorFactory[Jumpstart](context)
      val garbageOrchestratorFactory = new GarbageOrchestratorFactory[Jumpstart](context)
      val wasteSourceFactory = new WasteSourceFactory[Jumpstart](context)

      val collector1 = garbageCollectorFactory.spawn(1, 30, (5, 5))
      val collector2 = garbageCollectorFactory.spawn(2, 30, (5, 5))
      val orchestrator1 = garbageOrchestratorFactory.spawn(1)
      val source1 = wasteSourceFactory.spawn(1, (1, 1), 20, orchestrator1)

      // Sink test
      val wasteSinkFactory = new WasteSinkFactory[Jumpstart](context)

      val sink_1 = wasteSinkFactory.spawn(1, (1, 1), 10.0f, 100.0f, orchestrator1)
      val garbage_packet = GarbagePacket(List(GarbagePacketRecord(1, 1, 10.0f), GarbagePacketRecord(2, 1, 30.0f)), 40.0f)
      sink_1 ! ReceiveGarbage(garbage_packet)
      sink_1 ! ProcessGarbage(1)

      collector1 ! GarbageCollector.AttachOrchestrator(1, orchestrator1) // TODO: GC should automatically find the closest GO
      collector2 ! GarbageCollector.AttachOrchestrator(1, orchestrator1)

      Behaviors.same
    }

  final case class Jumpstart()
}

object MissionImpossiBots extends App {
  val cityWasteAgentSystem: ActorSystem[CityWasteAgentSystem.Jumpstart] = ActorSystem(CityWasteAgentSystem(), "CityWasteAgentSystem")

  cityWasteAgentSystem ! Jumpstart()
}
