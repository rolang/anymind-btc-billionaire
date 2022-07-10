package dev.rolang.wallet.infrastructure

import java.time.Instant
import java.util.UUID

import scala.concurrent.Future
import scala.concurrent.duration.DurationInt

import akka.actor.ActorSystem
import akka.kafka.ConsumerMessage.{CommittableMessage, CommittableOffset}
import akka.kafka.scaladsl.{Committer, Consumer}
import akka.kafka.{CommitterSettings, ConsumerSettings, Subscriptions}
import akka.stream.RestartSettings
import akka.stream.scaladsl.RestartSource
import dev.rolang.wallet.config.KafkaConfig
import dev.rolang.wallet.domain.{TransactionEvent, TransactionsRepository}
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.common.serialization.{ByteArrayDeserializer, StringDeserializer}

import zio.{Task, Unsafe, ZIO, ZLayer}

class WalletEventsKafkaConsumer(kafkaConfig: KafkaConfig, repo: TransactionsRepository[Task])(implicit
  system: ActorSystem
) {

  private val consumerSettings =
    ConsumerSettings(system, new StringDeserializer, new ByteArrayDeserializer)
      .withGroupId(kafkaConfig.consumerGroup)
      .withProperty(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest")

  private val committerSettings = CommitterSettings(system)

  val consumeTask: Task[Unit] = ZIO.fromFuture { _ =>
    RestartSource
      .onFailuresWithBackoff(
        // could be moved to config
        RestartSettings(minBackoff = 1.second, maxBackoff = 30.seconds, randomFactor = 0.1)
      ) { () =>
        Consumer
          .committableSource(
            consumerSettings,
            Subscriptions.topics(kafkaConfig.walletTopUpTopic)
          )
          .mapAsync(kafkaConfig.consumerConcurrency)(handleRecord)
          .via(Committer.flow(committerSettings))
      }
      .run()
  }.unit

  private def handleRecord(msg: CommittableMessage[String, Array[Byte]]): Future[CommittableOffset] =
    Unsafe.unsafe { implicit u =>
      zio.Runtime.default.unsafe.runToFuture {
        for {
          eventProto <- ZIO.from(proto.TransactionEvent.parseFrom(msg.record.value()))
          event       = TransactionEvent(
                          UUID.fromString(eventProto.transactionId),
                          Instant.ofEpochMilli(eventProto.datetimeMs),
                          eventProto.amount
                        )
          _          <- repo.addEvent(event)
        } yield msg.committableOffset
      }
    }
}

object WalletEventsKafkaConsumer {
  val layer: ZLayer[TransactionsRepository[Task] & ActorSystem & KafkaConfig, Nothing, WalletEventsKafkaConsumer] =
    ZLayer {
      for {
        config <- ZIO.service[KafkaConfig]
        system <- ZIO.service[ActorSystem]
        repo   <- ZIO.service[TransactionsRepository[Task]]
      } yield new WalletEventsKafkaConsumer(config, repo)(system)
    }
}
