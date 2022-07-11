package dev.rolang.wallet.infrastructure.repository

import java.time.temporal.ChronoUnit.{HOURS, MINUTES}
import java.time.{Instant, LocalDate, OffsetDateTime, OffsetTime, ZoneOffset}
import java.util.UUID

import dev.rolang.wallet.config.DBConfig
import dev.rolang.wallet.domain.{BalanceSnapshot, DateTimeRange, Satoshi, TransactionEvent, TransactionsRepository}
import dev.rolang.wallet.infrastructure.db.{DbSessionPool, Migration}
import dev.rolang.wallet.infrastructure.testsupport.Postgres

import zio.test.Assertion.{equalTo, hasSameElements, hasSize}
import zio.test.{Spec, TestEnvironment, ZIOSpecDefault, _}
import zio.{Scope, Task, ZIO, ZLayer}

object SkunkTransactionsRepositorySpec extends ZIOSpecDefault {

  private val repoLayer: ZLayer[Scope & DBConfig, Throwable, TransactionsRepository[Task]] =
    ZLayer(DbSessionPool.scopedSession) >>> SkunkTransactionsRepository.layer

  private val postgresLayer = Scope.default >>> Postgres.embedded

  private def toOffsetDateTime(instant: Instant) = OffsetDateTime.ofInstant(instant, ZoneOffset.UTC)

  override def spec: Spec[TestEnvironment & Scope, Any] = suite("SkunkTransactionsRepositorySpec")(
    test("add events and list balance snapshots") {
      (for {
        _          <- Migration.runMigration
        r          <- ZIO.service[TransactionsRepository[Task]]
        date        = LocalDate.of(2022, 7, 10)
        fromOneAm   = date.atTime(OffsetTime.of(1, 0, 0, 0, ZoneOffset.UTC)).toInstant
        fromTwoAm   = fromOneAm.plus(1, HOURS)
        fromFiveAm  = fromTwoAm.plus(3, HOURS)
        _          <- ZIO.foreachDiscard(
                        Seq(
                          // to 2 am balance 3
                          TransactionEvent(UUID.randomUUID(), fromOneAm, 1L),
                          TransactionEvent(UUID.randomUUID(), fromOneAm.plus(10, MINUTES), 1L),
                          TransactionEvent(UUID.randomUUID(), fromOneAm.plus(30, MINUTES), 1L),
                          // to 3 am balance 3 + 6 = 9
                          TransactionEvent(UUID.randomUUID(), fromTwoAm, 2L),
                          TransactionEvent(UUID.randomUUID(), fromTwoAm.plus(5, MINUTES), 2L),
                          TransactionEvent(UUID.randomUUID(), fromTwoAm.plus(45, MINUTES), 2L),
                          // gap of 4 and 5 am = 9
                          // to 6 am balance 3 + 6 + 1 = 10
                          TransactionEvent(UUID.randomUUID(), fromFiveAm, 0L),
                          TransactionEvent(UUID.randomUUID(), fromFiveAm, 1L),
                          TransactionEvent(UUID.randomUUID(), fromFiveAm.plus(2, MINUTES), 0L)
                        )
                      )(r.addEvent)
        expectedAll = Seq(
                        BalanceSnapshot(fromOneAm.plus(1, HOURS), Satoshi(3L)),
                        BalanceSnapshot(fromOneAm.plus(2, HOURS), Satoshi(9L)),
                        BalanceSnapshot(fromOneAm.plus(3, HOURS), Satoshi(9L)),
                        BalanceSnapshot(fromOneAm.plus(4, HOURS), Satoshi(9L)),
                        BalanceSnapshot(fromOneAm.plus(5, HOURS), Satoshi(10L))
                      )
        expectedGap = Seq(
                        BalanceSnapshot(fromOneAm.plus(3, HOURS), Satoshi(9L)),
                        BalanceSnapshot(fromOneAm.plus(4, HOURS), Satoshi(9L)),
                      )
        allRange    = toDateTimeRange(fromOneAm, fromFiveAm.plus(1, HOURS))
        gapRange    = toDateTimeRange(fromOneAm.plus(3, HOURS), fromOneAm.plus(4, HOURS))
        allResults <- r.listHourlyBalanceSnapshots(allRange)
        gapResult  <- r.listHourlyBalanceSnapshots(gapRange)
      } yield {
        assert(allResults)(hasSize(equalTo(expectedAll.size))) &&
        assert(allResults)(hasSameElements(expectedAll)) &&
        assert(gapResult)(hasSize(equalTo(gapResult.size))) &&
        assert(gapResult)(hasSameElements(expectedGap))
      }).provideSomeLayer(repoLayer)

    }
  ).provideCustomLayerShared(Scope.default ++ postgresLayer)

  private def toDateTimeRange(from: Instant, to: Instant): DateTimeRange =
    DateTimeRange(from = toOffsetDateTime(from), to = toOffsetDateTime(to))
}
