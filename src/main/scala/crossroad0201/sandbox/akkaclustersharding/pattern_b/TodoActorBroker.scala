package crossroad0201.sandbox.akkaclustersharding.pattern_b

import akka.actor.typed.{ ActorRef, Behavior }
import akka.actor.typed.scaladsl.Behaviors
import crossroad0201.sandbox.akkaclustersharding.EntityId

object TodoActorBroker {
  import TodoActor.Protocol._

  def apply(
    childBehavior: EntityId => Behavior[Command],
    childNameResolver: EntityId => String
  ): Behavior[Command] =
    Behaviors.setup[Command] { ctx =>
      def getOrCreateChild(entityId: EntityId): ActorRef[Command] = {
        val name = childNameResolver(entityId)
        ctx.child(name) match {
          case None =>
            val actorRef = ctx.spawn(childBehavior(entityId), name)
            actorRef
          case Some(childRef) =>
            childRef.asInstanceOf[ActorRef[Command]]
        }
      }

      Behaviors.receiveMessagePartial[Command] { command =>
        getOrCreateChild(command.entityId) ! command
        Behaviors.same
      }
    }

}
