package mission.impossibl.bots.sink

import akka.actor.Cancellable
import akka.actor.typed.ActorRef
import mission.impossibl.bots.orchestrator.GarbageOrchestrator

import java.util.UUID

final case class Garbage(amount: Int)

final case class Instance(id: UUID, location: (Int, Int), storageCapacity: Int, orchestrator: ActorRef[GarbageOrchestrator.Command])

final case class GarbagePacketRecord(wasteSourceId: UUID, wasteType: Int = 0, wasteMass: Int)

final case class GarbagePacket(records: List[GarbagePacketRecord], totalMass: Int)

final case class State(id: UUID, efficiency: Int, garbagePackets: List[GarbagePacket] = List.empty, reservedSpace: List[Reservation] = List.empty)

final case class Reservation(auctionId: UUID, garbageCollectorId: UUID, wasteMass: Int, timeout: Cancellable)
