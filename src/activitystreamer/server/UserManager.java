package activitystreamer.server;

import org.json.simple.JSONObject;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

// Class to manage all registered(/ing) users
public class UserManager {
    private static List<User> userStore = new ArrayList<User>();
    private static List<User> loggedInUsers = new ArrayList<User>();
    private static List<User> pendingRegistrations = new ArrayList<User>();
    private static List<User> committedRegistrations = new ArrayList<User>();

    public static User createUser(String username, String secret) {
        User u = new User(username, secret);
        userStore.add(u);
        return u;
    }

    public static void deleteAllUsers() {
        userStore = new ArrayList<User>();
    }

    public static void deleteUser(User user) {
        userStore.remove(user);
    }

    public static void deleteUser(String user) {
        User u = findUser(user);
        if (u!=null) {
            deleteUser(u);
        }
    }

    public static void addRegistration(Connection conn, String username, String secret) {
        User u = new User(username, secret);
        u.setConnection(conn);
        pendingRegistrations.add(u);
        ClusterManager.sendToReplicator("getUsers");
    }

    public static void processRegistrations() {
        for (Iterator<User> iterator = pendingRegistrations.iterator(); iterator.hasNext(); ) {
            User u = iterator.next();
            if (findUser(u.getUsername()) == null) {
                // commit registration due to no conflict
                committedRegistrations.add(u);
                iterator.remove();
                ClusterManager.sendToReplicator(u);
            } else {
                Message.sendRegistrationFailed(u.getConnection(), u.getUsername());
            }
        }
    }

    public static void finalizeRegistrations() {
        for (Iterator<User> iterator = committedRegistrations.iterator(); iterator.hasNext(); ) {
            User u = iterator.next();
            User foundUser = findUser(u.getUsername());
            if (foundUser != null) {
                // register successful
                foundUser.setConnection(u.getConnection());
                iterator.remove();
                Message.sendRegistrationSuccess(u.getConnection(), u.getUsername());
            }
        }
    }

    public static User findRegistration(String username) {
        for (User u : pendingRegistrations) {
            if (u.getUsername().equals(username)) {
                return u;
            }
        }
        return null;
    }

    public static User findUser(Connection conn) {
        for(User u : loggedInUsers) {
            if (u.getConnection() == conn) {
                return u;
            }
        }
        return null;
    }


    public static User findUser(String username) {
        for(User u : userStore) {
            if (u.getUsername().equals(username)) {
                return u;
            }
        }
        return null;
    }

    public static User findUser(String username, String secret) {
        for(User u : userStore) {
            if (u.validateCredentials(username, secret)) {
                return u;
            }
        }
        return null;
    }

    public static User isLoggedInUser(Connection conn) {
        User user = findUser(conn);
        if (user!=null) {
            return user;
        }
        return null;
    }

    public static void logInUser(User user, Connection conn) {
        loggedInUsers.add(user);
        user.setConnection(conn);
    }

    public static void logOutUser(User user) {
        loggedInUsers.remove(user);
        user.setConnection(null);
    }

    public static List<User> getUserStore() {
        return userStore;
    }
    public static List<User> getLoggedInUsers() { return loggedInUsers; }
}
