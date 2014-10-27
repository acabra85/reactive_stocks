package actors

import akka.actor.{ActorLogging, Actor, ActorRef, Props}
import play.api.libs.json._
import play.Play
import play.api.libs.json.JsObject
import play.api.libs.json.JsString
import play.api.libs.json.JsNumber
import actors.StockManagerActor.StockHistory
import actors.ActorManagerExtension

import scala.collection.JavaConverters._

/** The out actor is wired in by Play Framework when this Actor is created.
  * When a message is sent to out the Play Framework then sends it to the client WebSocket.
  *
  * */
class UserActor(out: ActorRef) extends Actor with ActorLogging {

    //val stockManagerActor = ActorManagerExtension(context.system).stockManagerActor
    val stockManagerActor = ActorManagerExtension(context.system).stockManagerProxy

    // watch the default stocks
    val defaultStocks = Play.application.configuration.getStringList("default.stocks")
    for (stockSymbol <- defaultStocks.asScala) {
        stockManagerActor ! StockManagerActor.WatchStock(stockSymbol)
    }

    def receive = {
        //Handle the FetchTweets message to periodically fetch tweets if there is a query available.
        case StockManagerActor.StockUpdate(symbol, price) =>
            val stockUpdateJson: JsObject = JsObject(Seq(
                "type" -> JsString("stockupdate"),
                "symbol" -> JsString(symbol),
                "price" -> JsNumber(price.doubleValue())
            ))
            out ! stockUpdateJson

        case StockHistory(symbol, history) =>
            val jsonHistList = JsArray(history.map(price => JsNumber(price)))
            val stockHistoryJson: JsObject = JsObject(Seq(
                "type" -> JsString("stockhistory"),
                "symbol" -> JsString(symbol),
                "history" -> jsonHistList
            ))
            out ! stockHistoryJson

        case message: JsValue =>
            (message \ "symbol").asOpt[String] match {
                case Some(symbol) =>
                    stockManagerActor ! StockManagerActor.WatchStock(symbol)
                case None => log.error("symbol was not found in json: $message")
            }
    }

    override def postStop() {
        stockManagerActor ! StockManagerActor.UnwatchStock(None)
    }
}

object UserActor {
    def props(out: ActorRef) = Props(new UserActor(out))
}
