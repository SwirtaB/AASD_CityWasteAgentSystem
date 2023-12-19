package mission.impossibl.bots.sink

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, Behavior}
import mission.impossibl.bots.orchestrator.GarbageOrchestrator.GarbageDisposalProposal
import mission.impossibl.bots.orchestrator.{DisposalAuctionOffer, GarbageOrchestrator}

import java.util.UUID

object WasteSink {
  def apply(instance: Instance, processing_power: Float): Behavior[Command] = {
    sink(instance, State(processing_power, 0.0f, Map.empty[Int, GarbagePacket]))
  }

  private def sink(instance: Instance, state: State): Behavior[Command] =
    Behaviors.receive {
      (context, message) => {
        message match {
          case AttachOrchestrator(orchestratorId, orchestratorRef) =>
            context.log.info("Sink{} attached to Orchestrator{}", instance.id, orchestratorId)
            orchestratorRef ! GarbageOrchestrator.WasteSinkRegistered(context.self)
            sink(instance.copy(orchestrator = orchestratorRef), state)

          case GarbageDisposalCallForProposal(auctionId, collectorId, garbageAmount) =>
            context.log.info("Received Garbage Disposal CFP from Collector{} for {} kg of garbage", collectorId, garbageAmount)
            // TODO: check processing capabilities before accepting the proposal
            val auctionOffer = DisposalAuctionOffer(context.self)
            instance.orchestrator ! GarbageDisposalProposal(auctionId, auctionOffer)
            sink(instance, state.copy(ongoingAuctions = state.ongoingAuctions.updated(auctionId, garbageAmount)))

          case GarbageDisposalAccepted(auctionId) =>
            context.log.info("WasteSink{} won auction id {}", instance.id, auctionId)
            val garbage = state.ongoingAuctions.get(auctionId)
            if (garbage.isEmpty) {
              context.log.info("Won action {} I don't rember of :)", auctionId)
              return Behaviors.same
            }
            val updatedAuctions = state.ongoingAuctions.removed(auctionId)
            sink(instance, state.copy(ongoingAuctions = updatedAuctions))

          case GarbageDisposalRejected(auctionId) =>
            Behaviors.same

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

  sealed trait Command

  final case class ProcessGarbage(garbage_packet_id: Int) extends Command

  final case class ReceiveGarbage(packet: GarbagePacket) extends Command

  final case class AttachOrchestrator(orchestratorId: Int, orchestratorRef: ActorRef[GarbageOrchestrator.Command]) extends Command

  final case class GarbageDisposalCallForProposal(auctionId: UUID, collectorId: Int, garbageAmount: Int) extends Command

  final case class GarbageDisposalAccepted(auctionId: UUID) extends Command

  final case class GarbageDisposalRejected(auctionId: UUID) extends Command

}
