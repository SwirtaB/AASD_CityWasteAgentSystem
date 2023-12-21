package mission.impossibl.bots.sink

import akka.actor.typed.ActorRef
import mission.impossibl.bots.orchestrator.GarbageOrchestrator

import java.util.UUID

final case class Garbage(amount: Int)

final case class Instance(id: Int,
                          location: (Int, Int),
                          storage_capacity: Float,
                          orchestrator: ActorRef[GarbageOrchestrator.Command])

final case class GarbagePacketRecord(waste_source_id: Int, waste_type: Int, waste_mass: Float)

final case class GarbagePacket(records: List[GarbagePacketRecord], total_mass: Float)

final case class State(processing_power: Float, garbage_level: Float, garbage_packets: Map[Int, GarbagePacket], ongoingAuctions: Map[UUID, Int] = Map.empty)
