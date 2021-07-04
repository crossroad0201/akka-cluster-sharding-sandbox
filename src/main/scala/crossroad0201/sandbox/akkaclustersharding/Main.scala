package crossroad0201.sandbox.akkaclustersharding

import java.net.InetAddress
import java.util.UUID

import akka.Done
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ ActorRef, ActorSystem, Scheduler }
import akka.cluster.sharding.typed.ShardingEnvelope
import akka.cluster.sharding.typed.scaladsl.{ ClusterSharding, Entity, EntityTypeKey }
import akka.management.cluster.bootstrap.ClusterBootstrap
import akka.management.scaladsl.AkkaManagement
import akka.util.Timeout
import com.typesafe.config.{ Config, ConfigFactory, ConfigRenderOptions }
import crossroad0201.sandbox.akkaclustersharding.persistence.TodoActor
import org.slf4j.LoggerFactory

import scala.concurrent.{ Await, ExecutionContext }
import scala.concurrent.duration._

object Main extends App {
  private final val logger = LoggerFactory.getLogger(getClass)

  val config: Config = ConfigFactory.load()
  logger.info(s"Loaded configurations... ${config.root().render(ConfigRenderOptions.concise())}")

  val actorSystem: ActorSystem[Done] = ActorSystem(
    Behaviors.setup[Done] { ctx =>
      AkkaManagement(ctx.system).start()
      ClusterBootstrap(ctx.system).start()
      Behaviors.same
    },
    "AkkaClusterShardingSandbox",
    config
  )

  val clusterSharding: ClusterSharding = ClusterSharding(actorSystem)

  val entityTypeKey = EntityTypeKey[TodoActor.Protocol.Command]("Todo")
  val todoShardRegion: ActorRef[ShardingEnvelope[TodoActor.Protocol.Command]] = clusterSharding.init(
    Entity(entityTypeKey) { entityContext =>
      TodoActor(entityContext.entityId)
    }
  )

  logger.info("Waiting for startup to complete...")
  Thread.sleep(10 * 1000)

  implicit val askTimeout: Timeout = Timeout(30.seconds)
  implicit val scheduler: Scheduler = actorSystem.scheduler
  implicit val ec: ExecutionContext = actorSystem.executionContext
  import akka.actor.typed.scaladsl.AskPattern._

  def generateRandomEntityId(): String = UUID.randomUUID().toString.replaceAll("-", "").reverse

  {
    logger.info("Creating a Todo via Shard region...")
    val entityId = generateRandomEntityId()
    Await.ready(
      todoShardRegion.ask[TodoActor.Protocol.CreateTodoReply] { replyTo =>
        ShardingEnvelope(entityId, TodoActor.Protocol.CreateTodo(s"Test Todo $entityId", replyTo))
      }.map {
        case TodoActor.Protocol.CreateTodoSucceeded =>
          logger.info(s"Todo created. $entityId")
        case TodoActor.Protocol.CreateTodoFailedByAlreadyCreated =>
          logger.error(s"Todo was not created. $entityId")
      },
      10.seconds
    )
  }

  {
    logger.info("Creating a Todo via EntityRef...")
    val entityId = generateRandomEntityId()
    Await.ready(
      clusterSharding.entityRefFor[TodoActor.Protocol.Command](entityTypeKey, entityId)
        .ask[TodoActor.Protocol.CreateTodoReply] { replyTo =>
          TodoActor.Protocol.CreateTodo(s"Test Todo $entityId", replyTo)
        }.map {
        case TodoActor.Protocol.CreateTodoSucceeded =>
          logger.info(s"Todo created. $entityId")
        case TodoActor.Protocol.CreateTodoFailedByAlreadyCreated =>
          logger.error(s"Todo was not created. $entityId")
      },
      10.seconds
    )
  }

  logger.info(
    s"""=== Actor Tree =====================================================================
       |${actorSystem.printTree}
       |====================================================================================
       |""".stripMargin
  )

  actorSystem.terminate()

}
