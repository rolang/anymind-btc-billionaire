package dev.rolang.wallet.infrastructure.repository

import java.time.Instant
import java.util.UUID

import akka.actor.{ActorSystem, CoordinatedShutdown}
import akka.kafka.ProducerSettings
import akka.kafka.scaladsl.SendProducer
import dev.rolang.wallet.config.KafkaConfig
import dev.rolang.wallet.domain.{SatoshiTopUp, TopUpRepository, TransactionEvent}
import dev.rolang.wallet.infrastructure.proto
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.serialization.{ByteArraySerializer, StringSerializer}

import zio.{Random, Task, ZIO, ZLayer}

class KafkaTopUpRepository(producer: SendProducer[String, Array[Byte]], topic: String, random: Random)
    extends TopUpRepository[Task] {
  import KafkaTopUpRepository._

  override def topUp(t: SatoshiTopUp): Task[TransactionEvent] = for {
    id        <- random.nextUUID
    event      = toTransactionEvent(id, t)
    eventProto = toTransactionEventProto(event)
    record     = new ProducerRecord(topic, id.toString, eventProto.toByteArray)
    _         <- ZIO.fromFuture(_ => producer.send(record))
  } yield event
}

object KafkaTopUpRepository {

  def toTransactionEvent(id: UUID, topOp: SatoshiTopUp): TransactionEvent =
    TransactionEvent(id, Instant.ofEpochMilli(topOp.datetime.toInstant.toEpochMilli), topOp.amount.value)

  def toTransactionEvent(p: proto.TransactionEvent): TransactionEvent =
    TransactionEvent(
      UUID.fromString(p.transactionId),
      Instant.ofEpochMilli(p.datetimeMs),
      p.amount
    )

  def toTransactionEventProto(event: TransactionEvent): proto.TransactionEvent =
    proto.TransactionEvent(event.id.toString, event.datetime.toEpochMilli, event.amount)

  val layer: ZLayer[ActorSystem & KafkaConfig & Random, Throwable, TopUpRepository[Task]] = ZLayer {
    for {
      system          <- ZIO.service[ActorSystem]
      kafkaConfig     <- ZIO.service[KafkaConfig]
      random          <- ZIO.service[Random]
      producerSettings = ProducerSettings(system, new StringSerializer, new ByteArraySerializer)
                           .withBootstrapServers(kafkaConfig.bootstrapServers)
      producer         = SendProducer(producerSettings)(system)
      // add shutdown hook
      _               <- ZIO.from {
                           CoordinatedShutdown(system)
                             .addTask(CoordinatedShutdown.PhaseBeforeActorSystemTerminate, "close-wallet-repo-producer") { () =>
                               producer.close()
                             }
                         }
    } yield new KafkaTopUpRepository(producer, kafkaConfig.walletTopUpTopic, random)
  }
}
