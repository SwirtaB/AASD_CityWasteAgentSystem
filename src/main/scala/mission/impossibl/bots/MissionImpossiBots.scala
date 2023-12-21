package mission.impossibl.bots

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, ActorSystem, Behavior}
import mission.impossibl.bots.collector.{GarbageCollector, GarbageCollectorFactory}
import mission.impossibl.bots.orchestrator.{GarbageOrchestrator, GarbageOrchestratorFactory}
import mission.impossibl.bots.sink.WasteSink.{GarbagePacket, GarbagePacketRecord, ProcessGarbage, ReceiveGarbage}
import mission.impossibl.bots.sink.{WasteSink, WasteSinkFactory}
import mission.impossibl.bots.source.{WasteSource, WasteSourceFactory}
import org.apache.commons.math3.distribution.PoissonDistribution

import scala.concurrent.ExecutionContextExecutor
import scala.concurrent.duration.{FiniteDuration, SECONDS}



object EnvironmentSimulator {
  def apply(sourceDistParam: Double = 2.0, moveDistParams: (Int, Int) = (4, 1)): Behavior[Command] = {
    Behaviors.setup { context =>
      val garbageCollectorFactory = new GarbageCollectorFactory[Command](context)
      val garbageOrchestratorFactory = new GarbageOrchestratorFactory[Command](context)
      val wasteSourceFactory = new WasteSourceFactory[Command](context)
      val wasteSinkFactory = new WasteSinkFactory[Command](context)
      val sourceDist = new PoissonDistribution(2.0)

      simulate(State(sourceDist, sourceDistParam, moveDistParams, wasteSourceFactory,
        wasteSinkFactory, garbageCollectorFactory, garbageOrchestratorFactory))
    }
  }
  private def simulate(state: State): Behavior[Command] = {
    Behaviors.receive {
      (context, message) => {
        message match {
          case SpawnWasteSource(capacity, location) =>
            val sourceID = state.sourceCounter
            // TODO id: Int -> UUID
            val orchestrator = state.garbageOrchestrators.head
            val wasteSource = state.wasteSourceFactory.spawn(sourceID, location, capacity, orchestrator)

            context.log.info(s"Waste Source {} at {} created. Capacity {}",
              sourceID, location, capacity)
            simulate(state.copy(wasteSources = state.wasteSources :+ wasteSource,
                                sourceCounter = state.sourceCounter + 1))
          case SpawnWasteSink(efficiency, storageCapacity, location) =>
            val sinkID = state.sinkCounter
            // TODO id: Int -> UUID
            val orchestrator = state.garbageOrchestrators.head
            val wasteSink = state.wasteSinkFactory.spawn(sinkID, location, efficiency, storageCapacity, orchestrator)

            context.log.info(s"Waste Sink {} at ({},{}) created. Processing power {}, capacity {}",
              sinkID, location._1, location._2, efficiency, storageCapacity)
            simulate(state.copy(wasteSinks = state.wasteSinks :+ wasteSink,
                                sinkCounter = state.sinkCounter + 1))
          case SpawnGarbageCollector(capacity, location) =>
            val collectorID = state.collectorCounter
            val collector = state.garbageCollectorFactory.spawn(collectorID, capacity, location)
            // TODO id: Int -> UUID
            collector ! GarbageCollector.AttachOrchestrator(0, state.garbageOrchestrators.head)
            context.log.info(s"Garbage collector {} at ({},{}) created. Capacity {}",
              collectorID, location._1, location._2, capacity)
            simulate(state.copy(garbageCollectors = state.garbageCollectors :+ collector,
                                collectorCounter = state.collectorCounter + 1))
          case SpawnGarbageOrchestrator() =>
            // TODO id: Int -> UUID
            val orchestratorID = state.orchestratorCounter
            val orchestrator = state.garbageOrchestratorFactory.spawn(orchestratorID)
            context.log.info(s"Garbage orchestrator {} created.", orchestratorID)
            simulate(state.copy(garbageOrchestrators = state.garbageOrchestrators :+ orchestrator,
                                orchestratorCounter = state.orchestratorCounter + 1))
          case SimulationTick() =>
            // Produce garbage in each source
            state.wasteSources.foreach(source => {
              val amount = state.sourceDist.sample()
              source ! WasteSource.ProduceGarbage(amount)
            })
            // Process garbage in each sink
            state.wasteSinks.foreach(sink => {
              sink ! WasteSink.ProcessGarbage()
            })
            // Move each collector
            state.garbageCollectors.foreach(collector => {
              collector ! GarbageCollector.Move(Utils.sample_normal(4, 1))
            })
            Behaviors.same
        }
      }
    }
  }

  // TODO: Rewrite all actors to use UUID instead of Int. Simpler and more robust than counting.
  sealed trait Command
  final case class State(sourceDist: PoissonDistribution,
                         sourceDistParam: Double,
                         moveDistParams: (Int, Int),
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
  final case class SpawnWasteSink(efficiency: Int, storageCapacity: Int, location: (Int, Int)) extends Command
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

      val sink_1 = wasteSinkFactory.spawn(1, (1, 1), 10, 100, orchestrator1)
      val garbage_packet = GarbagePacket(List(GarbagePacketRecord(1, 1, 10), GarbagePacketRecord(2, 1, 30)), 40)
      sink_1 ! ReceiveGarbage(garbage_packet)
      sink_1 ! ProcessGarbage()

      collector1 ! GarbageCollector.AttachOrchestrator(1, orchestrator1) // TODO: GC should automatically find the closest GO
      collector2 ! GarbageCollector.AttachOrchestrator(1, orchestrator1)

      Behaviors.same
    }

  final case class Jumpstart()
}

object MissionImpossiBots extends App {
  val environment: ActorSystem[EnvironmentSimulator.Command] = ActorSystem(EnvironmentSimulator(), "EnvSim")
  environment ! EnvironmentSimulator.SpawnGarbageOrchestrator()
  environment ! EnvironmentSimulator.SpawnWasteSource(20, (1, 1))
  environment ! EnvironmentSimulator.SpawnWasteSink(100, 100, (20, 20))
  environment ! EnvironmentSimulator.SpawnGarbageCollector(30, (5,5))
  environment ! EnvironmentSimulator.SpawnGarbageCollector(30, (5,5))

  implicit val ec: ExecutionContextExecutor = environment.executionContext
  environment.scheduler.scheduleAtFixedRate(FiniteDuration(5, SECONDS), FiniteDuration(5, SECONDS))(() => environment ! EnvironmentSimulator.SimulationTick())
}
