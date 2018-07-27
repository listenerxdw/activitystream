package activitystreamer.server;

import activitystreamer.util.Settings;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.simple.JSONObject;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;


// Class to track and manage connected clients, both anonymous and registered users
public class ClientManager {
    private static final Logger log = LogManager.getLogger();
    private static List<Connection> anonClients = new ArrayList<Connection>();
    private static Set<Integer> deliveredActMessageIds = new HashSet<Integer>();

    public static boolean login(Connection conn, JSONObject payload) {

        // do not allow logged in clients to login again
        if (isLoggedInClient(conn)) {
            logout(conn);
            Message.sendInvalidMessage(conn, "your connection is already logged in, now disconnecting");
            return true;
        }

        String username = (String) payload.get("username");
        String secret = (String) payload.get("secret");

        // check username specified
        if (username == null || username.isEmpty()) {
            Message.sendInvalidMessage(conn, "username must be specified");
            return true;
        }

        // allow anonymous login
        if (username.equals("anonymous")) {
            anonClients.add(conn);
            Message.sendLoginSuccess(conn, "anonymous");
            checkForRedirection(conn);
            return false;
        }

        // check secret specified
        if (secret == null || secret.isEmpty()) {
            Message.sendInvalidMessage(conn, "secret must be specified");
            return true;
        }

        // find user and login if successfully registered
        User user = UserManager.findUser(username, secret);
        if (user!=null) {
//                if (user.isLoggedIn()) {
//                    Message.sendLoginFailed(conn, "user already logged in on this server");
//                    return true;
//                } else {
                    UserManager.logInUser(user, conn);
                    Message.sendLoginSuccess(conn, username);
                    checkForRedirection(conn);
                    return false;
//                }
        } else {
            Message.sendLoginFailed(conn, "username and/or secret is invalid");
            return true;
        }
    }

    // register user
    public static boolean register(Connection conn, JSONObject payload) {

        // do not allow register if already registered(/ing)
        if (isCurrentClient(conn)) {
            logout(conn);
            Message.sendInvalidMessage(conn, "your connection is already registering or registered, now disconnecting");
            return true;
        }

        String username = (String) payload.get("username");
        String secret = (String) payload.get("secret");

        // check username specified
        if (username==null || username.isEmpty()) {
            Message.sendInvalidMessage(conn, "username must be specified");
            return true;
        }

        // do not allow registering of username 'anonymous'
        if (username.equals("anonymous")) {
            Message.sendInvalidMessage(conn, "cannot register username 'anonymous'");
            return true;
        }

        // check secret specified
        if (secret==null || secret.isEmpty()) {
            Message.sendInvalidMessage(conn, "secret must be specified");
            return true;
        }

        // check user with that name does not exist
        if (UserManager.findUser(username)!=null) {
            Message.sendRegistrationFailed(conn, username);
            return true;
        // register user otherwise
        } else {
            UserManager.addRegistration(conn, username, secret);
            return false;
        }

    }

    // find all clients that are logged in users
    // cycle through all registered users to find
    // not scalable but simple and sufficient
    public static List<Connection> getLoggedInUserConnections() {
        List<Connection> userConnections = new ArrayList<Connection>();
        for (User user : UserManager.getLoggedInUsers()) {
            if (user.getConnection() != null) {
                userConnections.add(user.getConnection());
            }
        }
        return userConnections;
    }

    // get all NON-anonymous connections
    // ie logged in users and registering(/ed) users
    public static List<Connection> getAllUserConnections() {
        List<Connection> userConnections = new ArrayList<Connection>();
        for (User user : UserManager.getUserStore()) {
            if (user.getConnection()!=null) {
                userConnections.add(user.getConnection());
            }
        }
        return userConnections;
    }

    // return no. logged in clients and anonymous clients
    public static Integer numValidClients() {
        return getLoggedInUserConnections().size() + anonClients.size();
    }

    // is this connection a client
    public static boolean isCurrentClient(Connection inconn) {
        for(Connection conn : getAllUserConnections()) {
            if (conn==inconn) return true;
        }
        for(Connection conn : anonClients) {
            if (conn==inconn) return true;
        }
        return false;
    }

    // is connection currently logged in as user or anon
    public static boolean isLoggedInClient(Connection inconn) {
        for(Connection conn : getLoggedInUserConnections()) {
            if (conn==inconn) return true;
        }
        for(Connection conn : anonClients) {
            if (conn==inconn) return true;
        }
        return false;
    }

    // send activity to all logged in clients
    public static void relayActivityBroadcastToClients(JSONObject payload) {
        for(Connection conn : getLoggedInUserConnections()) {
            Message.relayActivityBroadcast(conn, payload);
        }
        for(Connection conn : anonClients) {
            if (conn != null)
                Message.relayActivityBroadcast(conn, payload);
        }
    }

    // send redirect to connection if applicable
    private static void checkForRedirection(Connection conn) {
        JSONObject bestServer = ServerManager.getLowestLoadServer();
        if (bestServer != null && bestServer.get("id") != Settings.getServerId()) {
            Message.sendRedirect(conn, (String) bestServer.get("hostname"), (int) bestServer.get("port"));
        }
    }

    // logout client
    public static void logout(Connection conn) {
        User user = UserManager.findUser(conn);
        if (user!=null) {
            UserManager.logOutUser(user);
        }
        anonClients.remove(conn);
    }

    // is client logged in as anon
    public static boolean isLoggedInAnon(Connection conn) {
        if (anonClients.contains(conn)) return true;
        return false;
    }

    public static void addDeliveredActMessageId(Integer id) {
        deliveredActMessageIds.add(id);
    }

    public static boolean checkDeliveredActMessageId(Integer id) {
        return deliveredActMessageIds.contains(id);
    }
}
