package dev.rolang.wallet.infrastructure

import java.time.Instant
import java.util.UUID

import scala.concurrent.Future
import scala.concurrent.duration.FiniteDuration

import akka.actor.{ActorSystem, Cancellable}
import akka.kafka.ConsumerMessage.{CommittableMessage, CommittableOffset}
import akka.kafka.scaladsl.{Committer, Consumer}
import akka.kafka.{CommitterSettings, ConsumerSettings, Subscriptions}
import akka.stream.scaladsl.Source
import dev.rolang.wallet.config.KafkaConfig
import dev.rolang.wallet.domain.{TransactionEvent, TransactionsRepository}
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.common.serialization.{ByteArrayDeserializer, StringDeserializer}
import org.slf4j.{Logger, LoggerFactory}

import zio.{Task, Unsafe, ZIO, ZLayer}

class WalletEventsKafkaConsumer(
  kafkaConfig: KafkaConfig,
  refreshViewInterval: FiniteDuration,
  repo: TransactionsRepository[Task]
)(implicit
  system: ActorSystem
) {
  // TODO inject logger via a layer
  private val logger: Logger = LoggerFactory.getLogger(getClass)

  private val consumerSettings =
    ConsumerSettings(system, new StringDeserializer, new ByteArrayDeserializer)
      .withGroupId(kafkaConfig.consumerGroup)
      .withBootstrapServers(kafkaConfig.bootstrapServers)
      .withProperty(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest")

  private val committerSettings = CommitterSettings(system)

  // more configuration like RestartSettings etc. for the consumer could be introduced here
  private val consumerStream: Source[Unit, Consumer.Control] = Consumer
    .committableSource(
      consumerSettings,
      Subscriptions.topics(kafkaConfig.walletTopUpTopic)
    )
    .mapAsync(kafkaConfig.consumerConcurrency)(handleRecord)
    .via(Committer.flow(committerSettings))
    .map(_ => ())

  private val refreshStream: Source[Unit, Cancellable] =
    Source.tick(refreshViewInterval, refreshViewInterval, ()).mapAsync(1)(_ => refreshViewFuture)

  private val mergedStream =
    Source.mergePrioritizedN(Seq((consumerStream, 100), (refreshStream, 1)), eagerComplete = false)

  // added withinDuration configuration to enable easier termination of the consumer in tests
  def run(withinDuration: Option[FiniteDuration]): Task[Unit] = ZIO.fromFuture { _ =>
    withinDuration match {
      case Some(n) => mergedStream.takeWithin(n).run()
      case _       => mergedStream.run()
    }
  }.unit

  private def refreshViewFuture: Future[Unit] =
    Unsafe.unsafe { implicit u =>
      zio.Runtime.default.unsafe.runToFuture {
        ZIO.from(logger.info("Running balance view refresh...")) *> repo.refreshBalanceView
      }
    }

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
  def layer(
    refreshViewInterval: FiniteDuration
  ): ZLayer[TransactionsRepository[Task] & ActorSystem & KafkaConfig, Nothing, WalletEventsKafkaConsumer] =
    ZLayer {
      for {
        config <- ZIO.service[KafkaConfig]
        system <- ZIO.service[ActorSystem]
        repo   <- ZIO.service[TransactionsRepository[Task]]
      } yield new WalletEventsKafkaConsumer(config, refreshViewInterval, repo)(system)
    }
}
