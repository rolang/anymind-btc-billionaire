package dev.rolang.wallet.actors

import akka.actor.typed.{ActorRef, Behavior}
import akka.persistence.typed.PersistenceId
import akka.persistence.typed.scaladsl.{Effect, EventSourcedBehavior}

import java.time.{Instant, OffsetDateTime}
import java.util.UUID

object Wallet {

  sealed trait Command
  object Command {
    final case class AddSatoshi(datetime: OffsetDateTime, amount: Long, replyTo: ActorRef[Response]) extends Command
  }

  // events = to persist to Cassandra
  trait Event
  final case class SatoshiAdded(transactionId: UUID, dateTime: Instant, amount: Long) extends Event

  // state
  final case class SatoshiBalance(balance: Long)

  // responses
  sealed trait Response
  object Response {
    final case class SatoshiAddedResponse(transactionId: UUID) extends Response
  }

  import Command._
  import Response._

  val commandHandler: (SatoshiBalance, Command) => Effect[Event, SatoshiBalance] = (_, command) =>
    command match {
      case AddSatoshi(datetime, amount, wallet) =>
        val id = UUID.randomUUID()
        Effect
          .persist(SatoshiAdded(id, dateTime = datetime.toInstant, amount = amount))
          .thenReply(wallet)(_ => SatoshiAddedResponse(id))
    }

  val eventHandler: (SatoshiBalance, Event) => SatoshiBalance = (state, event) =>
    event match {
      case SatoshiAdded(_, _, amount) => state.copy(balance = state.balance + amount)
    }

  def apply(id: String): Behavior[Command] =
    EventSourcedBehavior[Command, Event, SatoshiBalance](
      persistenceId = PersistenceId.ofUniqueId(id),
      emptyState = SatoshiBalance(0L),
      commandHandler = commandHandler,
      eventHandler = eventHandler
    )
}
