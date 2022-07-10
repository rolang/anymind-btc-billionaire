package dev.rolang.wallet.infrastructure.repository

import akka.actor.{ActorSystem, CoordinatedShutdown}
import akka.kafka.ProducerSettings
import akka.kafka.scaladsl.SendProducer
import dev.rolang.wallet.config.KafkaConfig
import dev.rolang.wallet.domain.{SatoshiTopUp, TopUpRepository}
import dev.rolang.wallet.infrastructure.proto
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.serialization.{ByteArraySerializer, StringSerializer}

import zio.{Random, Task, ZIO, ZLayer}

class KafkaTopUpRepository(producer: SendProducer[String, Array[Byte]], topic: String, random: Random)
    extends TopUpRepository[Task] {

  override def topUp(t: SatoshiTopUp): Task[Unit] = for {
    id    <- random.nextUUID
    event  = proto.TransactionEvent(id.toString, t.datetime.toInstant.toEpochMilli, t.amount.value)
    record = new ProducerRecord(topic, id.toString, event.toByteArray)
    _     <- ZIO.fromFuture(_ => producer.send(record))
  } yield ()
}

object KafkaTopUpRepository {
  val layer: ZLayer[ActorSystem & KafkaConfig & Random, Throwable, TopUpRepository[Task]] = ZLayer {
    for {
      system          <- ZIO.service[ActorSystem]
      kafkaConfig     <- ZIO.service[KafkaConfig]
      random          <- ZIO.service[Random]
      producerSettings = ProducerSettings(system, new StringSerializer, new ByteArraySerializer)
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
