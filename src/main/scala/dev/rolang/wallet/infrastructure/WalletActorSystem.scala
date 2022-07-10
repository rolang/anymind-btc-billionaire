package dev.rolang.wallet.infrastructure

import akka.actor.ActorSystem

import zio.{ZIO, ZLayer}

object WalletActorSystem {
  val layer: ZLayer[Any, Throwable, ActorSystem] =
    ZLayer
      .scoped(
        ZIO.acquireRelease(
          ZIO
            .attempt(ActorSystem("WalletSystem"))
        )(sys => ZIO.fromFuture(_ => sys.terminate()).either)
      )
}
