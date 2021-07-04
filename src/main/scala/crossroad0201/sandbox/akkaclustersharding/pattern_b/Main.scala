package crossroad0201.sandbox.akkaclustersharding.pattern_b

import java.util.UUID

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ ActorRef, ActorSystem, Scheduler }
import akka.cluster.sharding.typed.scaladsl.ClusterSharding
import akka.management.cluster.bootstrap.ClusterBootstrap
import akka.management.scaladsl.AkkaManagement
import akka.util.Timeout
import com.typesafe.config.{ Config, ConfigFactory, ConfigRenderOptions }
import crossroad0201.sandbox.akkaclustersharding.EntityId
import org.slf4j.LoggerFactory

import scala.concurrent.{ Await, ExecutionContext }

object Main extends App {
  import scala.concurrent.duration._

  private final val logger = LoggerFactory.getLogger(getClass)

  val config: Config = ConfigFactory.load()
  logger.info(s"Loaded configurations... ${config.root().render(ConfigRenderOptions.concise())}")

  val actorSystem: ActorSystem[TodoActor.Protocol.Command] = ActorSystem(
    Behaviors.setup[TodoActor.Protocol.Command] { ctx =>
      AkkaManagement(ctx.system).start()
      ClusterBootstrap(ctx.system).start()
      val clusterSharding: ClusterSharding = ClusterSharding(actorSystem)

      val todoActorProxy: ActorRef[TodoActor.Protocol.Command] = {
        ShardedTotoActor.initClusterSharding(
          actorSystem,
          clusterSharding,
          TodoActorBroker.apply(
            entityId => TodoActor(entityId),
            entityId => entityId
            ),
          "todoBroker",
          None
        )
        ctx.spawn(ShardedTotoActor.ofProxy(clusterSharding), "todoProxy")
      }

      // TODO このアクターがボトルネックになりそう
      Behaviors.receiveMessage { command =>
          todoActorProxy ! command
          Behaviors.same
      }
    },
    "AkkaClusterShardingSandbox",
    config
  )


  logger.info("Waiting for startup to complete...")
  Thread.sleep(10 * 1000)

  implicit val askTimeout: Timeout = Timeout(30.seconds)
  implicit val scheduler: Scheduler = actorSystem.scheduler
  implicit val ec: ExecutionContext = actorSystem.executionContext

  def generateRandomEntityId(): EntityId = UUID.randomUUID().toString.replaceAll("-", "")

  {
    import akka.actor.typed.scaladsl.AskPattern._

    logger.info("Creating a Todo via Sharded Actor...")
    val entityId = generateRandomEntityId()
    Await.ready(
      actorSystem.ask[TodoActor.Protocol.CreateTodoReply] { replyTo =>
        TodoActor.Protocol.CreateTodo(entityId, s"Test Todo $entityId", replyTo)
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
