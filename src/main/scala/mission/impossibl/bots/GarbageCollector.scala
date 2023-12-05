package mission.impossibl.bots

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, Behavior}
import mission.impossibl.bots.orchestrator.{AuctionOffer, GarbageOrchestrator}
import mission.impossibl.bots.orchestrator.GarbageOrchestrator.GarbageCollectionProposal

import java.util.UUID

object GarbageCollector {
  def apply(instance: Instance, initialLocation: (Int, Int)): Behavior[Command] = {
    collector(instance, State(initialLocation))
  }

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
            val nextNode = GarbagePathElem(garbage.get.location, garbage.get.amount, sourceRef)
            val updatedPath = state.futureSources.appended(nextNode)
            val updatedAuctions = state.ongoingAuctions.removed(auctionId)
            //todo here should start informing the source of delivery
            collector(instance, state.copy(ongoingAuctions = updatedAuctions, futureSources = updatedPath))
          case GarbageCollectionRejected(auctionId) =>
            context.log.info("GC Rejected for auction id {}", auctionId)
            val garbage = state.ongoingAuctions.get(auctionId)
            if (garbage.isEmpty) {
              context.log.info("Lost auction {} I don't rember of :)", auctionId)
              return Behaviors.same
            }
            collector(instance, state.copy(ongoingAuctions = state.ongoingAuctions.removed(auctionId)))
        }
      }
    }

  sealed trait Command

  final case class Instance(id: Int, capacity: Int, orchestrator: ActorRef[GarbageOrchestrator.Command])

  final case class State(
                          currentLocation: (Int, Int),
                          visitedSources: List[GarbagePathElem] = List.empty,
                          futureSources: List[GarbagePathElem] = List.empty,
                          carriedGarbage: Int = 0,
                          reservedSpace: Int = 0,
                          ongoingAuctions: Map[UUID, Garbage] = Map.empty
                        )

  final case class Garbage(
                            location: (Int, Int),
                            amount: Int
                          )

  final case class GarbagePathElem(
                                    location: (Int, Int),
                                    amount: Int,
                                    ref: ActorRef[WasteSource.Command]
                                  )


  final case class GarbageCollectionCallForProposal(auctionId: UUID, sourceId: Int, sourceLocation: (Int, Int), garbageAmount: Int) extends Command

  final case class AttachOrchestrator(orchestratorId: Int, orchestratorRef: ActorRef[GarbageOrchestrator.Command]) extends Command

  final case class GarbageCollectionAccepted(auctionId: UUID, sourceRef: ActorRef[WasteSource.Command]) extends Command

  final case class GarbageCollectionRejected(auctionId: UUID) extends Command
}
