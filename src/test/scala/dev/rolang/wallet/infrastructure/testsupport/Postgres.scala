package dev.rolang.wallet.infrastructure.testsupport

import com.opentable.db.postgres.embedded.EmbeddedPostgres
import dev.rolang.wallet.config.DBConfig
import org.testcontainers.utility.DockerImageName

import zio.{Scope, ZIO, ZLayer}

object Postgres {

  val embedded: ZLayer[Scope, Throwable, DBConfig] = ZLayer {
    ZIO
      .acquireRelease(
        ZIO.from(EmbeddedPostgres.builder().setImage(DockerImageName.parse("postgres:14-alpine")).start())
      )(pg => ZIO.from(pg.close()).orDie)
      .map { pg =>
        DBConfig(
          user = "postgres",
          password = None,
          host = "localhost",
          port = pg.getPort,
          database = "postgres",
          maxPoolSize = 5,
          ssl = false
        )
      }
  }

}
