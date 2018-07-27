package activitystreamer.server;

import activitystreamer.util.Settings;
import akka.actor.AbstractActor;
import akka.actor.ActorRef;
import akka.cluster.Cluster;
import akka.cluster.ddata.*;
import akka.cluster.ddata.Replicator.Get;
import akka.cluster.ddata.Replicator.GetSuccess;
import akka.cluster.ddata.Replicator.GetFailure;
import akka.cluster.ddata.Replicator.NotFound;
import akka.cluster.ddata.Replicator.Changed;
import akka.cluster.ddata.Replicator.Subscribe;
import akka.cluster.ddata.Replicator.Update;
import akka.cluster.ddata.Replicator.UpdateSuccess;
import akka.cluster.ddata.Replicator.UpdateResponse;
import akka.cluster.ddata.Replicator.WriteConsistency;
import akka.cluster.ddata.Replicator.WriteMajority;
import akka.cluster.ddata.Replicator.WriteAll;
import akka.cluster.ddata.Replicator.ReadConsistency;
import akka.cluster.ddata.Replicator.ReadMajority;
import akka.event.Logging;
import akka.event.LoggingAdapter;
import org.json.simple.JSONObject;
import scala.concurrent.duration.Duration;
import scala.concurrent.java8.FuturesConvertersImpl;
import scala.util.parsing.json.JSON;

import java.util.*;

import static java.util.concurrent.TimeUnit.SECONDS;

public class ClusterReplicator extends AbstractActor {
    private final LoggingAdapter log = Logging.getLogger(getContext().getSystem(), this);
    private final ActorRef replicator =
            DistributedData.get(getContext().getSystem()).replicator();
    private final Cluster node = Cluster.get(getContext().getSystem());

    private final WriteConsistency writeMajority =
            new WriteMajority(Duration.create(3, SECONDS));
    private final WriteConsistency writeAll =
            new WriteAll(Duration.create(3, SECONDS));
    private final static ReadConsistency readMajority =
            new ReadMajority(Duration.create(3, SECONDS));

    final Key<LWWRegister<JSONObject>> lowestLoadServerKey = LWWRegisterKey.create("lowestLoadServer");
    final Key<ORMultiMap<String, String>> usersKey = ORMultiMapKey.create("users");
    final Key<ORMultiMap<Integer, JSONObject>> activityMessagesKey = ORMultiMapKey.create("messages");


    @Override
    public Receive createReceive() {
        return receiveBuilder()
                .match(GetSuccess.class, a -> a.key().equals(lowestLoadServerKey), a -> {
                    GetSuccess<LWWRegister<JSONObject>> g = a;
                    receiveServerLoad(g.dataValue().getValue());
                })
                .match(GetSuccess.class, a -> a.key().equals(usersKey), a -> {
                    log.info("got users");
                    GetSuccess<ORMultiMap<String, String>> g = a;
                    receiveUsers(g.dataValue().getEntries());
                })
                .match(GetSuccess.class, a -> a.key().equals(activityMessagesKey), a -> {
                    GetSuccess<ORMultiMap<Integer, JSONObject>> g = a;
                    receiveActMessages(g.dataValue().getEntries());
                })
                .match(Changed.class, a -> a.key().equals(lowestLoadServerKey), a -> {
                    Changed<LWWRegister<JSONObject>> g = a;
                    receiveServerLoad(g.dataValue().getValue());
                })
                .match(Changed.class, a -> a.key().equals(usersKey), a -> {
                    log.info("users db changed");
                    Changed<ORMultiMap<String, String>> g = a;
                    receiveUsers(g.dataValue().getEntries());
                })
                .match(Changed.class, a -> a.key().equals(activityMessagesKey), a -> {
                    log.info("activity messages set changed");
                    Changed<ORMultiMap<Integer, JSONObject>> g = a;
                    receiveActMessages(g.dataValue().getEntries());
                })
                .match(NotFound.class, a -> a.key().equals(lowestLoadServerKey), a -> {
                    sendServerLoad();
                })
                .match(NotFound.class, a -> a.key().equals(usersKey), a -> {
                    initializeUsers();
                })
                .match(NotFound.class, a -> a.key().equals(activityMessagesKey), a -> {
                    initializeActMessages();
                })
                .match(Changed.class, msg -> {
                    log.info("Changed: {}", msg);
                })
                .match(GetSuccess.class, a -> {
                    log.info(a.key().toString());
                })
                .match(GetFailure.class, a -> {
                    log.error("Get failure");
                })
                .match(User.class, a -> {
                    registerUser(a);
                })
                .match(JSONObject.class, a -> {
                    addActMessage(a);
                })
                .match(String.class, a -> {
                    switch(a) {
                        case "getServerLoad":
                            getServerLoad();
                            break;
                        case "getUsers":
                            getUsers();
                            break;
                        default:
                            log.error("Unrecognised string message: " + a);
                    }
                })
                .build();
    }

