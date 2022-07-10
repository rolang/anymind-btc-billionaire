package dev.rolang.wallet.infrastructure.http

import java.time.Instant
import java.util.UUID

import dev.rolang.wallet.config.HttpConfig
import dev.rolang.wallet.domain.{
  BalanceSnapshot,
  DateTimeRange,
  Satoshi,
  SatoshiTopUp,
  TopUpRepository,
  TransactionEvent,
  TransactionsRepository,
  WalletService
}
import dev.rolang.wallet.infrastructure.http.wallet.v1.BalanceSnapshot.encoder
import dev.rolang.wallet.infrastructure.testsupport.Http.{getRequest, postRequest, toJson}
import io.circe.syntax.EncoderOps

import zio.test.Assertion.equalTo
import zio.test.{Spec, TestEnvironment, ZIOSpecDefault, *}
import zio.{Scope, Task, ULayer, ZIO, ZLayer}

object ServerSpec extends ZIOSpecDefault {
  val randomPortHttpConfig: ULayer[HttpConfig] = ZLayer.succeed(HttpConfig(0))

  private val dummyTopUpRepo = ZLayer.succeed(new TopUpRepository[Task] {
    override def topUp(transaction: SatoshiTopUp): Task[TransactionEvent] = ZIO.succeed(
      TransactionEvent(UUID.randomUUID(), Instant.now(), 1L)
    )
  })

  private val exampleBalance     = List(BalanceSnapshot(Instant.now(), Satoshi(1L)))
  private val exampleBalanceJson = exampleBalance.map(wallet.v1.BalanceSnapshot.fromDomain).asJson

  private val dummyQueryRepo = ZLayer.succeed(new TransactionsRepository[Task] {
    override def addEvent(event: TransactionEvent): Task[Unit]                                 = ZIO.unit
    override def listHourlyBalanceSnapshots(range: DateTimeRange): Task[List[BalanceSnapshot]] =
      ZIO.succeed(exampleBalance)
  })

  private val dummyServiceLayer = ZLayer((for {
    a <- ZIO.service[TopUpRepository[Task]]
    b <- ZIO.service[TransactionsRepository[Task]]
  } yield new WalletService[Task](a, b)).provide(dummyTopUpRepo ++ dummyQueryRepo))

  val serverLayer: ULayer[HttpConfig & WalletService[Task]] =
    randomPortHttpConfig ++ dummyServiceLayer

  override def spec: Spec[TestEnvironment & Scope, Any] =
    suite("Http Server spec")(
      test("/health returns 200") {
        for {
          server   <- Server.http4sServe
          url       = s"${server.baseUri}/health"
          response <- getRequest(url)
        } yield assert(response.statusCode())(equalTo(200))
      },
      test("unknown endpoint returns 404") {
        for {
          server   <- Server.http4sServe
          url       = s"${server.baseUri}/unknown"
          response <- getRequest(url)
        } yield assert(response.statusCode())(equalTo(404))
      },
      test("/wallet/v1/top-up returns ok") {
        for {
          server   <- Server.http4sServe
          url       = s"${server.baseUri}/wallet/v1/top-up"
          body      = """{"datetime": "2022-07-07T00:00:00Z", "amount": 1.0}"""
          response <- postRequest(url, body)
        } yield assert(response.statusCode())(equalTo(200))
      },
      test("/wallet/v1/top-up invalid amount returns 400") {
        for {
          server   <- Server.http4sServe
          url       = s"${server.baseUri}/wallet/v1/top-up"
          body      = """{"datetime": "2022-07-07T00:00:00Z", "amount": 0.000000001}"""
          response <- postRequest(url, body)
        } yield assert(response.statusCode())(equalTo(400))
      },
      test("/wallet/v1/hourly-balance returns ok") {
        for {
          server   <- Server.http4sServe
          url       = s"${server.baseUri}/wallet/v1/hourly-balance"
          body      = """{"from": "2022-07-07T00:00:00Z", "to": "2022-07-07T00:00:00Z"}"""
          response <- postRequest(url, body)
        } yield assert(response.statusCode())(equalTo(200)) &&
          assert(toJson(response.body()))(equalTo(exampleBalanceJson))
      },
      test("/wallet/v1/hourly-balance invalid body returns 400") {
        for {
          server   <- Server.http4sServe
          url       = s"${server.baseUri}/wallet/v1/hourly-balance"
          body      = """{"from": "2022-07-07", "to": "2022-07-07"}"""
          response <- postRequest(url, body)
        } yield assert(response.statusCode())(equalTo(400))
      }
    ).provideCustomLayerShared(Scope.default ++ serverLayer)

}
