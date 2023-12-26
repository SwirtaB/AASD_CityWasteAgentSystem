package mission.impossibl.bots

import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.scaladsl.Behaviors
import mission.impossibl.bots.collector.{GarbageCollector, GarbageCollectorFactory}
import mission.impossibl.bots.http.AllActors
import mission.impossibl.bots.orchestrator.{GarbageOrchestrator, GarbageOrchestratorFactory}
import mission.impossibl.bots.sink.WasteSink.AttachOrchestrator
import mission.impossibl.bots.sink.{WasteSink, WasteSinkFactory}
import mission.impossibl.bots.source.{WasteSource, WasteSourceFactory}
import org.apache.commons.math3.distribution.PoissonDistribution

object EnvironmentSimulator {
  def apply(sourceDistParam: Double = 2.0, moveDistParams: (Int, Int) = (4, 1)): Behavior[Command] =
    Behaviors.setup { context =>
      val garbageCollectorFactory    = new GarbageCollectorFactory[Command](context)
      val garbageOrchestratorFactory = new GarbageOrchestratorFactory[Command](context)
      val wasteSourceFactory         = new WasteSourceFactory[Command](context)
      val wasteSinkFactory           = new WasteSinkFactory[Command](context)
      val sourceDist                 = new PoissonDistribution(2.0)

      simulate(State(sourceDist, sourceDistParam, moveDistParams, wasteSourceFactory, wasteSinkFactory, garbageCollectorFactory, garbageOrchestratorFactory))
    }
  private def simulate(state: State): Behavior[Command] =
    Behaviors.receive { (context, message) =>
      message match {
        case SpawnWasteSource(capacity, location) =>
          val orchestrator = state.garbageOrchestrators.head
          val wasteSource  = state.wasteSourceFactory.spawn(location, capacity, orchestrator)
          context.log.info(s"Waste Source at {} created. Capacity {}", location, capacity)
          simulate(state.copy(wasteSources = state.wasteSources :+ wasteSource))

        case SpawnWasteSink(efficiency, storageCapacity, location) =>
          val orchestrator = state.garbageOrchestrators.head
          val wasteSink    = state.wasteSinkFactory.spawn(location, efficiency, storageCapacity, orchestrator)
          context.log.info(s"Waste Sink at ({},{}) created. Processing power {}, capacity {}", location._1, location._2, efficiency, storageCapacity)
          wasteSink ! AttachOrchestrator(1, orchestrator)
          simulate(state.copy(wasteSinks = state.wasteSinks :+ wasteSink))

        case SpawnGarbageCollector(capacity, location, speed) =>
          val collector = state.garbageCollectorFactory.spawn(capacity, location, speed)
          collector ! GarbageCollector.AttachOrchestrator(0, state.garbageOrchestrators.head)
          context.log.info(s"Garbage collector at ({},{}) created. Capacity {}", location._1, location._2, capacity)
          simulate(state.copy(garbageCollectors = state.garbageCollectors :+ collector))

        case SpawnGarbageOrchestrator() =>
          val orchestrator = state.garbageOrchestratorFactory.spawn()
          context.log.info(s"Garbage orchestrator created.")
          simulate(state.copy(garbageOrchestrators = state.garbageOrchestrators :+ orchestrator))

        case SourceSimulationTick() =>
          state.wasteSources.foreach { source =>
            val amount = state.sourceDist.sample()
            source ! WasteSource.ProduceGarbage(amount)
          }
          Behaviors.same

        case SinkSimulationTick() =>
          state.wasteSinks.foreach { sink =>
            sink ! WasteSink.ProcessGarbage()
          }
          Behaviors.same

        case CollectorSimulationTick() =>
          state.garbageCollectors.foreach { collector =>
            collector ! GarbageCollector.Move()
          }
          Behaviors.same
        case Status(replyTo) =>
          replyTo ! AllActors(sources = state.wasteSources, sinks = state.wasteSinks, orchestrators = state.garbageOrchestrators, collectors = state.garbageCollectors)
          Behaviors.same
      }
    }

  sealed trait Command
  final case class State(
    sourceDist: PoissonDistribution,
    sourceDistParam: Double,
    moveDistParams: (Int, Int),
    wasteSourceFactory: WasteSourceFactory[_],
    wasteSinkFactory: WasteSinkFactory[_],
    garbageCollectorFactory: GarbageCollectorFactory[_],
    garbageOrchestratorFactory: GarbageOrchestratorFactory[_],
    wasteSources: List[ActorRef[WasteSource.Command]] = List.empty,
    wasteSinks: List[ActorRef[WasteSink.Command]] = List.empty,
    garbageCollectors: List[ActorRef[GarbageCollector.Command]] = List.empty,
    garbageOrchestrators: List[ActorRef[GarbageOrchestrator.Command]] = List.empty
  )

  final case class SourceSimulationTick()                                                      extends Command
  final case class SinkSimulationTick()                                                        extends Command
  final case class CollectorSimulationTick()                                                   extends Command
  final case class SpawnWasteSource(capacity: Int, location: (Int, Int))                       extends Command
  final case class SpawnWasteSink(efficiency: Int, storageCapacity: Int, location: (Int, Int)) extends Command
  final case class SpawnGarbageCollector(capacity: Int, location: (Int, Int), speed: Int)      extends Command
  final case class SpawnGarbageOrchestrator()                                                  extends Command
  final case class Status(replyTo: ActorRef[AllActors])                                        extends Command
}