    private void receiveServerLoad(JSONObject val) {
        log.info(val.toJSONString());
        String id = (String) val.get("id");
        int curLowestLoad = (int) val.get("load");
        long lastTimestamp = (long) val.get("timestamp");
        if (curLowestLoad > ClientManager.numValidClients()) {
            sendServerLoad();
        }
        if (lastTimestamp < System.currentTimeMillis() - 5000 && id.equals(Settings.getServerId())) {
            sendServerLoad();
        }
        if (lastTimestamp < System.currentTimeMillis() - 10000) {
            sendServerLoad();
        }
        ServerManager.setLowestLoadServer(val);
    }

    private void sendServerLoad() {
        JSONObject obj = new JSONObject();
        obj.put("id", Settings.getServerId());
        obj.put("load", ClientManager.numValidClients());
        obj.put("hostname", Settings.getLocalHostname());
        obj.put("port", Settings.getClientPort());
        obj.put("timestamp", System.currentTimeMillis());

        Update<LWWRegister<JSONObject>> update = new Update<LWWRegister<JSONObject>>(lowestLoadServerKey, LWWRegister.create(node, obj), Replicator.writeLocal(), data -> data.withValue(node, obj));
        replicator.tell(update, self());
        log.info("Sent server load");
    }

    private void getServerLoad() {
        replicator.tell(new Get<LWWRegister<JSONObject>>(lowestLoadServerKey, Replicator.readLocal()), getSelf());
    }

    private void getUsers() {
        // get users from majority of nodes for consistency
        log.info("getting users");
        replicator.tell(new Get<ORMultiMap<String, String>>(usersKey, readMajority), getSelf());
    }

    private void receiveUsers(Map<String, Set<String>> map) {
        log.info(map.toString());
        UserManager.deleteAllUsers();
        for (Map.Entry<String, Set<String>> entry : map.entrySet()) {
            String username = entry.getKey();
            String secret = entry.getValue().iterator().next();
            UserManager.createUser(username, secret);
        }
        UserManager.processRegistrations();
        UserManager.finalizeRegistrations();
    }

    private void initializeUsers() {
        log.info("initialising distributed users db");
        Update<ORMultiMap<String, String>> update = new Update<ORMultiMap<String, String>>(usersKey, ORMultiMap.create(), writeMajority, data -> data);
        replicator.tell(update, self());
    }

    private void registerUser(User user) {
        // commit to register the user
        Update<ORMultiMap<String, String>> update = new Update<ORMultiMap<String, String>>(usersKey, ORMultiMap.create(), writeAll, data -> data.put(node, user.getUsername(), new HashSet<>(Arrays.asList(user.getSecret()))));
        replicator.tell(update, self());
        getUsers();
    }

    private void initializeActMessages() {
        Update<ORMultiMap<Integer, JSONObject>> update = new Update<ORMultiMap<Integer, JSONObject>>(activityMessagesKey, ORMultiMap.create(), writeMajority, data -> data);
        replicator.tell(update, self());
    }
    private void addActMessage(JSONObject obj) {
        Integer id = (int) (long) obj.get("id");
        Update<ORMultiMap<Integer, JSONObject>> update = new Update<ORMultiMap<Integer, JSONObject>>(activityMessagesKey, ORMultiMap.create(), writeMajority, data -> data.put(node, id, new HashSet<>(Arrays.asList(obj))));
        replicator.tell(update, self());
    }

    private void receiveActMessages(Map<Integer, Set<JSONObject>> map) {
        SortedSet<Integer> ids = new TreeSet<>(map.keySet()); // sorted message IDs

        for (Integer id : ids) {
            JSONObject obj = map.get(id).iterator().next();
            if (!ClientManager.checkDeliveredActMessageId(id)) {
                log.info("Broadcasting activity message: " + obj.toString());
                ClientManager.relayActivityBroadcastToClients(obj);
                ClientManager.addDeliveredActMessageId(id);
            }
        }
    }

    @Override
    public void preStart() {
        // subscribe to changes of the replicated values
        replicator.tell(new Subscribe<LWWRegister<JSONObject>>(lowestLoadServerKey, getSelf()), ActorRef.noSender());
        replicator.tell(new Subscribe<ORMultiMap<String, String>>(usersKey, getSelf()), ActorRef.noSender());
        replicator.tell(new Subscribe<ORMultiMap<Integer, JSONObject>>(activityMessagesKey, getSelf()), ActorRef.noSender());
        self().tell("getServerLoad", ActorRef.noSender());
    }
}
