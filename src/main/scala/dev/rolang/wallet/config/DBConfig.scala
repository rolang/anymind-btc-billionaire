package dev.rolang.wallet.config

import zio.ZLayer
import zio.config.ConfigDescriptor.*
import zio.config.*

final case class DBConfig(
  user: String,
  password: Option[String],
  host: String,
  port: Int,
  database: String,
  maxPoolSize: Int,
  ssl: Boolean
)

object DBConfig {
  val layer: ZLayer[Any, ReadError[String], DBConfig] = ZConfig
    .fromSystemEnv(
      (string("POSTGRES_USER").default("postgres") zip
        string("POSTGRES_PASSWORD").default("postgres").optional zip
        string("POSTGRES_HOST").default("localhost") zip
        int("POSTGRES_PORT").default(5432) zip
        string("POSTGRES_DATABASE").default("postgres") zip
        int("POSTGRES_MAX_POOL_SIZE").default(80) zip
        boolean("POSTGRES_USE_SSL").default(false)).to[DBConfig]
    )
}
