package mission.impossibl.bots.collector

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, Behavior}
import mission.impossibl.bots.orchestrator.GarbageOrchestrator.{GarbageCollectionProposal, GarbageDisposalRequest}
import mission.impossibl.bots.orchestrator.{CollectionAuctionOffer, GarbageOrchestrator}
import mission.impossibl.bots.source.WasteSource
import mission.impossibl.bots.source.WasteSource.{DisposeGarbage, GarbageCollectionInfo}

import java.util.UUID
import scala.concurrent.duration._

object GarbageCollector {
  private val DisposalPercentFull       = 0.95
  private val DisposalAuctionTimeoutVal = 10.seconds

  def apply(instance: Instance, initialLocation: (Int, Int)): Behavior[Command] =
    collector(instance, State(initialLocation))

  private def collector(instance: Instance, state: State): Behavior[Command] =
    Behaviors.receive { (context, message) =>
      message match {
        case AttachOrchestrator(orchestratorId, orchestratorRef) =>
          context.log.info("Collector {} attached to Orchestrator {}", instance.id, orchestratorId)
          orchestratorRef ! GarbageOrchestrator.GarbageCollectorRegistered(context.self)
          collector(instance.copy(orchestrator = orchestratorRef), state)
        case GarbageCollectionCallForProposal(auctionId, sourceId, sourceLocation, garbageAmount) =>
          context.log.info("Received Garbage Collection CFP for source {} and amount {}", sourceId, garbageAmount)
          if (instance.capacity - garbageAmount - state.reservedSpace > 0) {
            val distanceToSource = routeLen(state.currentLocation, state.futureSources, sourceLocation)
            val auctionOffer     = CollectionAuctionOffer(context.self, when = math.ceil(distanceToSource / instance.speed).seconds)
            instance.orchestrator ! GarbageCollectionProposal(auctionId, auctionOffer)
            collector(
              instance,
              state.copy(
                ongoingCollectionAuctions = state.ongoingCollectionAuctions.updated(auctionId, Garbage(sourceLocation, garbageAmount)),
                reservedSpace = state.reservedSpace + garbageAmount
              )
            )
          } else {
            Behaviors.same
          }

        case GarbageCollectionAccepted(auctionId, sourceRef) =>
          context.log.info("GC Accepted for auction id {}", auctionId)
          val garbage = state.ongoingCollectionAuctions.get(auctionId)
          if (garbage.isEmpty) {
            context.log.info("Won action {} I don't rember of :)", auctionId)
            return Behaviors.same
          }
          val newNode          = GarbagePathElem(garbage.get.location, garbage.get.amount, sourceRef)
          val updatedPath      = state.futureSources.appended(newNode)
          val updatedAuctions  = state.ongoingCollectionAuctions.removed(auctionId)
          val distanceToSource = routeLen(state.currentLocation, state.futureSources, garbage.get.location)
          val ticksNeeded      = math.ceil(distanceToSource / instance.speed)
          newNode.ref ! GarbageCollectionInfo(instance.id, ticksNeeded.seconds)
          context.log.info("Will be at source location in {} seconds", ticksNeeded)
          collector(instance, state.copy(ongoingCollectionAuctions = updatedAuctions, futureSources = updatedPath))

        case GarbageCollectionRejected(auctionId) =>
          context.log.info("GC Rejected for auction id {}", auctionId)
          state.ongoingCollectionAuctions.get(auctionId) match {
            case Some(Garbage(_, amount)) =>
              collector(instance, state.copy(ongoingCollectionAuctions = state.ongoingCollectionAuctions.removed(auctionId), reservedSpace = state.reservedSpace - amount))
            case None =>
              context.log.info("Lost auction {} I don't rember of :)", auctionId)
              return Behaviors.same
          }

        case CollectGarbage(amount) =>
          val updatedGarbageState = state.carriedGarbage + amount
          var updatedState        = state
          if (updatedGarbageState >= DisposalPercentFull * instance.capacity && state.disposalAuctionTimeout.isEmpty) {
            context.log.info("Initing disposal auction")
            instance.orchestrator ! GarbageDisposalRequest(instance.id, state.carriedGarbage)
            val timeout = context.scheduleOnce(DisposalAuctionTimeoutVal, context.self, DisposalAuctionTimeout())
            updatedState = updatedState.copy(disposalAuctionTimeout = Some(timeout))
          }
          val updatedPath = state.visitedSources.appended(state.futureSources.head.copy(amount = amount))
          collector(instance, updatedState.copy(visitedSources = updatedPath, futureSources = state.futureSources.drop(1), carriedGarbage = updatedGarbageState))

        case Move() =>
          context.log.info("Collector{} moving towards destination", instance.id)
          state.disposalPoint match {
            case Some(DisposalPoint(destination, _)) =>
              val loc = move(destination, state.currentLocation, instance.speed)
              if (loc == destination) {
                // TODO: exchange garbage maybe via ask pattern
              }
              collector(instance, state.copy(currentLocation = loc, disposalPoint = None))
            case None =>
              state.futureSources.headOption match {
                case Some(nextSource) =>
                  val dest = nextSource.location
                  val loc  = move(dest, state.currentLocation, instance.speed)
                  if (loc == dest) {
                    nextSource.ref ! DisposeGarbage(instance.capacity - state.carriedGarbage, context.self)
                  }
                  collector(instance, state.copy(currentLocation = loc))
                case None => Behaviors.same
              }
          }

        case DisposalAuctionTimeout() =>
          context.log.info("Auction Timeout Reached, re-initing timeout")
          instance.orchestrator ! GarbageDisposalRequest(instance.id, state.carriedGarbage)
          val timeout      = context.scheduleOnce(DisposalAuctionTimeoutVal, context.self, DisposalAuctionTimeout())
          val updatedState = state.copy(disposalAuctionTimeout = Some(timeout))
          collector(instance, updatedState)

        case DisposalAuctionResponse(disposalPoint) =>
          state.disposalAuctionTimeout match {
            case Some(timeout) =>
              timeout.cancel()
              // TODO: consider dropping future queue
              val updatedState = state.copy(disposalAuctionTimeout = None, disposalPoint = Some(disposalPoint))
              collector(instance, updatedState)
            case None => Behaviors.same // action has already timed out and was repeated
          }
      }
    }

