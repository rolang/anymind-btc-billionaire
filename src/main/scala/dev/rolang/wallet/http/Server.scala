package dev.rolang.wallet.http

import akka.actor.typed.{ActorRef, ActorSystem, Behavior}
import akka.actor.typed.scaladsl.Behaviors
import dev.rolang.wallet.actors.Wallet.Command
import akka.actor.typed.scaladsl.AskPattern._
import akka.http.scaladsl.Http
import akka.util.Timeout
import dev.rolang.wallet.actors.Wallet
import dev.rolang.wallet.config.HttpConfig

import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration._
import scala.util.{Failure, Success}

object Server {

  private def startHttpServerByWalletActor(
    config: HttpConfig
  )(wallet: ActorRef[Command])(implicit system: ActorSystem[_]): Unit = {
    implicit val ec: ExecutionContext = system.executionContext
    val router                        = new WalletRouter(wallet)
    val routes                        = router.routes

    val httpBindingFuture = Http().newServerAt("localhost", config.port).bind(routes)
    httpBindingFuture.onComplete {
      case Success(binding) =>
        val address = binding.localAddress
        system.log.info(s"Server online at http://${address.getHostString}:${address.getPort}")
      case Failure(ex)      =>
        system.log.error(s"Failed to bind HTTP server, because: $ex")
        system.terminate()
    }
  }

  def start(config: HttpConfig): Unit = {
    trait RootCommand
    case class RetrieveWalletActor(replyTo: ActorRef[ActorRef[Command]]) extends RootCommand

    val rootBehavior: Behavior[RootCommand] = Behaviors.setup { context =>
      val walletActor = context.spawn(Wallet("btc"), "btc-wallet")
      Behaviors.receiveMessage { case RetrieveWalletActor(replyTo) =>
        replyTo ! walletActor
        Behaviors.same
      }
    }

    implicit val system: ActorSystem[RootCommand] = ActorSystem(rootBehavior, "WalletSystem")
    implicit val timeout: Timeout                 = Timeout(5.seconds)
    implicit val ec: ExecutionContext             = system.executionContext

    val walletActorFuture: Future[ActorRef[Command]] = system.ask(replyTo => RetrieveWalletActor(replyTo))
    walletActorFuture.foreach(startHttpServerByWalletActor(config))
  }

}
