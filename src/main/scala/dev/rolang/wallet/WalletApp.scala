package dev.rolang.wallet

import dev.rolang.wallet.config.{DBConfig, HttpConfig, KafkaConfig}
import dev.rolang.wallet.domain.{TopUpRepository, TransactionsRepository, WalletService}
import dev.rolang.wallet.infrastructure.WalletActorSystem
import dev.rolang.wallet.infrastructure.db.{DbSessionPool, Migration}
import dev.rolang.wallet.infrastructure.http.Server
import dev.rolang.wallet.infrastructure.repository.{KafkaTopUpRepository, SkunkTransactionsRepository}

import zio.{ExitCode, Random, Scope, Task, ZIO, ZIOAppDefault, ZLayer}

object WalletApp extends ZIOAppDefault {

  private val randomLayer     = ZLayer.succeed(Random.RandomLive)
  private val walletRepoLayer =
    (WalletActorSystem.layer ++ KafkaConfig.layer ++ randomLayer) >>> KafkaTopUpRepository.layer

  private val queryRepoLayer =
    (DBConfig.layer >>> ZLayer.fromZIO(DbSessionPool.scopedSession)) >>> SkunkTransactionsRepository.layer

  private val walletServiceLayer: ZLayer[Any, Throwable, WalletService[Task]] =
    (walletRepoLayer ++ queryRepoLayer) >>> ZLayer {
      for {
        r <- ZIO.service[TopUpRepository[Task]]
        h <- ZIO.service[TransactionsRepository[Task]]
        s  = new WalletService(r, h)
      } yield s
    }

  override def run: ZIO[Any, Throwable, ExitCode] = for {
    _        <- Migration.runMigration.provide(DBConfig.layer)
    exitCode <- (Server.http4sServe *> ZIO.never)
                  .provideSomeLayer(HttpConfig.layer ++ walletServiceLayer ++ Scope.default)
                  .exitCode
  } yield exitCode

}
