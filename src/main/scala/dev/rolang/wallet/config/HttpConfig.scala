package dev.rolang.wallet.config

import zio.ZLayer
import zio.config.ConfigDescriptor.*
import zio.config.*

final case class HttpConfig(port: Int)

object HttpConfig {
  val layer: ZLayer[Any, ReadError[String], HttpConfig] = ZConfig.fromSystemEnv(
    int("HTTP_PORT").default(8080).to[HttpConfig]
  )
}
