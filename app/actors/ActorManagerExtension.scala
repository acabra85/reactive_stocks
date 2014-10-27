package actors
  
import akka.actor._
import akka.cluster.Cluster
import akka.cluster.ClusterEvent.MemberUp

object ActorManagerExtension extends ExtensionKey[ActorManagerExtension]

class ActorManagerExtension(system: ExtendedActorSystem) extends Extension {
  val address = Address("akka.tcp", "application", "127.0.1.1", 2555)
  val path = RootActorPath(address) / "user"
  //val stockManagerActor = system.actorOf(Props(classOf[StockManagerActor]))
  //val stockManagerActor = system.actorSelection(path / "stockManagerActor")

  val stockManagerProxy = system.actorOf(StockManagerProxy.props(system), "stockManagerProxy")
}