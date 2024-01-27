package mission.impossibl.bots.collector

import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
import akka.actor.typed.{ActorRef, Behavior}
import mission.impossibl.bots.Utils.dist
import mission.impossibl.bots.collector.GarbageCollector.Move
import mission.impossibl.bots.http.{CollectorStatus, SourcePathElem}
import mission.impossibl.bots.orchestrator.GarbageOrchestrator.{GarbageCollectionProposal, GarbageDisposalRequest}
import mission.impossibl.bots.orchestrator.{CollectionAuctionOffer, GarbageOrchestrator}
import mission.impossibl.bots.sink.{GarbagePacket, GarbagePacketRecord}
import mission.impossibl.bots.sink.WasteSink.ReceiveGarbage
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
          if (state.disposalPoint.isDefined || state.disposalAuctionTimeout.isDefined) {
            context.log.info("Disposal point exists or disposal auction in progress, ignoring collection CFP {}.", auctionId)
            Behaviors.same
          } else {
            val emptySpace = instance.capacity - state.ongoingCollectionAuctions.values.map(_.amount).sum - state.futureSources.map(_.amount).sum
            if (emptySpace <= 0) {
              context.log.warn("O free space and disposal not in progress")
            }
            val distanceToSource = routeLen(state.currentLocation, state.futureSources, sourceLocation)
            val estimatedArrival = math.ceil(distanceToSource / instance.speed).seconds
            instance.orchestrator ! GarbageCollectionProposal(auctionId, CollectionAuctionOffer(context.self, estimatedArrival, emptySpace))
            collector(
              instance,
              state.copy(
                ongoingCollectionAuctions = state.ongoingCollectionAuctions.updated(auctionId, Garbage(sourceLocation, garbageAmount))
              )
            )
          }
        case GarbageCollectionAccepted(auctionId, sourceId, sourceRef) =>
          context.log.info("GC Accepted for auction id {}", auctionId)
          val garbage = state.ongoingCollectionAuctions.get(auctionId)
          if (garbage.isEmpty) {
            context.log.warn("Won action {} I don't remember of :)", auctionId)
            Behaviors.same
          } else {
            val newNode          = GarbagePathElem(garbage.get.location, garbage.get.amount, sourceId, sourceRef)
            val updatedPath      = state.futureSources.appended(newNode)
            val updatedAuctions  = state.ongoingCollectionAuctions.removed(auctionId)
            val distanceToSource = routeLen(state.currentLocation, state.futureSources, garbage.get.location)
            val ticksNeeded      = math.ceil(distanceToSource / instance.speed)
            newNode.ref ! GarbageCollectionInfo(instance.id, ticksNeeded.seconds)
            context.log.info("Will be at source location in {} seconds", ticksNeeded)
            collector(instance, state.copy(ongoingCollectionAuctions = updatedAuctions, futureSources = updatedPath))
          }

        case GarbageCollectionRejected(auctionId) =>
          context.log.info("GC Rejected for auction id {}", auctionId)
          collector(instance, state.copy(ongoingCollectionAuctions = state.ongoingCollectionAuctions.removed(auctionId)))

        case CollectGarbage(amount) =>
          var updatedState = state.copy(carriedGarbage = state.carriedGarbage + amount)
          if (state.disposalAuctionTimeout.isEmpty && updatedState.carriedGarbage >= DisposalPercentFull * instance.capacity) {
            updatedState = initDisposal(instance, updatedState, context)
          }
          val updatedPath = state.visitedSources.appended(state.futureSources.head.copy(amount = amount))
          context.log.info(
            "Collected {} garbage, I now have {}/{} garbage, disposalInProgress ? {}",
            amount,
            updatedState.carriedGarbage,
            instance.capacity,
            updatedState.disposalAuctionTimeout.isDefined
          )
          collector(instance, updatedState.copy(visitedSources = updatedPath, futureSources = state.futureSources.drop(1)))

        case Move() =>
          state.disposalPoint match {
            case Some(DisposalPoint(destination, sink, auctionId)) =>
              val loc = move(destination, state.currentLocation, instance.speed)
              if (loc == destination) {
                context.log.info("At destination - sink at {}", destination)
                val packets = state.visitedSources.map(g => GarbagePacketRecord(g.id, wasteMass = g.amount))
                sink ! ReceiveGarbage(GarbagePacket(packets, packets.map(_.wasteMass).sum), instance.id, auctionId)
                context.log.info("Emptied")
                collector(instance, state.copy(carriedGarbage = 0, visitedSources = List.empty, disposalPoint = None))
              } else {
                collector(instance, state.copy(currentLocation = loc))
              }
            case None =>
              state.futureSources.headOption match {
                case Some(nextSource) =>
                  val dest = nextSource.location
                  val loc  = move(dest, state.currentLocation, instance.speed)
                  if (loc == dest) {
                    nextSource.ref ! DisposeGarbage(instance.capacity - state.carriedGarbage, context.self)
                  }
                  collector(instance, state.copy(currentLocation = loc))
                case None =>
                  Behaviors.same
              }
          }

        case DisposalAuctionTimeout() =>
          context.log.warn("Disposal Auction Timeout Reached, re-initing")
          val updatedState = initDisposal(instance, state.copy(disposalAuctionTimeout = None), context)
          collector(instance, updatedState)

        case DisposalAuctionResponse(disposalPoint) =>
          state.disposalAuctionTimeout match {
            case Some(timeout) =>
              context.log.info("Got disposal point info - moving to {}", disposalPoint.location)
              timeout.cancel()
              val updatedState = state.copy(disposalAuctionTimeout = None, disposalPoint = Some(disposalPoint))
              collector(instance, updatedState)
            case None => Behaviors.same // action has already timed out and was repeated
          }
        case Status(replyTo) =>
          replyTo ! CollectorStatus(
            instance.id,
            instance.capacity,
            state.currentLocation,
            state.carriedGarbage,
            state.visitedSources.map(s => SourcePathElem(s.location, s.amount, s.id)),
            state.futureSources.map(s => SourcePathElem(s.location, s.amount, s.id)),
            state.ongoingCollectionAuctions,
            state.disposalPoint.map(_.location)
          )
          Behaviors.same
      }
    }

  private def move(destination: (Int, Int), location: (Int, Int), movement: Int): (Int, Int) = {
    val deltaX = math.abs(destination._1 - location._1)
    if (deltaX >= movement) {
      // move only in x space
      if (destination._1 > location._1) {
        (location._1 + movement, location._2)
      } else {
        (location._1 - movement, location._2)
      }
    } else {
      val deltaY  = math.abs(destination._2 - location._2)
      val movLeft = movement - deltaX
      if (deltaY < movLeft) {
        destination
      } else {
        if (location._2 > destination._2) {
          (destination._1, location._2 - movLeft)
        } else {
          (destination._1, location._2 + movLeft)
        }
      }
    }
  }

  private def routeLen(location: (Int, Int), waypoints: List[GarbagePathElem], destination: (Int, Int)): Int = {
    val (updatedLoc, updatedDistance) = waypoints.foldLeft((location, 0)) { (runningInfo, waypoint) =>
      val (currLoc, distance) = runningInfo
      (waypoint.location, distance + dist(currLoc, waypoint.location))
    }
    updatedDistance + dist(updatedLoc, destination)
  }

  private def initDisposal(instance: Instance, state: State, context: ActorContext[Command]): State = {
    context.log.info("Init disposal auction")
    instance.orchestrator ! GarbageDisposalRequest(instance.id, state.carriedGarbage, context.self, state.currentLocation)
    val timeout = context.scheduleOnce(DisposalAuctionTimeoutVal, context.self, DisposalAuctionTimeout())
    state.copy(disposalAuctionTimeout = Some(timeout))
  }

  sealed trait Command

  final case class GarbageCollectionCallForProposal(auctionId: UUID, sourceId: UUID, sourceLocation: (Int, Int), garbageAmount: Int) extends Command

  final case class GarbageCollectionAccepted(auctionId: UUID, sourceId: UUID, sourceRef: ActorRef[WasteSource.Command]) extends Command

  final case class GarbageCollectionRejected(auctionId: UUID) extends Command

  final case class CollectGarbage(amount: Int) extends Command

  // Technical
  final case class AttachOrchestrator(orchestratorId: Int, orchestratorRef: ActorRef[GarbageOrchestrator.Command]) extends Command

  final case class Move() extends Command

  // Disposal
  final case class DisposalAuctionTimeout() extends Command

  final case class DisposalAuctionResponse(disposalPoint: DisposalPoint) extends Command

  final case class Status(replyTo: ActorRef[CollectorStatus]) extends Command

}
