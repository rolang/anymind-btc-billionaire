package dev.rolang.wallet.infrastructure.repository

import java.time.OffsetDateTime
import java.util.UUID

import akka.actor.ActorSystem
import akka.kafka.scaladsl.Consumer
import akka.kafka.{ConsumerSettings, Subscriptions}
import dev.rolang.wallet.config.KafkaConfig
import dev.rolang.wallet.domain.{Satoshi, SatoshiTopUp, TopUpRepository, TransactionEvent}
import dev.rolang.wallet.infrastructure.testsupport.Kafka
import dev.rolang.wallet.infrastructure.{WalletActorSystem, proto}
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.common.serialization.{ByteArrayDeserializer, StringDeserializer}

import zio.test.Assertion.{equalTo, hasSameElements, hasSize}
import zio.test.{Spec, TestEnvironment, ZIOSpecDefault, _}
import zio.{Random, Scope, Task, ZIO, ZLayer, durationInt}

object KafkaTopUpRepositorySpec extends ZIOSpecDefault {
  private val kafkaConfigLayer = {
    val topic = UUID.randomUUID().toString
    val group = UUID.randomUUID().toString

    Kafka.embedded >>> ZLayer {
      for {
        kafka <- ZIO.service[Kafka]
      } yield KafkaConfig(topic, group, 1, kafka.bootstrapServers.head)
    }
  }

  private val repo =
    (WalletActorSystem.layer ++ kafkaConfigLayer ++ ZLayer.succeed(Random.RandomLive)) >>> KafkaTopUpRepository.layer

  override def spec: Spec[TestEnvironment & Scope, Any] = suite("KafkaTopUpRepositorySpec")(
    test("convert events") {
      val min = OffsetDateTime.parse("1970-01-01T00:00:00Z")
      val max = OffsetDateTime.parse("2030-01-01T00:00:00Z")

      check(Gen.uuid, Gen.offsetDateTime(min, max), Gen.long) { case (id, ts, amount) =>
        val asEvent = KafkaTopUpRepository.toTransactionEvent(id, SatoshiTopUp(ts, Satoshi(amount)))
        val asProto = KafkaTopUpRepository.toTransactionEventProto(asEvent)

        val bytes          = proto.TransactionEvent.toByteArray(asProto)
        val fromBytesProto = proto.TransactionEvent.parseFrom(bytes)

        assert(asEvent)(equalTo(KafkaTopUpRepository.toTransactionEvent(asProto))) &&
        assert(asProto)(equalTo(fromBytesProto)) &&
        assert(asEvent)(equalTo(KafkaTopUpRepository.toTransactionEvent(fromBytesProto)))
      }
    },
    test("a top-up creates publishes events to a kafka topic") {
      (for {
        system          <- ZIO.service[ActorSystem]
        config          <- ZIO.service[KafkaConfig]
        repository      <- ZIO.service[TopUpRepository[Task]]
        now              = OffsetDateTime.now()
        topUps           = Set.tabulate(20)(i => SatoshiTopUp(now, Satoshi(i.toLong)))
        topUpEvents     <- ZIO.foreach(topUps)(repository.topUp)
        consumerSettings =
          ConsumerSettings(system, new StringDeserializer, new ByteArrayDeserializer)
            .withGroupId(config.consumerGroup)
            .withBootstrapServers(config.bootstrapServers)
            .withProperty(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest")
        consumedEvents  <-
          ZIO.fromFuture { _ =>
            implicit val s: ActorSystem = system
            Consumer
              .plainSource(
                consumerSettings,
                Subscriptions.topics(config.walletTopUpTopic)
              )
              .map { msg =>
                KafkaTopUpRepository.toTransactionEvent(proto.TransactionEvent.parseFrom(msg.value()))
              }
              .take(topUps.size.toLong)
              .runFold(Set.empty[TransactionEvent])((set, event) => set + event)
          }
      } yield assert(consumedEvents)(hasSize(equalTo(topUpEvents.size))) &&
        assert(topUpEvents)(hasSameElements(consumedEvents)))
        .provideSomeLayer(Scope.default ++ WalletActorSystem.layer ++ kafkaConfigLayer ++ repo)
    }
  ) @@ TestAspect.timeout(30.seconds)
}
