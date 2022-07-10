package dev.rolang.wallet.infrastructure

import java.time.Instant
import java.util.UUID

import dev.rolang.wallet.domain.{SatoshiTopUp, TransactionEvent}

object Conversion {
  def toTransactionEvent(id: UUID, topOp: SatoshiTopUp): TransactionEvent =
    TransactionEvent(id, Instant.ofEpochMilli(topOp.datetime.toInstant.toEpochMilli), topOp.amount.value)

  def toTransactionEvent(bytes: Array[Byte]): TransactionEvent =
    toTransactionEvent(toTransactionEventProto(bytes))

  def toTransactionEvent(p: proto.TransactionEvent): TransactionEvent =
    TransactionEvent(
      UUID.fromString(p.transactionId),
      Instant.ofEpochMilli(p.datetimeMs),
      p.amount
    )

  def toTransactionEventProto(event: TransactionEvent): proto.TransactionEvent =
    proto.TransactionEvent(event.id.toString, event.datetime.toEpochMilli, event.amount)

  def toTransactionEventProto(bytes: Array[Byte]): proto.TransactionEvent =
    proto.TransactionEvent.parseFrom(bytes)
}
