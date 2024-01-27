package mission.impossibl.bots.collector

import akka.actor.Cancellable
import akka.actor.typed.ActorRef
import mission.impossibl.bots.orchestrator.GarbageOrchestrator
import mission.impossibl.bots.sink.WasteSink
import mission.impossibl.bots.source.WasteSource

import java.util.UUID

final case class Instance(id: UUID, capacity: Int, speed: Int = 5, orchestrator: ActorRef[GarbageOrchestrator.Command] = null)

final case class State(
  currentLocation: (Int, Int),
  visitedSources: List[GarbagePathElem] = List.empty,
  futureSources: List[GarbagePathElem] = List.empty,
  carriedGarbage: Int = 0,
  ongoingCollectionAuctions: Map[UUID, Garbage] = Map.empty,
  disposalAuctionTimeout: Option[Cancellable] = None,
  disposalPoint: Option[DisposalPoint] = None,
  auctionMissed: Int = 0
)
final case class DisposalPoint(
  location: (Int, Int),
  ref: ActorRef[WasteSink.Command],
  auctionId: UUID
)

final case class Garbage(
  location: (Int, Int),
  amount: Int
)

final case class GarbagePathElem(
  location: (Int, Int),
  amount: Int,
  id: UUID,
  ref: ActorRef[WasteSource.Command]
)
