package mission.impossibl.bots

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, ActorSystem, Behavior}
import mission.impossibl.bots.collector.{GarbageCollector, GarbageCollectorFactory}
import mission.impossibl.bots.orchestrator.{GarbageOrchestrator, GarbageOrchestratorFactory}
import mission.impossibl.bots.sink.WasteSink.{GarbagePacket, GarbagePacketRecord, ProcessGarbage, ReceiveGarbage}
import mission.impossibl.bots.sink.{WasteSink, WasteSinkFactory}
import mission.impossibl.bots.source.{WasteSource, WasteSourceFactory}
import org.apache.commons.math3.distribution.PoissonDistribution



object EnvironmentSimulator {
  def apply(): Behavior[Command] = {
    Behaviors.setup { context =>
      val garbageCollectorFactory = new GarbageCollectorFactory[Command](context)
      val garbageOrchestratorFactory = new GarbageOrchestratorFactory[Command](context)
      val wasteSourceFactory = new WasteSourceFactory[Command](context)
      val wasteSinkFactory = new WasteSinkFactory[Command](context)
      val sourceDist = new PoissonDistribution(2.0)
      val sinkDist = new PoissonDistribution(3.0)

      simulate(State(sourceDist, sinkDist, wasteSourceFactory, wasteSinkFactory, garbageCollectorFactory, garbageOrchestratorFactory))
    }
  }
  private def simulate(state: State): Behavior[Command] = {
    Behaviors.receive {
      (context, message) => {
        message match {
          case SpawnWasteSource(capacity, location) =>
            val sourceID = state.sourceCounter
            // TODO better assigment
            val orchestrator = state.garbageOrchestrators.head
            val wasteSource = state.wasteSourceFactory.spawn(sourceID, location, capacity, orchestrator)

            context.log.info(s"Waste Source {} at ({},{}) created. Capacity {}",
              sourceID, location._1, location._2, capacity)
            simulate(state.copy(wasteSources = state.wasteSources :+ wasteSource,
                                sourceCounter = state.sourceCounter + 1))
          case SpawnWasteSink(processing_power, storage_capacity, location) =>
            val sinkID = state.sinkCounter
            // TODO better assigment
            val orchestrator = state.garbageOrchestrators.head
            val wasteSink = state.wasteSinkFactory.spawn(sinkID, location, processing_power, storage_capacity, orchestrator)

            context.log.info(s"Waste Sink {} at ({},{}) created. Processing power {}, capacity {}",
              sinkID, location._1, location._2, processing_power, storage_capacity)
            simulate(state.copy(wasteSinks = state.wasteSinks :+ wasteSink,
                                sinkCounter = state.sinkCounter + 1))
          case SpawnGarbageCollector(capacity, location) =>
            val collectorID = state.collectorCounter
            val collector = state.garbageCollectorFactory.spawn(collectorID, capacity, location)
            // TODO better assigment
            collector ! GarbageCollector.AttachOrchestrator(0, state.garbageOrchestrators.head)
            context.log.info(s"Garbage collector {} at ({},{}) created. Capacity {}",
              collectorID, location._1, location._2, capacity)
            simulate(state.copy(garbageCollectors = state.garbageCollectors :+ collector,
                                collectorCounter = state.collectorCounter + 1))
          case SpawnGarbageOrchestrator() =>
            val orchestratorID = state.orchestratorCounter
            val orchestrator = state.garbageOrchestratorFactory.spawn(orchestratorID)
            context.log.info(s"Garbage orchestrator {} created.", orchestratorID)
            simulate(state.copy(garbageOrchestrators = state.garbageOrchestrators :+ orchestrator,
                                orchestratorCounter = state.orchestratorCounter + 1))
          case SimulationTick() =>
            state.wasteSources.foreach(source => {
              val amount = state.sourceDist.sample()
              source ! WasteSource.ProduceGarbage(amount)
            })
            state.wasteSinks.foreach(sink => {
              val n_packets = state.sourceDist.sample()
              // TODO see WasteSink
              // sink ! WasteSink.ProcessGarbage(n_packets)
              context.log.info(s"Processed {} packets of garbage", n_packets)
            })
            state.garbageCollectors.foreach(collector => {
              // TODO we can randomize move so that it simulates traffic
              collector ! GarbageCollector.Move(4)
            })
            Behaviors.same
        }
      }
    }
  }

  // TODO: Rewrite all actors to use UUID instead of Int. Simpler and more robust than counting.
  sealed trait Command
  final case class State(sourceDist: PoissonDistribution,
                         sinkDist: PoissonDistribution,
                         wasteSourceFactory: WasteSourceFactory[_],
                         wasteSinkFactory: WasteSinkFactory[_],
                         garbageCollectorFactory: GarbageCollectorFactory[_],
                         garbageOrchestratorFactory: GarbageOrchestratorFactory[_],
                         sourceCounter: Int = 0,
                         sinkCounter: Int = 0,
                         orchestratorCounter: Int = 0,
                         collectorCounter: Int = 0,
                         wasteSources: List[ActorRef[WasteSource.Command]] = List.empty,
                         wasteSinks: List[ActorRef[WasteSink.Command]] = List.empty,
                         garbageCollectors: List[ActorRef[GarbageCollector.Command]] = List.empty,
                         garbageOrchestrators: List[ActorRef[GarbageOrchestrator.Command]] = List.empty)
  final case class SimulationTick() extends Command
  final case class SpawnWasteSource(capacity: Int,
                                    location: (Int, Int)) extends Command
  final case class SpawnWasteSink(processing_power: Float,
                                  storage_capacity: Float,
                                  location: (Int, Int)) extends Command
  final case class SpawnGarbageCollector(capacity: Int,
                                         location: (Int, Int)) extends Command
  final case class SpawnGarbageOrchestrator() extends Command
}

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
//  val cityWasteAgentSystem: ActorSystem[CityWasteAgentSystem.Jumpstart] = ActorSystem(CityWasteAgentSystem(), "CityWasteAgentSystem")
  val environment: ActorSystem[EnvironmentSimulator.Command] = ActorSystem(EnvironmentSimulator(), "EnvSim")
  environment ! EnvironmentSimulator.SpawnGarbageOrchestrator()
  environment ! EnvironmentSimulator.SpawnWasteSource(20, (1, 1))
  environment ! EnvironmentSimulator.SpawnWasteSink(100.0f, 100.0f, (20, 20))
  environment ! EnvironmentSimulator.SpawnGarbageCollector(30, (5,5))
  environment ! EnvironmentSimulator.SpawnGarbageCollector(30, (5,5))

  environment ! EnvironmentSimulator.SimulationTick()
}
