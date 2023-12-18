package mission.impossibl.bots.collector

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, Behavior}
import mission.impossibl.bots.orchestrator.GarbageOrchestrator.GarbageCollectionProposal
import mission.impossibl.bots.orchestrator.{CollectionAuctionOffer, GarbageOrchestrator}
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
              val auctionOffer = CollectionAuctionOffer(context.self)
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

          case Move() =>
            context.log.info("Collector{} moving towards destination", instance.id)
            Behaviors.same
        }
      }
    }

  sealed trait Command


  final case class GarbageCollectionCallForProposal(auctionId: UUID, sourceId: Int, sourceLocation: (Int, Int), garbageAmount: Int) extends Command

  final case class AttachOrchestrator(orchestratorId: Int, orchestratorRef: ActorRef[GarbageOrchestrator.Command]) extends Command

  final case class GarbageCollectionAccepted(auctionId: UUID, sourceRef: ActorRef[WasteSource.Command]) extends Command

  final case class GarbageCollectionRejected(auctionId: UUID) extends Command

  final case class CollectGarbage(amount: Int) extends Command

  final case class Move() extends Command
}
