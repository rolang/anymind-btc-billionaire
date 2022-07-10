package dev.rolang.wallet.domain

import java.time.Instant
import java.util.UUID

final case class TransactionEvent(id: UUID, datetime: Instant, amount: Long)
