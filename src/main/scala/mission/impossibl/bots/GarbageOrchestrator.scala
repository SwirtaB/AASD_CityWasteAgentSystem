package mission.impossibl.bots

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, Behavior}

object GarbageOrchestrator {
  def apply(instance: Instance): Behavior[Command] = {
    orchestrator(instance)
  }

  private def orchestrator(instance: Instance): Behavior[Command] =
    Behaviors.receive {
      (context, message) => {
        message match {
          case GarbageCollectionRequest(sourceId, sourceLocation, _, garbageAmount) =>
            context.log.info("Orchestrator{} received request to collect garbage from Source{}", instance.id, sourceId)
            for (gc <- instance.garbageCollectors) {
              gc ! GarbageCollector.GarbageCollectionCallForProposal(sourceId, sourceLocation, garbageAmount)
            }
            Behaviors.same
          case LateInitialize() =>
            context.log.info("Orchestrator{} late initialize", instance.id)
            for (gc <- instance.garbageCollectors) {
              gc ! GarbageCollector.AttachOrchestrator(instance.id, context.self)
            }
            Behaviors.same
        }
      }
    }

  sealed trait Command

  final case class Instance(id: Int, garbageCollectors: List[ActorRef[GarbageCollector.Command]])

  final case class GarbageCollectionRequest(sourceId: Int, sourceLocation: (Int, Int), sourceRef: ActorRef[WasteSource.Command], garbageAmount: Int) extends Command

  final case class LateInitialize() extends Command
}
