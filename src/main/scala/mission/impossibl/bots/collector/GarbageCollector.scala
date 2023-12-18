package mission.impossibl.bots.collector

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, Behavior}
import mission.impossibl.bots.orchestrator.GarbageOrchestrator.GarbageCollectionProposal
import mission.impossibl.bots.orchestrator.{AuctionOffer, GarbageOrchestrator}
import mission.impossibl.bots.source.WasteSource
import mission.impossibl.bots.source.WasteSource.GarbageCollectionInfo

import java.util.UUID
import scala.concurrent.duration._

object GarbageCollector {
  def apply(instance: Instance, initialLocation: (Int, Int)): Behavior[Command] = {
    collector(instance, State(initialLocation))
  }

  //todo on startup init travel (collector schedules clock tics and moves with set speed towards the
  // first source on list, provided that said list is nonempty)
  private def collector(instance: Instance, state: State): Behavior[Command] =
    Behaviors.receive {
      (context, message) => {
        message match {
          case AttachOrchestrator(orchestratorId, orchestratorRef) =>
            context.log.info("Collector{} attached to Orchestrator{}", instance.id, orchestratorId)
            orchestratorRef ! GarbageOrchestrator.GarbageCollectorRegistered(context.self)
            collector(instance.copy(orchestrator = orchestratorRef), state)

          case GarbageCollectionCallForProposal(auctionId, sourceId, sourceLocation, garbageAmount) =>
            context.log.info("Received Garbage Collection CFP for source {} and amount {}", sourceId, garbageAmount)
            if (state.carriedGarbage + garbageAmount + state.ongoingAuctions.values.map(_.amount).sum < instance.capacity) {
              //TODO add logic for offer creation
              val auctionOffer = AuctionOffer(context.self)
              instance.orchestrator ! GarbageCollectionProposal(auctionId, auctionOffer)
              collector(instance, state.copy(ongoingAuctions = state.ongoingAuctions.updated(auctionId, Garbage(sourceLocation, garbageAmount))))
            } else {
              Behaviors.same
            }

          case GarbageCollectionAccepted(auctionId, sourceRef) =>
            context.log.info("GC Accepted for auction id {}", auctionId)
            val garbage = state.ongoingAuctions.get(auctionId)
            if (garbage.isEmpty) {
              context.log.info("Won action {} I don't rember of :)", auctionId)
              return Behaviors.same
            }
            val newNode = GarbagePathElem(garbage.get.location, garbage.get.amount, sourceRef)
            val updatedPath = state.futureSources.appended(newNode)
            val updatedAuctions = state.ongoingAuctions.removed(auctionId)
            newNode.ref ! GarbageCollectionInfo(instance.id, 10.seconds)
            collector(instance, state.copy(ongoingAuctions = updatedAuctions, futureSources = updatedPath))

          case GarbageCollectionRejected(auctionId) =>
            context.log.info("GC Rejected for auction id {}", auctionId)
            val garbage = state.ongoingAuctions.get(auctionId)
            if (garbage.isEmpty) {
              context.log.info("Lost auction {} I don't rember of :)", auctionId)
              return Behaviors.same
            }
            collector(instance, state.copy(ongoingAuctions = state.ongoingAuctions.removed(auctionId)))

          case CollectGarbage(amount) =>
            val updatedGarbageState = state.carriedGarbage + amount
            if (updatedGarbageState >= 0.95 * instance.capacity) {
              //init disposal auction
            }
            val updatedPath = state.visitedSources.appended(state.futureSources.head.copy(amount = amount))
            collector(instance, state.copy(visitedSources = updatedPath, futureSources = state.futureSources.drop(1), carriedGarbage = updatedGarbageState))

          case Move(movement) =>
            if (state.futureSources.isEmpty) {
              return Behaviors.same
            }
            val newLocation = this.calculate_move_location(state.futureSources.head.location, state.currentLocation, movement)
            context.log.info("Collector{} moved from {},{} to {},{}",
              instance.id, state.currentLocation._1, state.currentLocation._2, newLocation._1, newLocation._2)
            collector(instance, state.copy(currentLocation = newLocation))
        }
      }
    }
  private def calculate_move_location(destination: (Int, Int), location: (Int, Int), movement: Int): (Int, Int) = {
    val x = math.abs(destination._1 - location._1) - movement
    if (x < 0) {
      val y = math.abs(destination._2 - location._2) - math.abs(x)
      return (x, y)
    }
    else {
      return (x, location._2)
    }
  }
  sealed trait Command


  final case class GarbageCollectionCallForProposal(auctionId: UUID, sourceId: Int, sourceLocation: (Int, Int), garbageAmount: Int) extends Command

  final case class AttachOrchestrator(orchestratorId: Int, orchestratorRef: ActorRef[GarbageOrchestrator.Command]) extends Command

  final case class GarbageCollectionAccepted(auctionId: UUID, sourceRef: ActorRef[WasteSource.Command]) extends Command

  final case class GarbageCollectionRejected(auctionId: UUID) extends Command

  final case class CollectGarbage(amount: Int) extends Command

  final case class Move(movement: Int) extends Command
}
