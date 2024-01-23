package mission.impossibl.bots.source

import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
import akka.actor.typed.{ActorRef, Behavior}
import mission.impossibl.bots.collector.GarbageCollector.CollectGarbage
import mission.impossibl.bots.http.SourceStatus
import mission.impossibl.bots.orchestrator.GarbageOrchestrator

import java.util.UUID
import scala.concurrent.duration._

object WasteSource {
  private val DisposalPercentFull    = 0.7
  private val DisposalAuctionTimeout = 3.seconds
  private val LatenessTolerance      = 20.seconds

  def apply(instance: Instance): Behavior[Command] =
    source(instance, State())

  private def source(instance: Instance, state: State): Behavior[Command] =
    Behaviors.receive { (context, message) =>
      message match {
        case AttachOrchestrator(orchestratorId, orchestratorRef) =>
          context.log.info("Waste Source{} attached to Orchestrator{}", instance.id, orchestratorId)
          orchestratorRef ! GarbageOrchestrator.WasteSourceRegistered(context.self, instance.id)
          source(instance.copy(orchestrator = orchestratorRef), state)

        case ProduceGarbage(amount) => // simulate garbage production
          context.log.info(
            s"New garbage in town! {}, current amount: {}/{}",
            amount,
            state.garbage + amount,
            instance.capacity
          )
          val updatedState = state.copy(state.garbage + amount)
          source(instance, checkGarbageLevel(updatedState, instance, context))

        case AuctionTimeout() =>
          context.log.info("Timed out but no auction result")
          source(instance, checkGarbageLevel(state.copy(auctionTimeout = None), instance, context))

        case DisposeGarbage(maxAmount, collectorRef) =>
          state.collectionTimeout.map(_.cancel())
          val garbageToCollect = maxAmount.min(state.garbage)
          collectorRef ! CollectGarbage(garbageToCollect)
          context.log.info("Passing {} garbage", garbageToCollect)
          source(instance, state.copy(garbage = state.garbage - garbageToCollect, collectionTimeout = None))

        case GarbageCollectionInfo(collectorId, estimatedArrival) =>
          context.log.info("Collector {} will arrive in {}", collectorId, estimatedArrival)
          state.auctionTimeout.map(_.cancel())
          val collectionTimeout = state.collectionTimeout.getOrElse(context.scheduleOnce(estimatedArrival + LatenessTolerance, context.self, CollectionTimeout()))
          source(instance, state.copy(collectionTimeout = Some(collectionTimeout), estimatedCollectorArrival = Some(estimatedArrival), auctionTimeout = None))

        case CollectionTimeout() =>
          context.log.warn("Collection Timeout")
          source(instance, checkGarbageLevel(state.copy(collectionTimeout = None), instance, context))

        case GarbageScoreSummary(garbage_score) =>
          context.log.info("Waste Source got its Score")
          source(instance, state.copy(score = garbage_score))
        case Status(replyTo) =>
          replyTo ! SourceStatus(instance.id, instance.capacity, instance.location, state.garbage, state.score, state.collectionTimeout.isDefined, state.auctionTimeout.isDefined)
          Behaviors.same
      }
    }

  private def checkGarbageLevel(state: State, instance: Instance, context: ActorContext[Command]): State =
    if (state.collectionTimeout.isEmpty && state.auctionTimeout.isEmpty && state.garbage > DisposalPercentFull * instance.capacity) {
      instance.orchestrator ! GarbageOrchestrator.GarbageCollectionRequest(
        instance.id,
        instance.location,
        context.self,
        state.garbage
      )
      state.copy(auctionTimeout = Some(context.scheduleOnce(DisposalAuctionTimeout, context.self, AuctionTimeout())))
    } else {
      state
    }

  sealed trait Command

  final case class ProduceGarbage(amount: Int) extends Command

  final case class GarbageCollectionInfo(collectorId: UUID, estimatedArrival: FiniteDuration) extends Command

  final case class DisposeGarbage(maxAmount: Int, collectorRef: ActorRef[CollectGarbage]) extends Command

  final case class AttachOrchestrator(orchestratorId: Int, orchestratorRef: ActorRef[GarbageOrchestrator.Command]) extends Command

  private final case class AuctionTimeout() extends Command

  private final case class CollectionTimeout() extends Command

  final case class GarbageScoreSummary(garbage_score: Int) extends Command

  final case class Status(replyTo: ActorRef[SourceStatus]) extends Command

}
