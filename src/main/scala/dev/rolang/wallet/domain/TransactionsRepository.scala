package dev.rolang.wallet.domain

trait TransactionsRepository[F[_]] {
  def addEvent(event: TransactionEvent): F[Unit]

  def listHourlyBalanceSnapshots(range: DateTimeRange): F[List[BalanceSnapshot]]
}
