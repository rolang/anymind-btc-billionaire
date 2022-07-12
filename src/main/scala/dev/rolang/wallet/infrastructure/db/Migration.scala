package dev.rolang.wallet.infrastructure.db

import dev.rolang.wallet.config.DBConfig
import skunk.implicits.*

import zio.{RIO, Task, ZIO, ZLayer}

object Migration {

  private def migrate(pool: DbSessionPool.Pool): Task[Unit] = ZIO.scoped {
    for {
      session <- pool
      _       <- session.execute(sql"""
             CREATE TABLE IF NOT EXISTS wallet_transactions(
               id UUID PRIMARY KEY,
               datetime TIMESTAMP WITH TIME ZONE NOT NULL,
               amount BIGINT NOT NULL
             )""".command)
      _       <- session.execute(sql"""
            CREATE INDEX IF NOT EXISTS wallet_transactions_dt_idx ON wallet_transactions(datetime)
             """.command)

      _ <- session.execute(sql"""
             CREATE OR REPLACE VIEW hourly_txn_amount AS (
               SELECT date_trunc('hour', datetime + '1 hour') by_hour,
                      SUM(amount) amount_sum
               FROM wallet_transactions
               GROUP BY by_hour
             )""".command)

      // This could be optimized by using a materialized view which is updated once a hour
      _ <- session.execute(sql"""
             CREATE OR REPLACE VIEW hourly_balance_snapshots AS (
               SELECT by_hour, SUM(amount_sum) OVER (ORDER BY by_hour) balance
               FROM hourly_txn_amount
             )""".command)
    } yield ()
  }

  val runMigration: RIO[DBConfig, Unit] = for {
    config <- ZIO.service[DBConfig]
    pool   <- DbSessionPool.scopedSession.provide(ZLayer.succeed(config))
    _      <- migrate(pool)
  } yield ()

}
