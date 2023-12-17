package mission.impossibl.bots.collector

import akka.actor.typed.ActorRef
import mission.impossibl.bots.orchestrator.GarbageOrchestrator
import mission.impossibl.bots.source.WasteSource

import java.util.UUID

final case class Instance(id: Int, capacity: Int, orchestrator: ActorRef[GarbageOrchestrator.Command])

final case class State(
                        currentLocation: (Int, Int),
                        visitedSources: List[GarbagePathElem] = List.empty,
                        futureSources: List[GarbagePathElem] = List.empty,
                        carriedGarbage: Int = 0,
                        reservedSpace: Int = 0,
                        ongoingAuctions: Map[UUID, Garbage] = Map.empty
                      )

final case class Garbage(
                          location: (Int, Int),
                          amount: Int
                        )

final case class GarbagePathElem(
                                  location: (Int, Int),
                                  amount: Int,
                                  ref: ActorRef[WasteSource.Command]
                                )
