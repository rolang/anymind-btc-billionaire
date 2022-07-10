package dev.rolang.wallet.domain

import java.time.OffsetDateTime

final case class SatoshiTopUp(datetime: OffsetDateTime, amount: Satoshi)
