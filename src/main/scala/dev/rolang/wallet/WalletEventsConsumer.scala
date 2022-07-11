package dev.rolang.wallet

import dev.rolang.wallet.config.{DBConfig, KafkaConfig}
import dev.rolang.wallet.infrastructure.db.{DbSessionPool, Migration}
import dev.rolang.wallet.infrastructure.repository.SkunkTransactionsRepository
import dev.rolang.wallet.infrastructure.{WalletActorSystem, WalletEventsKafkaConsumer}

import zio.{ExitCode, Scope, ZIO, ZIOAppDefault, ZLayer}

object WalletEventsConsumer extends ZIOAppDefault {

  private val repoLayer =
    (DBConfig.layer >>> ZLayer(DbSessionPool.scopedSession)) >>> SkunkTransactionsRepository.layer

  private val consumerLayer =
    (WalletActorSystem.layer ++ KafkaConfig.layer ++ repoLayer) >>> WalletEventsKafkaConsumer.layer

  override def run: ZIO[Any, Any, ExitCode] = (for {
    _        <- Migration.runMigration.provide(DBConfig.layer)
    consumer <- ZIO.service[WalletEventsKafkaConsumer]
    e        <- consumer.consumeTask(None).exitCode
  } yield e).provideSomeLayer(Scope.default ++ consumerLayer)
}
