package mission.impossibl.bots.sink

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, Behavior}
import mission.impossibl.bots.orchestrator.{DisposalAuctionOffer, GarbageOrchestrator}
import mission.impossibl.bots.Utils
import mission.impossibl.bots.http.SinkStatus
import mission.impossibl.bots.orchestrator.GarbageOrchestrator.GarbageDisposalProposal
import org.apache.commons.math3.distribution.PoissonDistribution

import java.util.UUID
import scala.concurrent.duration._

object WasteSink {

  private val ReservationTimeoutVal = 2.minutes
  private val AuctionTimeoutVal     = 10.seconds
  def apply(instance: Instance, efficiency: Int): Behavior[Command] =
    sink(instance, State(UUID.randomUUID(), efficiency))

  private def sink(instance: Instance, state: State): Behavior[Command] =
    Behaviors.receive { (context, message) =>
      message match {
        case AttachOrchestrator(orchestratorId, orchestratorRef) =>
          context.log.info("Sink {} attached to Orchestrator {}", instance.id, orchestratorId)
          orchestratorRef ! GarbageOrchestrator.WasteSinkRegistered(context.self, state.id)
          sink(instance.copy(orchestrator = orchestratorRef), state)

        case GarbageDisposalCallForProposal(auctionId, collectorId, garbageAmount) =>
          context.log.info("Received Garbage Disposal CFP {} from Collector{} for {} kg of garbage", auctionId, collectorId, garbageAmount)
          val emptySpace = calcEmptySpace(state.garbagePackets, state.reservedSpace, instance.storageCapacity)
          if (emptySpace >= garbageAmount) {
            val auctionOffer = DisposalAuctionOffer(context.self, instance.location)
            instance.orchestrator ! GarbageDisposalProposal(auctionId, auctionOffer)

            val timeout = context.scheduleOnce(AuctionTimeoutVal, context.self, ReservationTimeout(auctionId))
            sink(instance, state.copy(reservedSpace = state.reservedSpace.appended(Reservation(auctionId, collectorId, garbageAmount, timeout))))
          } else {
            context.log.info("Ignoring CFP {}", auctionId)
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

        case ReceiveGarbage(packet, collectorId, auctionId) => // updates state on garbage receive
          context.log.info(
            "Received {} kg of garbage from {}.",
            packet.totalMass,
            collectorId
          )
          // drop reservations on garbage receival
          val reservations = state.reservedSpace.filterNot(_.auctionId == auctionId)
          sink(instance, state.copy(garbagePackets = state.garbagePackets.appended(packet), reservedSpace = reservations))

        case ProcessGarbage() => // simulates garbage processing
          state.garbagePackets.headOption match {
            case Some(_) => // shift dist from N(0, 1) to N(efficiency, 1) and convert to discrete number
              val wasteToProcess                          = Utils.sampleNormal(state.efficiency, 1)
              val (updatedGarbagePackets, processedWaste) = processGarbageTraverse(instance.orchestrator, wasteToProcess, state.garbagePackets)
              context.log.info(s"Processed {} kg of garbage.", processedWaste)
              sink(instance, state.copy(garbagePackets = updatedGarbagePackets))
            case None => Behaviors.same
          }

        case ReservationTimeout(auctionId) =>
          val reservations = state.reservedSpace.filterNot(_.auctionId == auctionId) // drop reservation from rejected auction
          sink(instance, state.copy(reservedSpace = reservations))

        case Status(replyTo) =>
          replyTo ! SinkStatus(instance.id, state.efficiency, instance.location, instance.storageCapacity, state.garbagePackets, state.reservedSpace.map(_.wasteMass).sum)
          Behaviors.same
      }

    }

  private def calcEmptySpace(garbagePackets: List[GarbagePacket], reservations: List[Reservation], capacity: Int): Int =
    capacity - reservations.map(_.wasteMass).sum - garbagePackets.map(_.totalMass).sum

  private def processGarbageTraverse(orchestratorRef: ActorRef[GarbageOrchestrator.Command], processedWaste: Int, garbagePackets: List[GarbagePacket]): (List[GarbagePacket], Int) =
    garbagePackets.headOption match {
      case Some(garbagePacket: GarbagePacket) =>
        val remainingWaste = garbagePacket.totalMass - processedWaste
        if (remainingWaste > 0) {
          val updatedGarbagePacket = GarbagePacket(garbagePacket.records, remainingWaste)
          (garbagePackets.updated(0, updatedGarbagePacket), processedWaste)
        } else {
          val score = Utils.sampleNormal(0, 10)
          for (packet <- garbagePackets)
            for (packetRecord <- packet.records)
              orchestratorRef ! GarbageOrchestrator.GarbageScore(packetRecord.wasteSourceId, score)
          val result = processGarbageTraverse(orchestratorRef, math.abs(remainingWaste), garbagePackets.tail)
          (result._1, result._2 + garbagePacket.totalMass)
        }
      case None => (List.empty, 0)
    }

  sealed trait Command

  final case class ReceiveGarbage(packet: GarbagePacket, collectorId: UUID, auctionId: UUID) extends Command

  final case class ProcessGarbage() extends Command

  final case class GarbageDisposalCallForProposal(auctionId: UUID, collectorId: UUID, garbageAmount: Int) extends Command

  final case class GarbageDisposalAccepted(auctionId: UUID) extends Command

  final case class GarbageDisposalRejected(auctionId: UUID) extends Command

  private final case class ReservationTimeout(auctionId: UUID) extends Command

  final case class AttachOrchestrator(orchestratorId: Int, orchestratorRef: ActorRef[GarbageOrchestrator.Command]) extends Command

  final case class Status(replyTo: ActorRef[SinkStatus]) extends Command

}
