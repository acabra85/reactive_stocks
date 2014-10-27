package actors

import actors.StockManagerActor.WatchStock
import akka.actor._
import akka.cluster.Cluster
import akka.cluster.ClusterEvent.MemberUp

class StockManagerProxy(system: ActorSystem) extends Actor with Stash{



  Cluster(system).subscribe(self, classOf[MemberUp])

  var stockManagerActor: ActorSelection = _

  def initial: Receive = {
      case memberUp: MemberUp => if ( memberUp.member.hasRole("backend") ) {
        stockManagerActor = system.actorSelection(RootActorPath(memberUp.member.address) / "user" / "stockManagerActor")
        unstashAll()
        context.become(ready)
      }

      case _ => stash()
  }

  override  def receive: Receive = initial

  def ready: Receive = {
    case watchStock: WatchStock => stockManagerActor forward watchStock
  }
}

object StockManagerProxy {
  def props(system: ActorSystem) = Props(new StockManagerProxy(system))
}
