package activitystreamer.server;

import activitystreamer.Server;
import activitystreamer.util.Settings;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.simple.JSONObject;
import scala.util.parsing.json.JSON;

import java.util.ArrayList;
import java.util.List;

public class ServerManager {
    private static final Logger log = LogManager.getLogger();
    private static JSONObject lowestLoadServer;

    public static void setLowestLoadServer(JSONObject obj) {
        lowestLoadServer = obj;
    }

    public static JSONObject getLowestLoadServer() {
        return lowestLoadServer;
    }

    public static boolean receiveActivityMessage(Connection conn, JSONObject payload) {
        String username = (String) payload.get("username");
        String secret = (String) payload.get("secret");

        // check if connection is a logged in one
        User user = UserManager.isLoggedInUser(conn);

        // if so, validate message credentials
        if (user!=null) {
            if (username==null || secret==null || username.isEmpty() || secret.isEmpty()) {
                Message.sendInvalidMessage(conn, "username and secret must be specified");
                ClientManager.logout(conn);
                return true;
            }
            if (!user.validateCredentials(username,secret)) {
                Message.sendAuthenticationFail(conn, "username and/or secret does not match connection");
                ClientManager.logout(conn);
                return true;
            }
        } else if (username.equals("anonymous")) {
            if (!ClientManager.isLoggedInAnon(conn)) {
                Message.sendAuthenticationFail(conn, "username and/or secret does not match connection");
                return true;
            }
        } else {
            Message.sendAuthenticationFail(conn, "user not logged in");
            return true;
        }

        // check activity exists
        Object actObj = payload.get("activity");
        if (actObj==null) {
            Message.sendInvalidMessage(conn, "'activity' must be specified");
            ClientManager.logout(conn);
            return true;
        }

        // construct message and broadcast
        JSONObject obj = new JSONObject();
        obj.put("id", System.currentTimeMillis());
        obj.put("command", "ACTIVITY_BROADCAST");
        obj.put("activity", actObj);
        ClusterManager.sendToReplicator(obj);
        //ClientManager.relayActivityBroadcastToClients(obj);
        return false;
    }
}
