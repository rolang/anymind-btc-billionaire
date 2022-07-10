package dev.rolang.wallet.infrastructure.http.health

import dev.rolang.wallet.infrastructure.http.Endpoints
import sttp.capabilities.zio.ZioStreams
import sttp.tapir.ztapir.{RichZEndpoint, ZServerEndpoint, emptyOutput}

import zio.ZIO

object HealthRoutes {
  // TODO: do a proper health analysis
  private val healthEndpoint: ZServerEndpoint[Any, ZioStreams] = Endpoints.baseEndpoint.get
    .in("health")
    .out(emptyOutput)
    .zServerLogic(_ => ZIO.unit)

  val all: List[ZServerEndpoint[Any, ZioStreams]] = List(healthEndpoint)
}
