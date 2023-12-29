package mission.impossibl.bots.http

import akka.actor.typed.{ActorRef, ActorSystem}
import akka.util.Timeout
import mission.impossibl.bots.EnvironmentSimulator

import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration.DurationInt
import akka.actor.typed.scaladsl.AskPattern.{Askable, schedulerFromActorSystem}
import akka.http.scaladsl.model.StatusCodes.{InternalServerError, Created}

import scala.util.{Failure, Success}
import akka.http.scaladsl.server.{Directives, Route}
import mission.impossibl.bots.collector.GarbageCollector
import mission.impossibl.bots.orchestrator.GarbageOrchestrator
import mission.impossibl.bots.sink.WasteSink
import mission.impossibl.bots.source.WasteSource

class MissionApi(val environment: ActorRef[EnvironmentSimulator.Command])(implicit system: ActorSystem[_]) extends JsonSupport with Directives {
  implicit val timeout: Timeout = 1.second
  def routes()(implicit ec: ExecutionContext): Route =
    path("status") {
      get {
        onComplete(simulationState()) {
          case Failure(_)     => complete(InternalServerError, "Could not get response from system")
          case Success(value) => complete(value)
        }
      }
    } ~ pathPrefix("collector") {
      pathPrefix("spawn") {
        post {
          entity(as[CollectorParams]) { params =>
            spawnCollector(params)
            complete(Created)
          }
        }
      }

    } ~ pathPrefix("source") {
      pathPrefix("spawn") {
        post {
          entity(as[WasteSourceParams]) { params =>
            spawnWasteSource(params)
            complete(Created)
          }
        }
      }
    } ~ pathPrefix("sink") {
      pathPrefix("spawn")  {
        post {
          entity(as[WasteSinkParams]) { params =>
            spawnWasteSink(params)
            complete(Created)
          }
        }
      }
    } ~ post {
      path(Remaining) { path =>
        complete(path)
      }
    }
  private def simulationState()(implicit ec: ExecutionContext): Future[EnvironmentResponse] =
    environment.ask(ref => EnvironmentSimulator.Status(ref)).flatMap { x =>
      val sinkStatusesFuture          = Future.sequence(x.sinks.map(sink => sink.ask(ref => WasteSink.Status(ref))))
      val sourceStatusesFuture        = Future.sequence(x.sources.map(source => source.ask(ref => WasteSource.Status(ref))))
      val collectorsStatusesFuture    = Future.sequence(x.collectors.map(coll => coll.ask(ref => GarbageCollector.Status(ref))))
      val orchestratorsStatusesFuture = Future.sequence(x.orchestrators.map(orch => orch.ask(ref => GarbageOrchestrator.Status(ref))))
      for {
        sinkStatuses         <- sinkStatusesFuture
        sourceStatuses       <- sourceStatusesFuture
        collectorStatuses    <- collectorsStatusesFuture
        orchestratorStatuses <- orchestratorsStatusesFuture
      } yield EnvironmentResponse(sourceStatuses, sinkStatuses, collectorStatuses, orchestratorStatuses)
    }

  private def spawnCollector(params: CollectorParams): Unit =
    environment ! EnvironmentSimulator.SpawnGarbageCollector(params.capacity, (params.location.x, params.location.y), params.speed)

  private def spawnWasteSource(params: WasteSourceParams): Unit =
    environment ! EnvironmentSimulator.SpawnWasteSource(params.capacity, (params.location.x, params.location.y))

  private def spawnWasteSink(params: WasteSinkParams): Unit =
    environment ! EnvironmentSimulator.SpawnWasteSink(params.efficiency, params.storageCapacity, (params.location.x, params.location.y))
}
