package dev.rolang.wallet.infrastructure.repository

import java.time.{OffsetDateTime, ZoneOffset}

import dev.rolang.wallet.domain.TransactionsRepository.fillHourlySnapshotGapsPipe
import dev.rolang.wallet.domain.*
import skunk.*
import skunk.codec.all.*
import skunk.implicits.*

import zio.interop.catz.*
import zio.{RIO, Scope, Task, ZIO, ZLayer}

class SkunkTransactionsRepository(s: Session[Task]) extends TransactionsRepository[Task] {
  override def addEvent(event: TransactionEvent): Task[Unit] = {
    val command: Command[TransactionEvent] =
      sql"""INSERT INTO wallet_transactions (id, datetime, amount)
           VALUES ($uuid, $timestamptz, $int8) ON CONFLICT (id) DO NOTHING""".command.contramap {
        case TransactionEvent(id, t, a) =>
          id ~ OffsetDateTime.ofInstant(t, ZoneOffset.UTC) ~ a
      }
    s.prepare(command).use(_.execute(event)).unit
  }

  override def listHourlyBalanceSnapshots(range: DateTimeRange): Task[List[BalanceSnapshot]] = {
    val query: Query[Void, BalanceSnapshot] =
      sql"""SELECT by_hour, balance FROM hourly_balance_snapshots"""
        .query(timestamptz ~ numeric)
        .map { case dateTime ~ balance =>
          BalanceSnapshot(dateTime.toInstant, Satoshi.fromDecimal(balance))
        }

    s.prepare(query).use {
      _.stream(Void, 64)
        .through(fillHourlySnapshotGapsPipe)
        .dropWhile(_.datetime.compareTo(range.from.toInstant) < 0)
        .takeWhile(_.datetime.compareTo(range.to.toInstant) <= 0)
        .compile
        .toList
    }
  }
}

object SkunkTransactionsRepository {
  val layer: ZLayer[RIO[Scope, Session[Task]], Throwable, TransactionsRepository[Task]] = ZLayer {
    for {
      s  <- ZIO.service[RIO[Scope, Session[Task]]]
      ss <- ZIO.scoped(s)
    } yield new SkunkTransactionsRepository(ss)
  }
}
