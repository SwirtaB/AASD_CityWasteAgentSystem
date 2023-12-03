package mission.impossibl.bots

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, Behavior}
import java.util.UUID

object WasteSink {
  sealed trait Command
  private def sink(instance: Instance, state: State): Behavior[Command] =
    Behaviors.receive {
      (context, message) => {
        message match {
          case ReceiveGarbage(packet) => // updates state on garbage receive
            context.log.info(
              s"Sink{}: Received {} kg of garbage.",
              instance.id, packet.total_mass
            )
            val packet_uuid = 1 // For testing only, change to UUID later
            sink(instance, State(state.processing_power,
                                 state.garbage_level + packet.total_mass,
                                 state.garbage_packets.updated(packet_uuid, packet)))
          case ProcessGarbage(garbage_packet_id) => // simulates garbage processing
            val garbage_packet = state.garbage_packets.get(garbage_packet_id)
            val processed_garbage = garbage_packet.get.total_mass
            context.log.info(
              s"Sink{}: Processed {} kg of garbage.",
              instance.id, processed_garbage
            )
            sink(instance, State(state.processing_power,
                                 state.garbage_level - processed_garbage,
                                 state.garbage_packets.removed(garbage_packet_id)))
        }
      }
    }

  def apply(instance: Instance, processing_power: Float): Behavior[Command] = {
    sink(instance, State(processing_power, 0.0f, Map.empty[Int, GarbagePacket]))
  }
  final case class Instance(id: Int,
                            location: (Int, Int),
                            storage_capacity: Float,
                            orchestrator: ActorRef[GarbageOrchestrator.Command])
  final case class GarbagePacketRecord(waste_source_id: Int, waste_type: Int, waste_mass: Float)
  final case class GarbagePacket(records: List[GarbagePacketRecord], total_mass: Float)
  final case class State(processing_power: Float, garbage_level: Float, garbage_packets: Map[Int, GarbagePacket])
  final case class ProcessGarbage(garbage_packet_id: Int) extends Command
  final case class ReceiveGarbage(packet: GarbagePacket) extends Command

}
