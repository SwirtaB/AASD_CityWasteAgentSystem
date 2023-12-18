package mission.impossibl.bots.orchestrator

import akka.actor.Cancellable
import akka.actor.typed.ActorRef
import mission.impossibl.bots.collector.GarbageCollector
import mission.impossibl.bots.source.WasteSource

import java.util.UUID

final case class Instance(id: Int)

final case class State(
                        garbageCollectors: List[ActorRef[GarbageCollector.Command]],
                        collectionAuctionsInProgress: Map[UUID, CollectionAuction] = Map.empty[UUID, CollectionAuction]
                      )

/**
 * Auction for collection of garbage from source.
 */
final case class CollectionAuction(
                                    auctionId: UUID,
                                    expected: Int,
                                    received: List[CollectionAuctionOffer],
                                    timeoutRef: Cancellable,
                                    collectionInfo: GarbageToCollect
                                  )

final case class GarbageToCollect(
                                   garbageAmount: Int,
                                   location: (Int, Int),
                                   sourceId: Int,
                                   sourceRef: ActorRef[WasteSource.Command]
                                 )

/**
 * Offer for collection of garbage from source.
 */
final case class CollectionAuctionOffer(gcRef: ActorRef[GarbageCollector.Command]) // todo more auction offer info)
