package dev.rolang.wallet.infrastructure.http

import java.net.URI
import java.net.http.{HttpClient, HttpRequest, HttpResponse}

import io.circe.Json
import io.circe.parser.parse

import zio.{Task, ZIO}

package object testsupport {

  def postRequest(uri: String, body: String): Task[HttpResponse[String]] = {
    val client = HttpClient.newHttpClient()
    val req    =
      HttpRequest.newBuilder
        .uri(
          URI.create(uri)
        )
        .POST(HttpRequest.BodyPublishers.ofString(body))
        .build

    ZIO.from(
      client.send(req, HttpResponse.BodyHandlers.ofString())
    )
  }

  def getRequest(uri: String): Task[HttpResponse[Void]] = {
    val client = HttpClient.newHttpClient()
    val req    =
      HttpRequest.newBuilder.uri(URI.create(uri)).GET.build

    ZIO.from(
      client.send(req, HttpResponse.BodyHandlers.discarding)
    )
  }

  def toJson(s: String): Json = parse(s).toOption.get

}
