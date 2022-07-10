package dev.rolang.wallet.infrastructure.http.wallet.v1

import java.time.OffsetDateTime

import dev.rolang.wallet.domain
import io.circe.Codec
import io.circe.generic.semiauto.deriveCodec
import sttp.tapir.Schema

final case class DateTimeRange(from: OffsetDateTime, to: OffsetDateTime) {
  def toDomain: domain.DateTimeRange = domain.DateTimeRange(from, to)
}

object DateTimeRange {
  implicit final val encoder: Codec[DateTimeRange] = deriveCodec
  implicit val schema: Schema[DateTimeRange]       = Schema.derived
}