  private def move(destination: (Int, Int), location: (Int, Int), movement: Int): (Int, Int) = {
    val x = math.abs(destination._1 - location._1) - movement
    if (x < 0) {
      (x, math.abs(destination._2 - location._2) - math.abs(x))
    } else {
      (x, location._2)
    }
  }

  private def routeLen(location: (Int, Int), waypoints: List[GarbagePathElem], destination: (Int, Int)): Int = {
    val (updatedLoc, updatedDistance) = waypoints.foldLeft((location, 0)) { (runningInfo, waypoint) =>
      val (currLoc, distance) = runningInfo
      (waypoint.location, distance + dist(currLoc, waypoint.location))
    }
    updatedDistance + dist(updatedLoc, destination)
  }

  private def dist(destination: (Int, Int), location: (Int, Int)): Int =
    math.abs(destination._1 - location._1) + math.abs(destination._2 - location._2)

  sealed trait Command

  final case class GarbageCollectionCallForProposal(auctionId: UUID, sourceId: Int, sourceLocation: (Int, Int), garbageAmount: Int) extends Command

  final case class GarbageCollectionAccepted(auctionId: UUID, sourceRef: ActorRef[WasteSource.Command]) extends Command

  final case class GarbageCollectionRejected(auctionId: UUID) extends Command

  final case class CollectGarbage(amount: Int) extends Command

  // Technical
  final case class AttachOrchestrator(orchestratorId: Int, orchestratorRef: ActorRef[GarbageOrchestrator.Command]) extends Command

  final case class Move() extends Command

  // Disposal
  final case class DisposalAuctionTimeout() extends Command

  final case class DisposalAuctionResponse(disposalPoint: DisposalPoint) extends Command
}
