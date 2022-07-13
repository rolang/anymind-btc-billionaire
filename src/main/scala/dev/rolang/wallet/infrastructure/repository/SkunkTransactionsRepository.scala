package dev.rolang.wallet.infrastructure.repository

import java.time.{Instant, OffsetDateTime, ZoneOffset}

import dev.rolang.wallet.domain.TransactionsRepository.fillHourlySnapshotGapsPipe
import dev.rolang.wallet.domain.*
import dev.rolang.wallet.infrastructure.db.DbSessionPool
import skunk.*
import skunk.codec.all.*
import skunk.implicits.*

import zio.interop.catz.*
import zio.{RIO, Scope, Task, ZIO, ZLayer}

class SkunkTransactionsRepository(pool: RIO[Scope, Session[Task]]) extends TransactionsRepository[Task] {
  override def addEvent(event: TransactionEvent): Task[Unit] = {
    val command: Command[TransactionEvent] =
      sql"""INSERT INTO wallet_transactions (id, datetime, amount)
           VALUES ($uuid, $timestamptz, $int8) ON CONFLICT (id) DO NOTHING""".command.contramap {
        case TransactionEvent(id, t, a) =>
          id ~ OffsetDateTime.ofInstant(t, ZoneOffset.UTC) ~ a
      }

    ZIO.scoped(pool.flatMap(_.prepare(command).use(_.execute(event)).unit))
  }

  override def listHourlyBalanceSnapshots(range: DateTimeRange): Task[List[BalanceSnapshot]] = {
    val query: Query[(OffsetDateTime, OffsetDateTime), BalanceSnapshot] =
      sql"""SELECT datetime, amount
            FROM hourly_balance_snapshots_complete
            WHERE datetime >= $timestamptz AND datetime <= $timestamptz"""
        .query(timestamptz ~ int8)
        .map { case dateTime ~ balance =>
          BalanceSnapshot(dateTime.toInstant, Satoshi.fromDecimal(balance))
        }

    ZIO.scoped(
      pool.flatMap(_.prepare(query).use {
        _.stream((range.from, range.to), 64).compile.toList
      })
    )
  }

  override def refreshBalanceView: Task[Unit] = {
    val refreshCmd: Command[((OffsetDateTime, Long), Long)] =
      sql"""INSERT INTO hourly_balance_snapshots_complete VALUES ($timestamptz, $int8)
            ON CONFLICT (datetime) DO UPDATE SET amount = $int8""".command

    ZIO.scoped {
      for {
        session   <- pool
        snapshots <- allHourlyBalanceSnapshots
        _         <- ZIO.foreachDiscard(snapshots) { snapshot =>
                       session
                         .prepare(refreshCmd)
                         .use(
                           _.execute(((toOffsetDateTime(snapshot.datetime), snapshot.balance.value), snapshot.balance.value))
                         )
                     }
      } yield ()
    }
  }

  private def allHourlyBalanceSnapshots: Task[List[BalanceSnapshot]] = {
    val query: Query[Void, BalanceSnapshot] =
      sql"""SELECT by_hour, balance FROM hourly_balance_snapshots"""
        .query(timestamptz ~ numeric)
        .map { case dateTime ~ balance =>
          BalanceSnapshot(dateTime.toInstant, Satoshi.fromDecimal(balance))
        }

    ZIO.scoped(
      pool.flatMap(_.prepare(query).use {
        _.stream(Void, 64)
          .through(fillHourlySnapshotGapsPipe)
          .compile
          .toList
      })
    )
  }

  private def toOffsetDateTime(i: Instant) = OffsetDateTime.ofInstant(i, ZoneOffset.UTC)
}

object SkunkTransactionsRepository {
  val layer: ZLayer[DbSessionPool.Pool, Throwable, TransactionsRepository[Task]] = ZLayer {
    for {
      pool <- ZIO.service[RIO[Scope, Session[Task]]]
    } yield new SkunkTransactionsRepository(pool)
  }
}
