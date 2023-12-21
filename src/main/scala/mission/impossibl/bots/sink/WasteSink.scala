package mission.impossibl.bots.sink

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, Behavior}
import mission.impossibl.bots.orchestrator.GarbageOrchestrator
import mission.impossibl.bots.Utils

import org.apache.commons.math3.distribution.PoissonDistribution

object WasteSink {
  def apply(instance: Instance, efficiency: Int): Behavior[Command] = {
    sink(instance, State(efficiency, 0, List.empty[GarbagePacket]))
  }

  private def sink(instance: Instance, state: State): Behavior[Command] =
    Behaviors.receive {
      (context, message) => {
        message match {
          case ReceiveGarbage(packet) => // updates state on garbage receive
            context.log.info(
              s"Sink{}: Received {} kg of garbage.",
              instance.id, packet.totalMass
            )
            sink(instance, State(state.efficiency, state.garbageLevel + packet.totalMass, state.garbagePackets :+ packet))
          case ProcessGarbage() => // simulates garbage processing
            val garbagePacket = state.garbagePackets.head
            // shift dist from N(0, 1) to N(efficiency, 1) and convert to discrete number
            val processedWaste = Utils.sample_normal(state.efficiency, 1)
            context.log.info(s"Sink{}: Processed {} kg of garbage.", instance.id, processedWaste)

            val remainingWaste = garbagePacket.totalMass - processedWaste
            if (remainingWaste > 0) {
              val updatedGarbagePacket = GarbagePacket(garbagePacket.records, remainingWaste)
              sink(instance,
                State(state.efficiency,
                      state.garbageLevel - processedWaste,
                      state.garbagePackets.updated(0, updatedGarbagePacket)))
            }
            else {
              // remainingWaste <= 0
              val newHead = state.garbagePackets(1)
              val updatedGarbagePacket = GarbagePacket(newHead.records, newHead.totalMass - math.abs(remainingWaste))

              // TODO garbage score messages
              // val garbage_score = score_garbage(garbage_packet_records)
              // for (record <- garbage_packet_records) instance.orchestrator ! GarbageOrchestrator.GarbageScore(record.wasteSourceId, garbage_score)

              sink(instance, State(state.efficiency,
                state.garbageLevel - processedWaste,
                state.garbagePackets.tail.updated(0, updatedGarbagePacket)))
            }
        }
      }
    }

  private def score_garbage(records: List[GarbagePacketRecord]): Int = {
      // FIXME You should use different distribution since Poisson models number of events in time period.
      // Imo normal dist will be good enough (ofc you should cast results to int)
      val scoreDist = new PoissonDistribution(3.0)
      val score = scoreDist.sample()
      score
    }

  sealed trait Command

  final case class Instance(id: Int,
                            location: (Int, Int),
                            storageCapacity: Int,
                            orchestrator: ActorRef[GarbageOrchestrator.Command])

  final case class GarbagePacketRecord(wasteSourceId: Int, wasteType: Int, mass: Int)

  final case class GarbagePacket(records: List[GarbagePacketRecord], totalMass: Int)

  final case class State(efficiency: Int, garbageLevel: Int, garbagePackets: List[GarbagePacket])

  final case class ProcessGarbage() extends Command

  final case class ReceiveGarbage(packet: GarbagePacket) extends Command

}
