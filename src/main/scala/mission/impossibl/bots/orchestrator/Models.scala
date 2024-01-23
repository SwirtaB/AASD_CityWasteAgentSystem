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

final case class Instance(id: UUID)

final case class State(
  garbageCollectors: List[ActorRef[GarbageCollector.Command]] = List.empty,
  wasteSinks: List[ActorRef[WasteSink.Command]] = List.empty,
  auctionsInProgress: Map[UUID, Auction] = Map.empty[UUID, Auction],
  wasteSources: Map[UUID, ActorRef[WasteSource.Command]] = Map.empty[UUID, ActorRef[WasteSource.Command]]
)

sealed trait Auction
final case class CollectionAuction(
  auctionId: UUID,
  expected: Int,
  received: List[CollectionAuctionOffer],
  timeoutRef: Cancellable,
  collectionDetails: CollectionDetails
) extends Auction

final case class DisposalAuction(
  auctionId: UUID,
  expected: Int,
  received: List[DisposalAuctionOffer],
  timeoutRef: Cancellable,
  disposalDetails: DisposalDetails,
  collectorRef: ActorRef[GarbageCollector.Command]
) extends Auction

final case class CollectionDetails(
  garbageAmount: Int,
  location: (Int, Int),
  sourceId: UUID,
  sourceRef: ActorRef[WasteSource.Command]
)

final case class DisposalDetails(
  garbageAmount: Int,
  collectorId: UUID,
  location: (Int, Int)
)

final case class CollectionAuctionOffer(
                                         collectorRef: ActorRef[GarbageCollector.Command],
                                         estimatedArrival: FiniteDuration,
                                         capacity: Int
)

final case class DisposalAuctionOffer(wasteSink: ActorRef[WasteSink.Command], location: (Int, Int))
