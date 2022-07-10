package dev.rolang.wallet.infrastructure.http

import sttp.model.StatusCode
import sttp.tapir.PublicEndpoint
import sttp.tapir.generic.auto.*
import sttp.tapir.json.circe.jsonBody
import sttp.tapir.ztapir.*

object Endpoints {

  val baseEndpoint: PublicEndpoint[Unit, ErrorInfo, Unit, Any] = endpoint.errorOut(
    oneOf[ErrorInfo](
      oneOfVariant(StatusCode.NotFound, jsonBody[NotFound].description("not found")),
      oneOfVariant(StatusCode.BadRequest, jsonBody[BadRequest].description("bad request")),
      oneOfVariant(StatusCode.Unauthorized, emptyOutputAs(Unauthorized)),
      oneOfVariant(StatusCode.NoContent, emptyOutputAs(NoContent)),
      oneOfVariant(StatusCode.Forbidden, emptyOutputAs(Forbidden)),
      oneOfVariant(StatusCode.Conflict, jsonBody[Conflict].description("conflict")),
      oneOfVariant(StatusCode.InternalServerError, jsonBody[ServerError].description("server error"))
    )
  )

}
