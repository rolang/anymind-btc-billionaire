package dev.rolang.wallet.domain

import zio.Scope
import zio.test.Assertion.equalTo
import zio.test.{Spec, TestEnvironment, ZIOSpecDefault, _}

object ConversionSpec extends ZIOSpecDefault {

  val maxBtc: Btc          = Btc(BigDecimal(21000000.0))
  val maxSatoshi: Satoshi  = Satoshi(2100000000000000L)
  val minBtcPrecision: Btc = Btc(BigDecimal(0.00000001d))
  val oneSatoshi: Satoshi  = Satoshi(1L)

  override def spec: Spec[TestEnvironment & Scope, Any] =
    suite("ConversionSpec")(
      test("convert from BTC to Satoshi") {
        assert(Conversion.toSatoshiAmount(minBtcPrecision))(equalTo(Satoshi(1)))
        assert(Conversion.toSatoshiAmount(maxBtc))(equalTo(maxSatoshi))

        check(Gen.bigDecimal(minBtcPrecision.value, maxBtc.value)) { n =>
          val expected = Satoshi((n * 100000000).longValue)
          assert(Conversion.toSatoshiAmount(Btc(n)))(equalTo(expected))
        }
      },
      test("convert from Satoshi to BTC") {
        assert(Conversion.toBtcAmount(oneSatoshi))(equalTo(minBtcPrecision))
        assert(Conversion.toBtcAmount(maxSatoshi))(equalTo(maxBtc))

        check(Gen.long) { n =>
          val expected = Btc(BigDecimal(n) / 100000000)
          assert(Conversion.toBtcAmount(Satoshi(n)))(equalTo(expected))
        }
      }
    )

}
