package dev.rolang.wallet.infrastructure.http.wallet.v1

import java.time.Instant

import dev.rolang.wallet.domain
import dev.rolang.wallet.domain.Conversion
import io.circe.Codec
import io.circe.generic.semiauto.deriveCodec
import sttp.tapir.Schema

final case class BalanceSnapshot(hour: Instant, balance: BigDecimal)

object BalanceSnapshot {
  implicit final val encoder: Codec[BalanceSnapshot] = deriveCodec
  implicit val schema: Schema[BalanceSnapshot]       = Schema.derived

  def fromDomain(model: domain.BalanceSnapshot): BalanceSnapshot =
    BalanceSnapshot(
      model.datetime,
      Conversion.toBtcAmount(model.balance).value
    )
}
