package dev.rolang.wallet.infrastructure.db

import cats.effect.std.Console as CatsConsole
import dev.rolang.wallet.config.DBConfig
import natchez.Trace.Implicits.noop
import skunk.{SSL, Session, Strategy}

import zio.interop.catz.*
import zio.interop.catz.implicits.*
import zio.{RIO, Scope, Task, ZIO}

object DbSessionPool {
  type Pool = RIO[Scope, Session[Task]]

  val scopedSession: ZIO[DBConfig, Throwable, Pool] = {
    implicit val console: CatsConsole[Task] = CatsConsole.make[Task]

    ZIO.scoped {
      for {
        conf         <- ZIO.service[DBConfig]
        poolResource <- Session
                          .pooled[Task](
                            host = conf.host,
                            port = conf.port,
                            user = conf.user,
                            database = conf.database,
                            password = conf.password,
                            max = conf.maxPoolSize,
                            strategy = Strategy.SearchPath,
                            ssl = if (conf.ssl) SSL.Trusted else SSL.None
                          )
                          .toScopedZIO
        pool          = poolResource.toScopedZIO
      } yield pool
    }
  }

}
