package actors
  
import akka.actor._
import akka.cluster.Cluster
import akka.cluster.ClusterEvent.MemberUp
import akka.contrib.pattern.ClusterSingletonProxy
import akka.routing.FromConfig
import backend.SentimentActor
import backend.journal.SharedJournalSetter

object ActorManagerExtension extends ExtensionKey[ActorManagerExtension]

class ActorManagerExtension(system: ExtendedActorSystem) extends Extension {
  //val address = Address("akka.tcp", "application", "127.0.1.1", 2555)
  //val path = RootActorPath(address) / "user"
  //val stockManagerActor = system.actorOf(Props(classOf[StockManagerActor]))
  //val stockManagerActor = system.actorSelection(path / "stockManagerActor")
  val sentimentActor = system.actorOf(FromConfig.props(SentimentActor.props()), "sentimentRouter")
  //val stockManagerProxy = system.actorOf(StockManagerProxy.props(system), "stockManagerProxy")
  val stockManagerProxy = system.actorOf(
    ClusterSingletonProxy.props(
      "/user/singleton/stockManagerActor",
      Some("backend")
    ),
    "stockManagerProxy"
  )


  system.actorOf(SharedJournalSetter.props,"shared-journal-setter")
}

trait ActorManagerExtensionActor {
  this : Actor =>

  val actorManager: ActorManagerExtension = ActorManagerExtension(context.system)
}