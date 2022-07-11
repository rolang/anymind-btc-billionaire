package dev.rolang.wallet.infrastructure

import akka.actor.ActorSystem
import akka.kafka.ProducerSettings
import akka.kafka.scaladsl.SendProducer
import dev.rolang.wallet.config.KafkaConfig
import dev.rolang.wallet.domain.{BalanceSnapshot, DateTimeRange, TransactionEvent, TransactionsRepository}
import dev.rolang.wallet.infrastructure.testsupport.Kafka
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.serialization.{ByteArraySerializer, StringSerializer}
import zio.{Ref, Scope, Task, ZIO, ZLayer}
import zio.test.*
import zio.test.Assertion.{equalTo, hasSameElements, hasSize}

import java.time.Instant
import java.util.UUID
import scala.concurrent.duration.DurationInt

object WalletEventsKafkaConsumerSpec extends ZIOSpecDefault {
  private val kafkaConfigLayer = {
    val topic = UUID.randomUUID().toString
    val group = UUID.randomUUID().toString

    Kafka.embedded >>> ZLayer {
      for {
        kafka <- ZIO.service[Kafka]
      } yield KafkaConfig(topic, group, 1, kafka.bootstrapServers.head)
    }
  }

  class TestTransactionsRepository(val storage: Ref[Vector[TransactionEvent]]) extends TransactionsRepository[Task] {
    override def addEvent(event: TransactionEvent): Task[Unit] =
      storage.getAndUpdate(_ :+ event).unit

    override def listHourlyBalanceSnapshots(range: DateTimeRange): Task[List[BalanceSnapshot]] = ZIO.succeed(Nil)
  }

  val testRepoLayer: ZLayer[Any, Nothing, TransactionsRepository[Task]] = ZLayer {
    for {
      s <- Ref.make(Vector.empty[TransactionEvent])
    } yield new TestTransactionsRepository(s)
  }

  override def spec: Spec[TestEnvironment & Scope, Any] =
    suite("WalletEventsKafkaConsumerSpec")(test("consume and store events") {
      (for {
        system          <- ZIO.service[ActorSystem]
        kafkaConfig     <- ZIO.service[KafkaConfig]
        subject         <- ZIO.service[WalletEventsKafkaConsumer]
        producerSettings = ProducerSettings(system, new StringSerializer, new ByteArraySerializer)
                             .withBootstrapServers(kafkaConfig.bootstrapServers)
        producer         = SendProducer(producerSettings)(system)
        produced        <- ZIO.foreach((1 to 20).toVector) { _ =>
                             val event  = proto.TransactionEvent(UUID.randomUUID().toString, Instant.now().toEpochMilli, 1L)
                             val record =
                               new ProducerRecord(kafkaConfig.walletTopUpTopic, event.transactionId, event.toByteArray)
                             ZIO.fromFuture(_ => producer.send(record)).as(event)
                           }
        _               <- subject.consumeTask(Some(2.seconds))
        testRepo        <- ZIO.service[TransactionsRepository[Task]].map(_.asInstanceOf[TestTransactionsRepository])
        stored          <- testRepo.storage.get.map(_.map(Conversion.toTransactionEventProto))
      } yield assert(stored)(hasSize(equalTo(produced.size))) &&
        assert(stored)(hasSameElements(produced))).provideSomeLayer(WalletEventsKafkaConsumer.layer)

    }).provideCustomLayerShared(WalletActorSystem.layer ++ kafkaConfigLayer ++ testRepoLayer)
}
