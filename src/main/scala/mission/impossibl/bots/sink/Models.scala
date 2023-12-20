package mission.impossibl.bots.sink

import akka.actor.Cancellable
import akka.actor.typed.ActorRef
import mission.impossibl.bots.orchestrator.GarbageOrchestrator

import java.util.UUID

final case class Garbage(amount: Int)

final case class Instance(id: UUID, location: (Int, Int), storageCapacity: Float, orchestrator: ActorRef[GarbageOrchestrator.Command])

final case class GarbagePacketRecord(wasteSourceId: Int, wasteType: Int, wasteMass: Float)

final case class GarbagePacket(records: List[GarbagePacketRecord], totalMass: Float)

final case class State(processingPower: Float, garbageLevel: Float, garbagePackets: Map[Int, GarbagePacket], reservedSpace: List[Reservation] = List.empty)

final case class Reservation(auctionId: UUID, garbageCollectorId: UUID, wasteMass: Float, timeout: Cancellable)
