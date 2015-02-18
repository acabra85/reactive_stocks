package backend

import akka.actor.{PoisonPill, ActorRef, ActorSystem}
import actors.{StockManagerProxy, StockManagerActor, Settings}
import akka.cluster.Cluster
import akka.cluster.ClusterEvent.MemberUp
import akka.contrib.pattern.ClusterSingletonManager
import backend.journal.SharedJournalSetter

/**
 * Main class for starting cluster nodes.
 */
object MainClusterManager extends BaseApp {

    override protected def initialize(system: ActorSystem, settings: Settings): Unit = {
      //val actorManager: ActorRef = system.actorOf(StockManagerActor.props(), "stockManagerActor")

      val actorManager = system.actorOf(
        ClusterSingletonManager.props(
          StockManagerActor.props,
          "stockManagerActor",
          PoisonPill,
          Some("backend")
        ),
        "singleton")
      system.actorOf(SharedJournalSetter.props,"shared-journal-setter")
    }

}