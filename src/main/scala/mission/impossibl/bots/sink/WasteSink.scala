package mission.impossibl.bots.sink

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, Behavior}
import mission.impossibl.bots.orchestrator.GarbageOrchestrator
import mission.impossibl.bots.Utils
import mission.impossibl.bots.sink.WasteSink.{GarbagePacket, ProcessGarbage, State}
import org.apache.commons.math3.distribution.PoissonDistribution

import java.util.UUID
import scala.concurrent.duration._

object WasteSink {
  def apply(instance: Instance, processing_power: Float): Behavior[Command] = {
    sink(instance, State(processing_power, 0.0f, Map.empty[Int, GarbagePacket]))
  }

  private def sink(instance: Instance, state: State): Behavior[Command] =
    Behaviors.receive { (context, message) =>
      message match {
        case AttachOrchestrator(orchestratorId, orchestratorRef) =>
          context.log.info("Sink{} attached to Orchestrator{}", instance.id, orchestratorId)
          orchestratorRef ! GarbageOrchestrator.WasteSinkRegistered(context.self)
          sink(instance.copy(orchestrator = orchestratorRef), state)

        case GarbageDisposalCallForProposal(auctionId, collectorId, garbageAmount) =>
          context.log.info("Received Garbage Disposal CFP from Collector{} for {} kg of garbage", collectorId, garbageAmount)
          val emptySpace = calcEmptySpace(state.garbagePackets, state.reservedSpace, instance.storageCapacity)
          if (emptySpace >= garbageAmount) {
            val auctionOffer = DisposalAuctionOffer(context.self, instance.location)
            instance.orchestrator ! GarbageDisposalProposal(auctionId, auctionOffer)

            val timeout = context.scheduleOnce(AuctionTimeoutVal, context.self, ReservationTimeout(auctionId))
            sink(instance, state.copy(reservedSpace = state.reservedSpace.appended(Reservation(auctionId, collectorId, garbageAmount.toFloat, timeout))))
          } else {
            Behaviors.same
          }

        case GarbageDisposalAccepted(auctionId) =>
          context.log.info("Won auction {}", auctionId)
          val reservation = state.reservedSpace.find(_.auctionId == auctionId)
          reservation match {
            case Some(reservation) =>
              reservation.timeout.cancel()
              val reservationTimeout  = context.scheduleOnce(ReservationTimeoutVal, context.self, ReservationTimeout(auctionId))
              val updatedReservations = state.reservedSpace.filterNot(_.auctionId == auctionId).appended(reservation.copy(timeout = reservationTimeout))
              sink(instance, state.copy(reservedSpace = updatedReservations))

            case None =>
              context.log.info("Won action {} I don't remember of :)", auctionId)
              Behaviors.same
          }
        case GarbageDisposalRejected(auctionId) =>
          val reservations = state.reservedSpace.filterNot(_.auctionId == auctionId) // drop reservation from rejected auction
          sink(instance, state.copy(reservedSpace = reservations))

        case ReceiveGarbage(packet, collectorId) => // updates state on garbage receive
          context.log.info(
            "Received {} kg of garbage from {}.",
            packet.totalMass,
            collectorId
          )
          sink(instance, state.copy(garbagePackets = state.garbagePackets :+ packet))

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
        case ReservationTimeout(auctionId) =>
          val reservations = state.reservedSpace.filterNot(_.auctionId == auctionId) // drop reservation from rejected auction
          sink(instance, state.copy(reservedSpace = reservations))
        }
      }

    }

private def calcEmptySpace(garbagePackets: List[GarbagePacket], reservations: List[Reservation], capacity: Float): Float =
  capacity - reservations.map(_.wasteMass).sum - garbagePackets.map(_.totalMass).sum

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

  final case class ReceiveGarbage(packet: GarbagePacket, collectorId: UUID) extends Command
  final case class GarbagePacketRecord(wasteSourceId: Int, wasteType: Int, mass: Int)

  final case class GarbagePacket(records: List[GarbagePacketRecord], totalMass: Int)

  final case class State(efficiency: UUID, garbageLevel: Int, garbagePackets: List[GarbagePacket])

  final case class ProcessGarbage() extends Command

  final case class GarbageDisposalRejected(auctionId: UUID) extends Command

  private final case class ReservationTimeout(auctionId: UUID) extends Command

}
