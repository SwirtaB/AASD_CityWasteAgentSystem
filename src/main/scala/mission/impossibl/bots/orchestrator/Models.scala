/** NOTE: collection = collector fetches the garbage from the source disposal = collector drives garbage to the processing facility (sink)
  */

package mission.impossibl.bots.orchestrator

import akka.actor.Cancellable
import akka.actor.typed.ActorRef
import mission.impossibl.bots.collector.GarbageCollector
import mission.impossibl.bots.sink.WasteSink
import mission.impossibl.bots.source.WasteSource

import java.util.UUID
import scala.concurrent.duration.FiniteDuration

final case class Instance(id: Int)

final case class State(
  garbageCollectors: List[ActorRef[GarbageCollector.Command]],
  wasteSinks: List[ActorRef[WasteSink.Command]],
  collectionAuctionsInProgress: Map[UUID, CollectionAuction] = Map.empty[UUID, CollectionAuction],
  disposalAuctionsInProgress: Map[UUID, DisposalAuction] = Map.empty[UUID, DisposalAuction]
)

final case class CollectionAuction(
  auctionId: UUID,
  expected: Int,
  received: List[CollectionAuctionOffer],
  timeoutRef: Cancellable,
  collectionDetails: CollectionDetails
)

final case class DisposalAuction(
  auctionId: UUID,
  expected: Int,
  received: List[DisposalAuctionOffer],
  timeoutRef: Cancellable,
  disposalDetails: DisposalDetails
)

final case class CollectionDetails(
  garbageAmount: Int,
  location: (Int, Int),
  sourceId: Int,
  sourceRef: ActorRef[WasteSource.Command]
)

final case class DisposalDetails(
  garbageAmount: Int,
  collectorId: UUID
)

final case class CollectionAuctionOffer(
  gcRef: ActorRef[GarbageCollector.Command],
  when: FiniteDuration
)

final case class DisposalAuctionOffer(wasteSink: ActorRef[WasteSink.Command], location: (Int, Int) = (0, 0)) // todo more auction offer info)
