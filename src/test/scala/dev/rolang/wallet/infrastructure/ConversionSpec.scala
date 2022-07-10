package dev.rolang.wallet.infrastructure

import java.time.OffsetDateTime

import dev.rolang.wallet.domain.{Satoshi, SatoshiTopUp}

import zio.Scope
import zio.test.Assertion.equalTo
import zio.test.{Gen, Spec, TestEnvironment, ZIOSpecDefault, assert, check}

object ConversionSpec extends ZIOSpecDefault {
  override def spec: Spec[TestEnvironment & Scope, Any] = suite("KafkaTopUpRepositorySpec")(test("convert events") {
    val min = OffsetDateTime.parse("1970-01-01T00:00:00Z")
    val max = OffsetDateTime.parse("2030-01-01T00:00:00Z")

    check(Gen.uuid, Gen.offsetDateTime(min, max), Gen.long) { case (id, ts, amount) =>
      val asEvent = Conversion.toTransactionEvent(id, SatoshiTopUp(ts, Satoshi(amount)))
      val asProto = proto.TransactionEvent(id.toString, ts.toInstant.toEpochMilli, amount)
      val asBytes = proto.TransactionEvent.toByteArray(asProto)

      assert(asEvent)(equalTo(Conversion.toTransactionEvent(asProto))) &&
      assert(asEvent)(equalTo(Conversion.toTransactionEvent(asBytes))) &&
      assert(asProto)(equalTo(Conversion.toTransactionEventProto(asEvent))) &&
      assert(asProto)(equalTo(Conversion.toTransactionEventProto(asBytes)))
    }
  })
}
