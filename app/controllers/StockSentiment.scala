package controllers


import actors.ActorManagerExtension
import akka.actor.{ActorRef, ActorSystem}
import play.libs.Akka

import scala.concurrent.ExecutionContext.Implicits.global
import play.api.mvc._
import play.api.libs.json._
import backend.SentimentActor

import akka.pattern.ask
import akka.util.Timeout

import scala.concurrent.duration.{Duration, SECONDS}


object StockSentiment extends Controller {

    val sentimentActor: ActorRef = ActorManagerExtension(Akka.system()).sentimentActor

    implicit val sentimentAskTimeout: Timeout = Duration(15, SECONDS)

    def get(symbol: String): Action[AnyContent] = Action.async {

        (sentimentActor ? SentimentActor.GetSentiment(symbol)).mapTo[JsObject].map {
            sentimentJson => {
                println(s"returning sentimentJson: $sentimentJson")
                Ok(sentimentJson)
            }
        }.recover {
                case nsee: NoSuchElementException =>
                    InternalServerError(Json.obj("error" -> JsString("Could not fetch the tweets")))
        }
    }
}
