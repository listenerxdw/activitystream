package activitystreamer.server;

import activitystreamer.util.Settings;
import akka.actor.*;
import akka.cluster.Cluster;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

import java.util.Arrays;

public class ClusterManager {
    private static ActorSystem system;
    private static ActorRef clusterReplicatorRef;

    public static void start() {
        Config config = ConfigFactory.parseString(
                "akka.cluster.auto-join = off\n" +
                        "akka.actor.provider = \"cluster\"\n" +
                        "akka.remote.netty.tcp.hostname=" + Settings.getLocalHostname() + "\n" +
                        "akka.remote.netty.tcp.port=" + Settings.getLocalPort() + "\n")
                .withFallback(ConfigFactory.load());
        system = ActorSystem.create(Settings.getSecret(), config);
        system.actorOf(Props.create(ClusterListener.class), "clusterListener");
        Address address;
        if (Settings.getRemoteHostname() != null) {
            address = new Address("akka.tcp", Settings.getSecret(), Settings.getRemoteHostname(), Settings.getRemotePort());
        } else {
            address = new Address("akka.tcp", Settings.getSecret());
        }
        Cluster.get(system).joinSeedNodes(Arrays.asList(address));
        clusterReplicatorRef = system.actorOf(Props.create(ClusterReplicator.class), "clusterReplicator");
    }

    public static void sendToReplicator(Object obj) {
        clusterReplicatorRef.tell(obj, ActorRef.noSender());
    }
}
