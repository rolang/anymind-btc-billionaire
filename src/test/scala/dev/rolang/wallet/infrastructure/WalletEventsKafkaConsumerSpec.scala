package dev.rolang.wallet.infrastructure

import java.time.Instant
import java.util.UUID

import scala.concurrent.duration.DurationInt

import akka.actor.ActorSystem
import akka.kafka.ProducerSettings
import akka.kafka.scaladsl.SendProducer
import dev.rolang.wallet.config.KafkaConfig
import dev.rolang.wallet.domain.{BalanceSnapshot, DateTimeRange, TransactionEvent, TransactionsRepository}
import dev.rolang.wallet.infrastructure.testsupport.Kafka
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.serialization.{ByteArraySerializer, StringSerializer}

import zio.test.Assertion.{equalTo, hasSameElements, hasSize}
import zio.test.*
import zio.{Ref, Scope, Task, ZIO, ZLayer}

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

  class TestTransactionsRepository(val eventsStorage: Ref[Vector[TransactionEvent]], val refreshCounts: Ref[Int])
      extends TransactionsRepository[Task] {
    override def addEvent(event: TransactionEvent): Task[Unit] =
      eventsStorage.getAndUpdate(_ :+ event).unit

    override def listHourlyBalanceSnapshots(range: DateTimeRange): Task[List[BalanceSnapshot]] = ZIO.succeed(Nil)

    override def refreshBalanceView: Task[Unit] = refreshCounts.getAndUpdate(_ + 1).unit
  }

  val testRepoLayer: ZLayer[Any, Nothing, TransactionsRepository[Task]] = ZLayer {
    for {
      s <- Ref.make(Vector.empty[TransactionEvent])
      c <- Ref.make(0)
    } yield new TestTransactionsRepository(s, c)
  }

  override def spec: Spec[TestEnvironment & Scope, Any] =
    suite("WalletEventsKafkaConsumerSpec")(test("consume events and refresh view") {
      val refreshFreq = 2.seconds
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
        _               <- subject.run(Some(refreshFreq.plus(1.second)))
        testRepo        <- ZIO.service[TransactionsRepository[Task]].map(_.asInstanceOf[TestTransactionsRepository])
        stored          <- testRepo.eventsStorage.get.map(_.map(Conversion.toTransactionEventProto))
        refreshCount    <- testRepo.refreshCounts.get
      } yield assert(stored)(hasSize(equalTo(produced.size))) &&
        assert(stored)(hasSameElements(produced)) &&
        assert(refreshCount)(equalTo(1))).provideSomeLayer(WalletEventsKafkaConsumer.layer(refreshFreq))

    }).provideCustomLayerShared(WalletActorSystem.layer ++ kafkaConfigLayer ++ testRepoLayer)
}
