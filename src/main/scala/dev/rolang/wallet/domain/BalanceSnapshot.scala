package dev.rolang.wallet.domain

import java.time.Instant

final case class BalanceSnapshot(datetime: Instant, balance: Satoshi)
