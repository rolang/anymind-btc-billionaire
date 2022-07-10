package dev.rolang.wallet.infrastructure.http

import dev.rolang.wallet.infrastructure.http.health.HealthRoutes
import org.http4s.HttpRoutes
import sttp.capabilities.zio.ZioStreams
import sttp.tapir.server.http4s.ztapir.ZHttp4sServerInterpreter
import sttp.tapir.swagger.bundle.SwaggerInterpreter
import sttp.tapir.ztapir.*

import zio.RIO

object RoutesInterpreter {

  type Env = wallet.v1.WalletRoutes.Env

  private val endpoints: List[ZServerEndpoint[Env, ZioStreams]] =
    HealthRoutes.all.map(_.widen[Env]) ++ wallet.v1.WalletRoutes.all

  val http4sOpenApiRoutes: HttpRoutes[RIO[Env, *]] =
    ZHttp4sServerInterpreter()
      .from(
        SwaggerInterpreter().fromServerEndpoints(endpoints, "Wallet API", "1.0")
      )
      .toRoutes

  val ttp4sAppRoutes: HttpRoutes[RIO[Env, *]] =
    ZHttp4sServerInterpreter().from(endpoints).toRoutes
}
