package dev.rolang.wallet.http

import akka.http.scaladsl.server.Directives._
import akka.actor.typed.{ActorRef, ActorSystem}
import akka.http.scaladsl.model.StatusCodes
import dev.rolang.wallet.actors.Wallet.Command
import dev.rolang.wallet.actors.Wallet.Command._
import dev.rolang.wallet.actors.Wallet.Response
import dev.rolang.wallet.actors.Wallet.Response._
import io.circe.generic.auto._
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport._
import akka.actor.typed.scaladsl.AskPattern._
import akka.util.Timeout

import scala.concurrent.Future
import scala.concurrent.duration._
import akka.http.scaladsl.server.Route
import cats.data.Validated.{Invalid, Valid}
import cats.data.ValidatedNel
import cats.implicits._

import java.time.OffsetDateTime

final case class AddBtc(datetime: OffsetDateTime, amount: Double) {
  def toCommand(replyTo: ActorRef[Response]): Command =
    AddSatoshi(datetime, AddBtc.btcToSatoshiAmount(amount), replyTo)
}

object AddBtc {
  private val oneBtcAsSatoshi: Int = 100000000
  private val minBtc: Double       = 1 / oneBtcAsSatoshi

  def btcToSatoshiAmount(btc: Double): Long =
    (BigDecimal(btc) * oneBtcAsSatoshi).toLong

  def validate(request: AddBtc): ValidatedNel[String, AddBtc] =
    if (request.amount < AddBtc.minBtc) {
      s"${request.amount} is below the minimum threshold ${AddBtc.minBtc}".invalidNel[AddBtc]
    } else {
      request.validNel[String]
    }
}

final case class FailureResponse(reason: String)

class WalletRouter(wallet: ActorRef[Command])(implicit system: ActorSystem[_]) {
  implicit val timeout: Timeout = Timeout(5.seconds)

  def addBtc(request: AddBtc): Future[Response] =
    wallet.ask(replyTo => request.toCommand(replyTo))

  def validateRequest(request: AddBtc)(routeIfValid: Route): Route =
    AddBtc.validate(request) match {
      case Valid(_)               =>
        routeIfValid
      case Invalid(errorMessages) =>
        complete(StatusCodes.BadRequest, FailureResponse(errorMessages.toList.mkString(", ")))
    }

  val routes: Route =
    path("wallet" / "add") {
      pathEndOrSingleSlash {
        post {
          entity(as[AddBtc]) { request =>
            validateRequest(request) {
              onSuccess(addBtc(request)) { case SatoshiAddedResponse(_) =>
                complete(StatusCodes.OK)
              }
            }
          }
        }
      }
    }
}
