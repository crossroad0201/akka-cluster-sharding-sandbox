package crossroad0201.sandbox.akkaclustersharding.pattern_b

import akka.actor.typed.{ ActorSystem, Behavior }
import akka.actor.typed.scaladsl.Behaviors
import akka.cluster.sharding.typed.{ ClusterShardingSettings, ShardingEnvelope, ShardingMessageExtractor }
import akka.cluster.sharding.typed.scaladsl.{ ClusterSharding, Entity, EntityContext, EntityTypeKey }

import scala.concurrent.duration.FiniteDuration

object ShardedTotoActor {
  import TodoActor.Protocol._

  private final val TypeKey: EntityTypeKey[Command] = EntityTypeKey[Command]("Todo")

  def initClusterSharding(
    system: ActorSystem[_],
    clusterSharding: ClusterSharding,
    commandBehavior: Behavior[Command],
    commandActorName: String,
    receiveTimeout: Option[FiniteDuration]
  ): Unit = {
    val numberOfShards = {
      val settings = ClusterShardingSettings(system)
      settings.numberOfShards
    }
    val entity = Entity(TypeKey)(behavior(commandBehavior, commandActorName, receiveTimeout))
      .withMessageExtractor(new MessageExtractor(numberOfShards))
      .withStopMessage(Stop)
    clusterSharding.init(entity)
  }

  private def behavior(
    commandBehavior: Behavior[Command],
    commandActorName: String,
    receiveTimeout: Option[FiniteDuration]
  ): EntityContext[Command] => Behavior[Command] = { entityContext =>
    Behaviors.setup[Command] { ctx =>
      receiveTimeout.foreach(ctx.setReceiveTimeout(_, Idle))

      Behaviors.receiveMessagePartial {
        case Idle =>
          entityContext.shard ! ClusterSharding.Passivate(ctx.self)
          Behaviors.same
        case Stop =>
          Behaviors.stopped
        case command =>
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
