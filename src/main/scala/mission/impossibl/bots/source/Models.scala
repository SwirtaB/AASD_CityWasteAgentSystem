package mission.impossibl.bots.source

import akka.actor.Cancellable
import akka.actor.typed.ActorRef
import mission.impossibl.bots.orchestrator.GarbageOrchestrator

import scala.concurrent.duration.FiniteDuration

final case class Instance(id: Int, location: (Int, Int), capacity: Int, orchestrator: ActorRef[GarbageOrchestrator.Command])

final case class State(garbage: Int = 0, score: Int = 0, estimatedCollectorArrival: Option[FiniteDuration] = None, auctionTimeout: Option[Cancellable] = None, collectionTimeout: Option[Cancellable] = None)
