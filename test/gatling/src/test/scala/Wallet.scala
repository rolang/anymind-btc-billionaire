import scala.concurrent.duration._
import io.gatling.core.Predef._
import io.gatling.core.structure.PopulationBuilder
import io.gatling.http.Predef._
import io.gatling.http.protocol.HttpProtocolBuilder
import io.gatling.http.request.builder.HttpRequestBuilder

import java.time.Instant
import scala.util.Random

class Wallet extends Simulation {

  val usersPerSecond: Double = 100.0
  val loadDuring: FiniteDuration = 30.seconds

  val httpProtocol: HttpProtocolBuilder = http.baseUrl("http://localhost:8080")

  val minFrom: Instant = Instant.parse("2022-01-01T00:00:00Z")
  val minTo: Instant = Instant.parse("2022-04-01T00:00:00Z")

  def randomInstant(min: Option[Instant] = None): Instant = {
    Instant.ofEpochMilli(
      Random.between(min.getOrElse(minFrom).toEpochMilli, minTo.toEpochMilli)
    )
  }

  def randomAmount: Double = Random.between(0.00000001, 1000000.0)

  val writeReqBuilder: HttpRequestBuilder = http("top-up")
    .post("/wallet/v1/top-up")
    .body(StringBody((_: Session) => {
      s"""{ "datetime":"${randomInstant()}", "amount": $randomAmount }"""
    }))
    .check(status.in(200))

  val readReqBuilder: HttpRequestBuilder = http("query")
    .post("/wallet/v1/hourly-balance")
    .body(StringBody((_: Session) => {
      val from = randomInstant()
      val to = randomInstant(Some(from))
      s"""{ "from":"$from", "to": "$to"}"""
    })).check(status.in(200))

  val writeScn: PopulationBuilder = scenario("write")
    .exec(writeReqBuilder)
    .inject(constantUsersPerSec(usersPerSecond).during(loadDuring)).protocols(httpProtocol)

  val readScn: PopulationBuilder = scenario("read").exec(readReqBuilder)
    .inject(constantUsersPerSec(usersPerSecond).during(loadDuring)).protocols(httpProtocol)

  val readAndWrite = scenario("write and read")
    .exec(writeReqBuilder).exec(readReqBuilder)
    .inject(constantUsersPerSec(usersPerSecond).during(loadDuring)).protocols(httpProtocol)

  // write, read, write and read
  setUp(
    writeScn
      .andThen(readScn)
      .andThen(readAndWrite)
  )
}
