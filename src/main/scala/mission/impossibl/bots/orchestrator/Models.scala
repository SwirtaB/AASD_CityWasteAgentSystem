package mission.impossibl.bots.orchestrator

import akka.actor.Cancellable
import akka.actor.typed.ActorRef
import mission.impossibl.bots.{GarbageCollector, WasteSource}

import java.util.UUID

final case class Instance(id: Int)

final case class State(
                        garbageCollectors: List[ActorRef[GarbageCollector.Command]],
                        auctionsInProgress: Map[UUID, Auction] = Map.empty[UUID, Auction]
                      )

final case class Auction(
                          auctionId: UUID,
                          expected: Int,
                          received: List[AuctionOffer],
                          timeoutRef: Cancellable,
                          collectionInfo: GarbageCollectionInfo
                        )

final case class GarbageCollectionInfo(
                                        garbageAmount: Int,
                                        location: (Int, Int),
                                        sourceId: Int,
                                        sourceRef: ActorRef[WasteSource.Command]
                                      )

final case class AuctionOffer(gcRef: ActorRef[GarbageCollector.Command]) // todo more auction offer info)
