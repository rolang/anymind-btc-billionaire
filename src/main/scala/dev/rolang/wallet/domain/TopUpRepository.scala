package dev.rolang.wallet.domain

trait TopUpRepository[F[_]] {
  def topUp(transaction: SatoshiTopUp): F[TransactionEvent]
}
