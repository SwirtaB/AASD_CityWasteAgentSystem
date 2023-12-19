package mission.impossibl.bots.source

import akka.actor.Cancellable
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, Behavior}
import mission.impossibl.bots.collector.GarbageCollector.CollectGarbage
import mission.impossibl.bots.orchestrator.GarbageOrchestrator

import scala.concurrent.duration._

object WasteSource {
  private val DisposalPercentFull = 0.7
  private val DisposalAuctionTimeout = 3.seconds
  private val LatenessTolerance = 10.seconds

  def apply(instance: Instance, orchestratorRef: ActorRef[GarbageOrchestrator.Command]): Behavior[Command] = {
    source(instance, State())
  }

  private def source(instance: Instance, state: State): Behavior[Command] =
    Behaviors.receive {
      (context, message) => {
        message match {
          case AttachOrchestrator(orchestratorId, orchestratorRef) =>
            context.log.info("Waste Source{} attached to Orchestrator{}", instance.id, orchestratorId)
            orchestratorRef ! GarbageOrchestrator.WasteSourceRegistered(context.self, instance.id)
            source(instance.copy(orchestrator = orchestratorRef), state)

          case CheckGarbageLevel() =>
            context.log.info("Checking garbage level")
            if (state.collectionTimeout.isEmpty && state.auctionTimeout.isEmpty && state.garbage > DisposalPercentFull * instance.capacity) {
              instance.orchestrator ! GarbageOrchestrator.GarbageCollectionRequest(
                instance.id, instance.location, context.self, state.garbage
              )
              val auctionTimeout = context.scheduleOnce(DisposalAuctionTimeout, context.self, AuctionTimeout())
              source(instance, state.copy(auctionTimeout = Some(auctionTimeout)))
            } else {
              Behaviors.same
            }
          case ProduceGarbage(amount) => // simulate garbage production
            context.log.info(
              s"New garbage in town! {}, current amount: {}",
              amount, state.garbage + amount
            )
            context.self ! CheckGarbageLevel()
            source(instance, state.copy(state.garbage + amount))
          case AuctionTimeout() =>
            context.log.info("Timed out but no auction result")
            context.self ! CheckGarbageLevel()
            source(instance, state.copy(auctionTimeout = None))

          case DisposeGarbage(maxAmount, collectorRef) =>
            val garbageToCollect = maxAmount.max(state.garbage)
            collectorRef ! CollectGarbage(garbageToCollect)
            state.collectionTimeout.map(_.cancel())
            source(instance, state.copy(garbage = state.garbage - garbageToCollect, collectionTimeout = None))

          case GarbageCollectionInfo(collectorId, estimatedArrival) =>
            context.log.info("Collector {} will arrive in {}", collectorId, estimatedArrival)
            state.auctionTimeout.map(_.cancel()) //todo maybe one timeout could suffice for both
            val collectionTimeout = state.collectionTimeout.getOrElse(context.scheduleOnce(estimatedArrival + LatenessTolerance, context.self, CollectionTimeout()))
            source(instance, state.copy(collectionTimeout = Some(collectionTimeout), estimatedCollectorArrival = Some(estimatedArrival), auctionTimeout = None))

          case CollectionTimeout() =>
            context.log.info("Collection Timeout")
            context.self ! CheckGarbageLevel()
            source(instance, state.copy(collectionTimeout = None))

          case GarbageScoreSummary(garbage_score) =>
            context.log.info("Waste Source got its Score")
            source(instance, state.copy(score = garbage_score))
        }
      }
    }

  sealed trait Command

  final case class Instance(id: Int, location: (Int, Int), capacity: Int, orchestrator: ActorRef[GarbageOrchestrator.Command])

  final case class State(garbage: Int = 0, score: Int = 0, estimatedCollectorArrival: Option[FiniteDuration] = None, auctionTimeout: Option[Cancellable] = None, collectionTimeout: Option[Cancellable] = None)

  final case class ProduceGarbage(amount: Int) extends Command

  final case class GarbageCollectionInfo(collectorId: Int, estimatedArrival: FiniteDuration) extends Command

  final case class DisposeGarbage(maxAmount: Int, collectorRef: ActorRef[CollectGarbage]) extends Command

  final case class AttachOrchestrator(orchestratorId: Int, orchestratorRef: ActorRef[GarbageOrchestrator.Command]) extends Command

  private final case class CheckGarbageLevel() extends Command

  private final case class AuctionTimeout() extends Command

  private final case class CollectionTimeout() extends Command
  
  final case class GarbageScoreSummary(garbage_score: Int) extends Command
}
