package mission.impossibl.bots.source

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, Behavior}
import mission.impossibl.bots.collector.GarbageCollector.CollectGarbage
import mission.impossibl.bots.orchestrator.GarbageOrchestrator

import java.util.UUID
import scala.concurrent.duration._

object WasteSource {
  private val DisposalPercentFull = 0.7
  private val DisposalAuctionTimeout = 3.seconds
  private val LatenessTolerance = 10.seconds

  def apply(instance: Instance): Behavior[Command] = {
    source(instance, State())
  }

  private def source(instance: Instance, state: State): Behavior[Command] =
    Behaviors.receive {
      (context, message) => {
        message match {
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
            val garbagePassed =state.garbage - garbageToCollect
            context.log.info("Passing {} garbage", garbagePassed)
            source(instance, state.copy(garbage = garbagePassed, collectionTimeout = None))

          case GarbageCollectionInfo(collectorId, estimatedArrival) =>
            context.log.info("Collector {} will arrive in {}", collectorId, estimatedArrival)
            state.auctionTimeout.map(_.cancel()) //todo maybe one timeout could suffice for both
            val collectionTimeout = state.collectionTimeout.getOrElse(context.scheduleOnce(estimatedArrival + LatenessTolerance, context.self, CollectionTimeout()))
            source(instance, state.copy(collectionTimeout = Some(collectionTimeout), estimatedCollectorArrival = Some(estimatedArrival), auctionTimeout = None))

          case CollectionTimeout() =>
            context.log.info("Collection Timeout")
            context.self ! CheckGarbageLevel()
            source(instance, state.copy(collectionTimeout = None))
        }
      }
    }

  sealed trait Command

  final case class ProduceGarbage(amount: Int) extends Command

  final case class GarbageCollectionInfo(collectorId: UUID, estimatedArrival: FiniteDuration) extends Command

  final case class DisposeGarbage(maxAmount: Int, collectorRef: ActorRef[CollectGarbage]) extends Command

  private final case class CheckGarbageLevel() extends Command

  private final case class AuctionTimeout() extends Command

  private final case class CollectionTimeout() extends Command
}
