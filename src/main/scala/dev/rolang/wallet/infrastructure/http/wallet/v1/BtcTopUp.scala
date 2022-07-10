package dev.rolang.wallet.infrastructure.http.wallet.v1

import java.time.OffsetDateTime

import dev.rolang.wallet.domain.{Btc, Conversion, SatoshiTopUp}
import dev.rolang.wallet.infrastructure.http.wallet.v1.BtcTopUp.BtcTopUpAmount
import eu.timepit.refined.api.Refined
import eu.timepit.refined.numeric.GreaterEqual
import io.circe.Codec
import io.circe.generic.semiauto.deriveCodec
import io.circe.refined.*
import sttp.tapir.Schema
import sttp.tapir.codec.refined.*

final case class BtcTopUp(datetime: OffsetDateTime, amount: BtcTopUpAmount) {
  def toDomain: SatoshiTopUp =
    SatoshiTopUp(datetime, Conversion.toSatoshiAmount(Btc(amount.value)))
}

object BtcTopUp {
  type BtcTopUpAmount = BigDecimal Refined GreaterEqual[0.00000001]

  implicit final val codec: Codec[BtcTopUp] = deriveCodec[BtcTopUp]
  implicit val schema: Schema[BtcTopUp]     = Schema.derived[BtcTopUp]
}
