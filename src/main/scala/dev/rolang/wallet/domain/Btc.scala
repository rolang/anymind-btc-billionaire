package dev.rolang.wallet.domain

final case class Btc(value: BigDecimal) extends AnyVal
final case class Satoshi(value: Long)   extends AnyVal

object Satoshi {
  def fromDecimal(decimal: BigDecimal): Satoshi = Satoshi(decimal.longValue)
}
