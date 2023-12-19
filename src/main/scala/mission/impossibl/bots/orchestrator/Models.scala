package mission.impossibl.bots.orchestrator

import akka.actor.Cancellable
import akka.actor.typed.ActorRef
import mission.impossibl.bots.collector.GarbageCollector
import mission.impossibl.bots.source.WasteSource

import java.util.UUID

final case class Instance(id: Int)

final case class State(
                        garbageCollectors: List[ActorRef[GarbageCollector.Command]],
                        auctionsInProgress: Map[UUID, Auction] = Map.empty[UUID, Auction],
                        wasteSources: Map[Int, ActorRef[WasteSource.Command]] = Map.empty[Int, ActorRef[WasteSource.Command]],
                      )

final case class Auction(
                          auctionId: UUID,
                          expected: Int,
                          received: List[AuctionOffer],
                          timeoutRef: Cancellable,
                          collectionInfo: GarbageToCollect
                        )

final case class GarbageToCollect(
                                   garbageAmount: Int,
                                   location: (Int, Int),
                                   sourceId: Int,
                                   sourceRef: ActorRef[WasteSource.Command]
                                 )

final case class AuctionOffer(gcRef: ActorRef[GarbageCollector.Command]) // todo more auction offer info)
