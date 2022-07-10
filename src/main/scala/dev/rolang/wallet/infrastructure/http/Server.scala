package dev.rolang.wallet.infrastructure.http

import cats.syntax.all.*
import dev.rolang.wallet.config.HttpConfig
import dev.rolang.wallet.infrastructure.http.wallet.v1.WalletRoutes
import org.http4s.blaze.server.BlazeServerBuilder
import org.http4s.server.Server as Http4sServer

import zio.interop.catz.*
import zio.{RIO, Scope, ZIO}

object Server {
  type ServerEnv = WalletRoutes.Env & HttpConfig

  val http4sServe: ZIO[ServerEnv & Scope, Throwable, Http4sServer] =
    for {
      conf <- ZIO.service[HttpConfig]
      ec   <- ZIO.executor.map(_.asExecutionContext)
      e    <-
        BlazeServerBuilder[RIO[WalletRoutes.Env, *]]
          .withExecutionContext(ec)
          .bindHttp(conf.port, "0.0.0.0")
          .withHttpApp(
            (RoutesInterpreter.ttp4sAppRoutes <+> RoutesInterpreter.http4sOpenApiRoutes).orNotFound
          )
          .resource
          .toScopedZIO
    } yield e
}
