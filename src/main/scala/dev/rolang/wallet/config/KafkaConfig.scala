package dev.rolang.wallet.config

import zio.ZLayer
import zio.config.ConfigDescriptor.*
import zio.config.*

final case class KafkaConfig(
  walletTopUpTopic: String,
  consumerGroup: String,
  consumerConcurrency: Int,
  bootstrapServers: String
)

object KafkaConfig {
  val layer: ZLayer[Any, ReadError[String], KafkaConfig] = ZConfig.fromSystemEnv(
    (string("KAFKA_TRANSACTION_EVENT_TOPIC").default("wallet-transaction-events-v1") zip
      string("KAFKA_WALLET_QUERY_CONSUMER_GROUP").default("wallet-query-consumer-group-v1") zip
      int("KAFKA_WALLET_QUERY_CONSUMER_CONCURRENCY").default(5) zip
      string("KAFKA_BOOTSTRAP_SERVERS").default("localhost:9092")).to[KafkaConfig]
  )
}
