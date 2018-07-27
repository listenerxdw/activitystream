package activitystreamer.server;

import java.util.HashSet;
import java.util.Set;

// User class, stores properties and connection details
public class User {
    private String username;
    private String secret;
    private Connection conn = null;

    public User(String username, String secret) {
        this.username = username;
        this.secret = secret;
    }

    public String getSecret() {
        return secret;
    }

    public String getUsername() {
        return username;
    }

    /* Only allow validation of credentials and never reveal actual credentials */
    public boolean validateCredentials(String username, String secret) {
        return (this.username.equals(username) && this.secret.equals(secret));
    }

    public Connection getConnection() {
        return conn;
    }

    public void setConnection(Connection conn) {
        this.conn = conn;
    }

}
