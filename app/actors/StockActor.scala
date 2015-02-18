package actors


import akka.util.Timeout
import scala.concurrent.duration._
import scala.util.{Success, Failure}
import akka.actor._
import akka.pattern.AskSupport
import akka.persistence.{SnapshotOffer, PersistentActor}
import utils.FakeStockQuote
import scala.collection.immutable.{HashSet, Queue}
import scala.concurrent.Future
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global
import actors.StockManagerActor._
import actors.StockActor._
import scala.util.Random

/**
 * There is one StockActor per stock symbol.  The StockActor maintains a list of users watching the stock and the stock
 * values.  Each StockActor updates a rolling dataset of randomly generated stock values.
 */

class StockActor(symbol: String) extends PersistentActor with AskSupport {

    override def persistenceId = "sample-id-1"

    protected[this] var watchers: HashSet[ActorRef] = HashSet.empty[ActorRef]

    val rand = Random

    // A random data set which uses stockQuote.newPrice to get each data point
    var stockHistory: Queue[Double] = {
        lazy val initialPrices: Stream[Double] = (rand.nextDouble * 800) #:: initialPrices.map(previous => FakeStockQuote.newPrice(previous))
        initialPrices.take(50).to[Queue]
    }

    // Fetch the latest stock value every 75ms
    val stockTick = context.system.scheduler.schedule(Duration.Zero, 75.millis, self, FetchLatest)


    def addWatcher(w: ActorRef) = {
      watchers = watchers + w
    }

    def removeWatcher(w: ActorRef) = {
      watchers = watchers - w
    }

  context.system.scheduler.schedule(1 minute, 3 minutes, self, SaveWatcher)

  override def receiveCommand: Receive = {
    case FetchLatest =>
      // add a new stock price to the history and drop the oldest
      val newPrice = FakeStockQuote.newPrice(stockHistory.last.doubleValue())
      stockHistory = stockHistory.drop(1) :+ newPrice
      // notify watchers
      watchers.foreach(_ ! StockUpdate(symbol, newPrice))
    case WatchStock(_) =>
      persist(StockWatchedEvt(sender)){ evt =>
        // send the stock history to the user
        evt.watcher ! StockHistory(symbol, stockHistory.toList)
        // add the watcher to the list
        addWatcher(evt.watcher)
      }
    case UnwatchStock(_) =>
      persist(StockUnwatchedEvt(sender)) { evt =>
        removeWatcher(evt.watcher)
        if (watchers.size == 0) {
          stockTick.cancel()
          context.stop(self)
        }
      }

    case AddWatcherAfterValidationEvt(watcher) => addWatcher(watcher)
    case RemoveWatcherAfterValidationEvt(watcher) => removeWatcher(watcher)
    case SaveWatcher => saveSnapshot(TakeSnapshotEvt(watchers, stockHistory))
  }

  implicit val timeout = Timeout(5 seconds)

  override def receiveRecover: Receive = {
      case StockWatchedEvt(watcher) =>
        val actorIdentity: Future[ActorIdentity] = (watcher ? Identify(self.path)).mapTo[ActorIdentity]
        actorIdentity.onComplete {
          case Success(actorIdentity)  =>
            actorIdentity.ref match {
              case Some(_) => self ! AddWatcherAfterValidationEvt(watcher)
              case None => self ! RemoveWatcherAfterValidationEvt(watcher)
            }
          case Failure(_) => self ! RemoveWatcherAfterValidationEvt(watcher)
        }
      case  StockUnwatchedEvt(watcher) => removeWatcher(watcher)
      case SnapshotOffer(_, TakeSnapshotEvt(myWatchers, myHistory)) => stockHistory = myHistory ; watchers = myWatchers
    }
}

object StockActor {

    def props(symbol: String): Props =
        Props(new StockActor(symbol))

    case object FetchLatest

    case class StockWatchedEvt(watcher: ActorRef)
    case class StockUnwatchedEvt(watcher: ActorRef)
    case class RemoveWatcherAfterValidationEvt(watcher: ActorRef)
    case class AddWatcherAfterValidationEvt(watcher: ActorRef)
    case object SaveWatcher

    case class TakeSnapshotEvt(watchers: HashSet[ActorRef], stockHistory: Queue[Double])

}