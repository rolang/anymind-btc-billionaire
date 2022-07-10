package dev.rolang.wallet.domain

class WalletService[F[_]](txnRepo: TopUpRepository[F], hourlyBalanceRepo: TransactionsRepository[F]) {
  def topUp(transaction: SatoshiTopUp): F[TransactionEvent] = txnRepo.topUp(transaction)

  def getHourlyReport(range: DateTimeRange): F[List[BalanceSnapshot]] =
    hourlyBalanceRepo.listHourlyBalanceSnapshots(range)
}
