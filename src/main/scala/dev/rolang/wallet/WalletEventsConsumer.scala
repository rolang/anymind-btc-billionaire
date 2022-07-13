package dev.rolang.wallet

import scala.concurrent.duration.{DurationInt, FiniteDuration}

import dev.rolang.wallet.config.{DBConfig, KafkaConfig}
import dev.rolang.wallet.infrastructure.db.{DbSessionPool, Migration}
import dev.rolang.wallet.infrastructure.repository.SkunkTransactionsRepository
import dev.rolang.wallet.infrastructure.{WalletActorSystem, WalletEventsKafkaConsumer}

import zio.config.ConfigDescriptor.*
import zio.{ExitCode, Scope, ZIO, ZIOAppDefault, ZLayer}

object WalletEventsConsumer extends ZIOAppDefault {

  private val repoLayer =
    (DBConfig.layer >>> ZLayer(DbSessionPool.scopedSession)) >>> SkunkTransactionsRepository.layer

  // should probably be moved to config package
  private val refreshFreqConf: ZIO[Any, Throwable, FiniteDuration] = zio.config
    .read(
      duration("SNAPSHOTS_VIEW_REFRESH_FREQUENCY").default(10.seconds)
    )
    .mapError(_.getCause)
    .flatMap(d => ZIO.from(FiniteDuration(d.length, d.unit)))

  private val consumerLayer = ZLayer {
    refreshFreqConf.map { refreshFreq =>
      (WalletActorSystem.layer ++
        KafkaConfig.layer ++
        repoLayer) >>> WalletEventsKafkaConsumer.layer(refreshFreq)
    }
  }.flatten

  override def run: ZIO[Any, Any, ExitCode] = (for {
    _        <- Migration.runMigration.provide(DBConfig.layer)
    consumer <- ZIO.service[WalletEventsKafkaConsumer]
    e        <- consumer.run(None).exitCode
  } yield e).provideSomeLayer(Scope.default ++ consumerLayer)
}
