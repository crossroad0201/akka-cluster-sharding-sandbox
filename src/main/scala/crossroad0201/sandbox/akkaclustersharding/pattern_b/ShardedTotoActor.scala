package crossroad0201.sandbox.akkaclustersharding.pattern_b

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ ActorSystem, Behavior }
import akka.cluster.sharding.typed.scaladsl.{ ClusterSharding, Entity, EntityContext, EntityTypeKey }
import akka.cluster.sharding.typed.{ ClusterShardingSettings, ShardingEnvelope, ShardingMessageExtractor }

object ShardedTotoActor {
  import TodoActor.Protocol._

  private final val TypeKey: EntityTypeKey[Command] = EntityTypeKey[Command]("Todo")

  def initClusterSharding(
    system: ActorSystem[_],
    clusterSharding: ClusterSharding,
    commandBehavior: Behavior[Command],
    commandActorName: String
  ): Unit = {
    val numberOfShards = {
      val settings = ClusterShardingSettings(system)
      settings.numberOfShards
    }
    val entity = Entity(TypeKey)(behavior(commandBehavior, commandActorName))
      .withMessageExtractor(new MessageExtractor(numberOfShards))
    clusterSharding.init(entity)
  }

  private def behavior(
    commandBehavior: Behavior[Command],
    commandActorName: String
  ): EntityContext[Command] => Behavior[Command] = { entityContext =>
    Behaviors.setup[Command] { ctx =>
      Behaviors.receiveMessage { command =>
        val actorRef = ctx.spawn(commandBehavior, commandActorName)
        actorRef ! command
        Behaviors.same
      }
    }
  }

  def ofProxy(clusterSharding: ClusterSharding): Behaviors.Receive[Command] = {
    Behaviors.receiveMessagePartial[Command] { command =>
      val entityRef = clusterSharding.entityRefFor[Command](TypeKey, command.entityId)
      entityRef ! command
      Behaviors.same
    }
  }

  private class MessageExtractor(numberOfShards: Int) extends ShardingMessageExtractor[ShardingEnvelope[Command], Command] {
    override def unwrapMessage(message: ShardingEnvelope[Command]): Command = message.message
    override def entityId(message: ShardingEnvelope[Command]): String = message.message.entityId
    override def shardId(entityId: String): String = (math.abs(entityId.reverse.hashCode) % numberOfShards).toString
  }

}
