package crossroad0201.sandbox.akkaclustersharding.pattern_a

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ ActorRef, Behavior }
import akka.persistence.typed.PersistenceId
import akka.persistence.typed.scaladsl.{ Effect, EventSourcedBehavior }
import crossroad0201.sandbox.akkaclustersharding.EntityId

object TodoActor {

  object Protocol {
    sealed trait Command

    case class CreateTodo(
      subject: String,
      replyTo: ActorRef[CreateTodoReply]) extends Command
    sealed trait CreateTodoReply
    case object CreateTodoSucceeded extends CreateTodoReply
    case object CreateTodoFailedByAlreadyCreated extends CreateTodoReply

    case class CloseTodo(replyTo: ActorRef[CloseTotoReply]) extends Command
    sealed trait CloseTotoReply
    case object CloseTodoSucceeded extends CloseTotoReply

    case class GetTodo(replyTo: ActorRef[GetTodoReply]) extends Command
    sealed trait GetTodoReply
    case class GetTodoSucceeded(todo: Todo) extends GetTodoReply

    object EntityNotFound extends CloseTotoReply with GetTodoReply
  }

  private sealed trait Event
  private case class TodoCreated(subject: String) extends Event
  private case object TodoClosed extends Event


  private sealed trait State
  private case class Empty(entityId: String) extends State
  private case class Just(entity: Todo) extends State

  import Protocol._

  def apply(entityId: EntityId): Behavior[Command] =
    Behaviors.setup[Command] { _ =>
      EventSourcedBehavior[Command, Event, State](
        PersistenceId.of("Todo", entityId),
        Empty(entityId),
        commandHandler,
        eventHandler
      )
    }

  private val commandHandler: (State, Command) => Effect[Event, State] = {
    case (Empty(entityId), CreateTodo(subject, replyTo)) => createTodo(entityId, subject, replyTo)
    case (Empty(_), CloseTodo(replyTo)) => todoNotFound(replyTo)
    case (Empty(_), GetTodo(replyTo)) => todoNotFound(replyTo)
    case (Just(_), CreateTodo(_, replyTo)) => todoAlreadyCreated(replyTo)
    case (Just(todo), CloseTodo(replyTo)) => closeTodo(todo, replyTo)
    case (Just(todo), GetTodo(replyTo)) => returnTodo(todo, replyTo)
  }

  private def createTodo(entityId: EntityId, subject: String, replyTo: ActorRef[CreateTodoSucceeded.type]): Effect[Event, State] =
    Effect.persist(
      TodoCreated(subject)
    ).thenReply(replyTo) { _ =>
      CreateTodoSucceeded
    }

  private def closeTodo(todo: Todo, replyTo: ActorRef[CloseTotoReply]): Effect[Event, State] =
    Effect.persist(
      TodoClosed
    ).thenReply(replyTo) { _ =>
      CloseTodoSucceeded
    }

  private def returnTodo(todo: Todo, replyTo: ActorRef[GetTodoReply]): Effect[Event, State] =
    Effect.reply(replyTo) {
      GetTodoSucceeded(todo)
    }

  private def todoAlreadyCreated(replyTo: ActorRef[CreateTodoFailedByAlreadyCreated.type]): Effect[Event, State] =
    Effect.reply(replyTo) {
      CreateTodoFailedByAlreadyCreated
    }

  private def todoNotFound(replyTo: ActorRef[EntityNotFound.type]): Effect[Event, State] =
    Effect.reply(replyTo) {
      EntityNotFound
    }

  private val eventHandler: (State, Event) => State = {
    case (Empty(entityId), TodoCreated(subject)) => Just(Todo.create(entityId, subject))
    case (state: Empty, event: TodoClosed.type ) => illegalEventOnState(state, event)
    case (state: Just, event: TodoCreated) => illegalEventOnState(state, event)
    case (Just(todo), TodoClosed) => Just(todo.close())
  }

  private def illegalEventOnState(state: State, event: Event): State =
    throw new IllegalStateException(s"Illegal event $event occurred on state $state")

}

case class Todo(id: EntityId, subject: String, open: Boolean) {
  def close(): Todo = copy(open = false)
}

object Todo {
  def create(id: EntityId, subject: String): Todo = Todo(id, subject, open = true)
}

