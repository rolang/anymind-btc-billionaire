package dev.rolang.wallet.domain
import java.time.temporal.ChronoUnit

import fs2.{Pipe, Stream}

trait TransactionsRepository[F[_]] {
  def addEvent(event: TransactionEvent): F[Unit]

  def listHourlyBalanceSnapshots(range: DateTimeRange): F[List[BalanceSnapshot]]
}

object TransactionsRepository {
  // this could be made redundant if we'd store this result in the database to optimize querying performance
  def fillHourlySnapshotGapsPipe[F[_]]: Pipe[F, BalanceSnapshot, BalanceSnapshot] =
    _.zipWithPrevious.flatMap {
      case (Some(prev), next) =>
        val overOneHourDiff = ((next.datetime.getEpochSecond - prev.datetime.getEpochSecond) / 3600L).toInt - 1
        Stream.emits(
          Vector.tabulate(overOneHourDiff) { h =>
            prev.copy(datetime = prev.datetime.plus((h + 1).toLong, ChronoUnit.HOURS))
          } :+ next
        )
      case (_, next)          => Stream.emits(Vector(next))
    }
}
