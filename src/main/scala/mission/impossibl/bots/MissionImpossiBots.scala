package mission.impossibl.bots

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, ActorSystem, Behavior}
import mission.impossibl.bots.collector.{GarbageCollector, GarbageCollectorFactory}
import mission.impossibl.bots.orchestrator.{GarbageOrchestrator, GarbageOrchestratorFactory}
import mission.impossibl.bots.sink.{WasteSink, WasteSinkFactory}
import mission.impossibl.bots.source.{WasteSource, WasteSourceFactory}
import org.apache.commons.math3.distribution.PoissonDistribution

object EnvironmentSimulator {
  def apply(): Behavior[Command] =
    Behaviors.setup { context =>
      val garbageCollectorFactory    = new GarbageCollectorFactory[Command](context)
      val garbageOrchestratorFactory = new GarbageOrchestratorFactory[Command](context)
      val wasteSourceFactory         = new WasteSourceFactory[Command](context)
      val wasteSinkFactory           = new WasteSinkFactory[Command](context)
      val sourceDist                 = new PoissonDistribution(2.0)
      val sinkDist                   = new PoissonDistribution(3.0)

      simulate(State(sourceDist, sinkDist, wasteSourceFactory, wasteSinkFactory, garbageCollectorFactory, garbageOrchestratorFactory))
    }
  private def simulate(state: State): Behavior[Command] =
    Behaviors.receive { (context, message) =>
      message match {
        case SpawnWasteSource(capacity, location) =>
          // TODO better assigment
          val orchestrator = state.garbageOrchestrators.head
          val wasteSource  = state.wasteSourceFactory.spawn(location, capacity, orchestrator)

          context.log.info(s"Waste Source at ({},{}) created. Capacity {}", location._1, location._2, capacity)
          simulate(state.copy(wasteSources = state.wasteSources :+ wasteSource))
        case SpawnWasteSink(processing_power, storage_capacity, location) =>
          // TODO better assigment
          val orchestrator = state.garbageOrchestrators.head
          val wasteSink    = state.wasteSinkFactory.spawn(location, processing_power, storage_capacity, orchestrator)

          context.log.info(s"Waste Sink at ({},{}) created. Processing power {}, capacity {}", location._1, location._2, processing_power, storage_capacity)
          simulate(state.copy(wasteSinks = state.wasteSinks :+ wasteSink))
        case SpawnGarbageCollector(capacity, location) =>
          val collector = state.garbageCollectorFactory.spawn(capacity, location)
          // TODO better assigment
          collector ! GarbageCollector.AttachOrchestrator(0, state.garbageOrchestrators.head)
          context.log.info(s"Garbage collector at ({},{}) created. Capacity {}", location._1, location._2, capacity)
          simulate(state.copy(garbageCollectors = state.garbageCollectors :+ collector))
        case SpawnGarbageOrchestrator() =>
          val orchestrator = state.garbageOrchestratorFactory.spawn()
          context.log.info(s"Garbage orchestrator created.")
          simulate(state.copy(garbageOrchestrators = state.garbageOrchestrators :+ orchestrator))
        case SimulationTick() =>
          state.wasteSources.foreach { source =>
            val amount = state.sourceDist.sample()
            source ! WasteSource.ProduceGarbage(amount)
          }
          state.wasteSinks.foreach { sink =>
            val packetCount = state.sourceDist.sample()
            // TODO see WasteSink
            sink ! WasteSink.ProcessGarbage(packetCount)
            context.log.info(s"Processed {} packets of garbage", packetCount)
          }
          state.garbageCollectors.foreach { collector =>
            // TODO we can randomize move so that it simulates traffic
            collector ! GarbageCollector.Move(4)
          }
          Behaviors.same
      }
    }

  sealed trait Command
  final case class State(
    sourceDist: PoissonDistribution,
    sinkDist: PoissonDistribution,
    wasteSourceFactory: WasteSourceFactory[_],
    wasteSinkFactory: WasteSinkFactory[_],
    garbageCollectorFactory: GarbageCollectorFactory[_],
    garbageOrchestratorFactory: GarbageOrchestratorFactory[_],
    wasteSources: List[ActorRef[WasteSource.Command]] = List.empty,
    wasteSinks: List[ActorRef[WasteSink.Command]] = List.empty,
    garbageCollectors: List[ActorRef[GarbageCollector.Command]] = List.empty,
    garbageOrchestrators: List[ActorRef[GarbageOrchestrator.Command]] = List.empty
  )
  final case class SimulationTick()                                                                       extends Command
  final case class SpawnWasteSource(capacity: Int, location: (Int, Int))                                  extends Command
  final case class SpawnWasteSink(processing_power: Float, storage_capacity: Float, location: (Int, Int)) extends Command
  final case class SpawnGarbageCollector(capacity: Int, location: (Int, Int))                             extends Command
  final case class SpawnGarbageOrchestrator()                                                             extends Command
}

object MissionImpossiBots extends App {
  val environment: ActorSystem[EnvironmentSimulator.Command] = ActorSystem(EnvironmentSimulator(), "EnvSim")
  environment ! EnvironmentSimulator.SpawnGarbageOrchestrator()
  environment ! EnvironmentSimulator.SpawnWasteSource(20, (1, 1))
  environment ! EnvironmentSimulator.SpawnWasteSink(100.0f, 100.0f, (20, 20))
  environment ! EnvironmentSimulator.SpawnGarbageCollector(30, (5, 5))
  environment ! EnvironmentSimulator.SpawnGarbageCollector(30, (5, 5))

  environment ! EnvironmentSimulator.SimulationTick()
}
