package mission.impossibl.bots.http

import akka.actor.typed.{ActorRef, ActorSystem}
import akka.util.Timeout
import mission.impossibl.bots.EnvironmentSimulator

import scala.concurrent.Future
import scala.concurrent.duration.DurationInt
import akka.actor.typed.scaladsl.AskPattern.{Askable, schedulerFromActorSystem}
import akka.http.scaladsl.model.StatusCodes.InternalServerError
import scala.util.{Failure, Success}

import akka.http.scaladsl.server.Directives
class MissionApi(val environment: ActorRef[EnvironmentSimulator.Status])(implicit system: ActorSystem[_]) extends JsonSupport with Directives {

  val routes =
    path("status") {
      get {
        onComplete(simulationState()) {
          case Failure(_)     => complete(InternalServerError, "Could not get response from system")
          case Success(value) => complete(value)
        }
      }
    } /* ~ path("create"){
      path("collector") & post {

      } ~ path("sink") & post {

      } ~ path("source") & post {

      }
    }*/
  private def simulationState(): Future[EnvironmentReply] = {
    implicit val timeout: Timeout = 1.second

    environment.ask(ref => EnvironmentSimulator.Status(ref))
  }
}
