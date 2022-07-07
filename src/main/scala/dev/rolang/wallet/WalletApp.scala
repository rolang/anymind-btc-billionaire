package dev.rolang.wallet

import dev.rolang.wallet.config.HttpConfig
import dev.rolang.wallet.http.Server

object WalletApp {
  def main(args: Array[String]): Unit = {
    val httpConfig = HttpConfig(sys.env.get("PORT").flatMap(_.toIntOption).getOrElse(8080))
    Server.start(httpConfig)
  }
}
