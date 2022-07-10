package dev.rolang.wallet.infrastructure.http.wallet.v1

import dev.rolang.wallet.domain.WalletService
import dev.rolang.wallet.infrastructure.http.Endpoints.baseEndpoint
import dev.rolang.wallet.infrastructure.http.{ErrorInfo, ServerError}
import sttp.capabilities.zio.ZioStreams
import sttp.tapir.json.circe.jsonBody
import sttp.tapir.ztapir.*
import sttp.tapir.{EndpointInput, emptyOutput}

import zio.{Task, ZIO}

object WalletRoutes {
  type Env = WalletService[Task]

  val BASE_PATH: EndpointInput[Unit] = "wallet" / "v1"

  def topUpServerLogic(t: BtcTopUp): ZIO[Env, ErrorInfo, Unit] = (for {
    r <- ZIO.service[WalletService[Task]]
    _ <- r.topUp(t.toDomain)
  } yield ()).mapError(r => ServerError(s"Top up failed: ${r.getMessage}"))

  def hourlyBalanceLogic(t: DateTimeRange): ZIO[Env, ErrorInfo, List[BalanceSnapshot]] = (for {
    r    <- ZIO.service[WalletService[Task]]
    list <- r.getHourlyReport(t.toDomain)
  } yield list.map(BalanceSnapshot.fromDomain)).mapError(r =>
    ServerError(s"Retrieving hourly balance failed: ${r.getMessage}")
  )

  private val topUpEndpoint =
    baseEndpoint.post
      .in(BASE_PATH / "top-up")
      .in(jsonBody[BtcTopUp])
      .out(emptyOutput)
      .zServerLogic(topUp => topUpServerLogic(topUp))

  private val hourlyBalanceEndpoint =
    baseEndpoint.post
      .in(BASE_PATH / "hourly-balance")
      .in(jsonBody[DateTimeRange])
      .out(jsonBody[List[BalanceSnapshot]])
      .zServerLogic(range => hourlyBalanceLogic(range))

  val all: List[ZServerEndpoint[Env, ZioStreams]] = List(
    topUpEndpoint,
    hourlyBalanceEndpoint
  )
}
