package dev.rolang.wallet.domain

object Conversion {
  val satoshiPerBtc = 100000000L

  def toSatoshiAmount(btc: Btc): Satoshi = Satoshi((btc.value * satoshiPerBtc).longValue)

  def toBtcAmount(satoshi: Satoshi): Btc = Btc(BigDecimal(satoshi.value) / satoshiPerBtc)
}
