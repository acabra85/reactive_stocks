package backend

import akka.actor.{ActorRef, ActorSystem}
import actors.{StockManagerProxy, StockManagerActor, Settings}
import akka.cluster.Cluster
import akka.cluster.ClusterEvent.MemberUp

/**
 * Main class for starting cluster nodes.
 */
object MainClusterManager extends BaseApp {

    override protected def initialize(system: ActorSystem, settings: Settings): Unit = {
      val actorManager: ActorRef = system.actorOf(StockManagerActor.props(), "stockManagerActor")
    }

}