package mission.impossibl.bots

import akka.actor.typed.ActorSystem
import mission.impossibl.bots.http.MissionApi

import scala.concurrent.ExecutionContextExecutor
import scala.concurrent.duration._
import scala.io.StdIn
import akka.actor.typed.scaladsl.Behaviors
import akka.http.scaladsl.Http

object MissionImpossiBots extends App {
  implicit val system: ActorSystem[EnvironmentSimulator.Command] = ActorSystem(Behaviors.empty, "main")
  val environment                                                = system.systemActorOf(EnvironmentSimulator(), "EnvSim")

  environment ! EnvironmentSimulator.SpawnGarbageOrchestrator()
  environment ! EnvironmentSimulator.SpawnWasteSource(20, (1, 1))
  environment ! EnvironmentSimulator.SpawnWasteSink(100, 100, (20, 20))
  environment ! EnvironmentSimulator.SpawnGarbageCollector(30, (5, 5), 5)
  environment ! EnvironmentSimulator.SpawnGarbageCollector(30, (5, 5), 3)

  implicit val ec: ExecutionContextExecutor = system.executionContext
  system.scheduler.scheduleAtFixedRate(10.seconds, 10.seconds)(() => environment ! EnvironmentSimulator.SourceSimulationTick())
  system.scheduler.scheduleAtFixedRate(10.seconds, 10.seconds)(() => environment ! EnvironmentSimulator.SinkSimulationTick())
  system.scheduler.scheduleAtFixedRate(1.second, 1.second)(() => environment ! EnvironmentSimulator.CollectorSimulationTick())

  val api           = new MissionApi(environment)
  val bindingFuture = Http().newServerAt("localhost", 8080).bind(api.routes())
  StdIn.readLine()
  bindingFuture.flatMap(_.unbind()).onComplete(_ => system.terminate())
}
