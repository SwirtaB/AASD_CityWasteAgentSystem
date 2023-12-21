package mission.impossibl.bots.sink

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, Behavior}
import mission.impossibl.bots.orchestrator.GarbageOrchestrator.GarbageDisposalProposal
import mission.impossibl.bots.orchestrator.{DisposalAuctionOffer, GarbageOrchestrator}

import java.util.UUID
import scala.concurrent.duration._

object WasteSink {
  val AuctionTimeoutVal: FiniteDuration = 10.seconds
  private val ReservationTimeoutVal     = 1.minute
  def apply(instance: Instance, processing_power: Float): Behavior[Command] =
    sink(instance, State(processing_power))

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

        case ProcessGarbage(massToProcess) => // simulates garbage processing
          // TODO change garbage_packets to list of tuples
          // Change garbage_packet_id to n, get first n packets and process themval garbage_packet    = state.garbagePackets.get(garbage_packet_id)
          // val (toProcess, processed) = process(massToProcess, state.garbagePackets, state.processedGarbage)
          // state.garbagePackets.headOption
          // val processed_garbage = garbage_packet.get.totalMass
          context.log.info(
            "Processed {} kg of garbage."
          )
          sink(instance, state)

        case ReservationTimeout(auctionId) =>
          val reservations = state.reservedSpace.filterNot(_.auctionId == auctionId) // drop reservation from rejected auction
          sink(instance, state.copy(reservedSpace = reservations))
      }

    }

  private def calcEmptySpace(garbagePackets: List[GarbagePacket], reservations: List[Reservation], capacity: Float): Float =
    capacity - reservations.map(_.wasteMass).sum - garbagePackets.map(_.totalMass).sum

  sealed trait Command

  final case class ProcessGarbage(massToProcess: Int) extends Command

  final case class ReceiveGarbage(packet: GarbagePacket, collectorId: UUID) extends Command

  final case class AttachOrchestrator(orchestratorId: Int, orchestratorRef: ActorRef[GarbageOrchestrator.Command]) extends Command

  final case class GarbageDisposalCallForProposal(auctionId: UUID, collectorId: UUID, garbageAmount: Int) extends Command

  final case class GarbageDisposalAccepted(auctionId: UUID) extends Command

  final case class GarbageDisposalRejected(auctionId: UUID) extends Command

  private final case class ReservationTimeout(auctionId: UUID) extends Command

}
