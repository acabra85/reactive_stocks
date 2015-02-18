package backend

import akka.actor.{ActorRef, Props, Actor, ActorLogging}
import play.api.libs.json.{JsObject, JsString, Json, JsValue}
import scala.concurrent.Future
import play.api.libs.ws.WSResponse
import actors.Settings
import utils.WSUtils
import model.Tweet

import akka.pattern.pipe
import play.libs.Akka

class SentimentActor extends Actor with ActorLogging {

    import context.dispatcher

    override def receive: Receive = {
        case SentimentActor.GetSentiment(symbol) => {
            val origSender = sender()
            val futureStockSentiments: Future[JsObject] = for {
                tweets <- getTweets(symbol) // get tweets that contain the stock symbol
                futureSentiments = loadSentimentFromTweets(tweets.json) // queue web requests each tweets' sentiments
                sentiments <- Future.sequence(futureSentiments) // when the sentiment responses arrive, set them
            } yield {
                sentimentJson(sentiments)
            }
            futureStockSentiments.pipeTo(origSender)
        }
    }

    def getTextSentiment(text: String): Future[WSResponse] =
        WSUtils.url(Settings(context.system).sentimentUrl) post Map("text" -> Seq(text))

    def getAverageSentiment(responses: Seq[WSResponse], label: String): Double = responses.map { response =>
        (response.json \\ label).head.as[Double]
    }.sum / responses.length.max(1) // avoid division by zero

    def loadSentimentFromTweets(json: JsValue): Seq[Future[WSResponse]] =
        (json \ "statuses").as[Seq[Tweet]] map (tweet => getTextSentiment(tweet.text))

    def getTweets(symbol: String): Future[WSResponse] = {
        val symbolTweetUrl = Settings(context.system).defaultTweetUrl.format(symbol)
        WSUtils.url(symbolTweetUrl).get.withFilter { response =>
            response.status == 200 //HTTP 200 OK Response
        }
    }

    def sentimentJson(sentiments: Seq[WSResponse]) = {
        val neg = getAverageSentiment(sentiments, "neg")
        val neutral = getAverageSentiment(sentiments, "neutral")
        val pos = getAverageSentiment(sentiments, "pos")

        val response = Json.obj(
            "probability" -> Json.obj(
                "neg" -> neg,
                "neutral" -> neutral,
                "pos" -> pos
            )
        )

        val classification =
            if (neutral > 0.5)
                "neutral"
            else if (neg > pos)
                "neg"
            else
                "pos"

        response + ("label" -> JsString(classification))
    }
}

object SentimentActor {

    //lazy val sentimentActor: ActorRef = Akka.system.actorOf(Props(classOf[SentimentActor]))

    case class GetSentiment(symbol: String)
    case class SentimentResults(results: JsValue)

    def props(): Props =
        Props(new SentimentActor())

}
