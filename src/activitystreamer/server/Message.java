package activitystreamer.server;

import org.json.simple.JSONObject;

public class Message {
    private JSONObject payload;

    public Message(JSONObject payload) {
        this.payload = payload;
    }

    public static void sendInvalidMessage(Connection conn, String info) {
        JSONObject obj = new JSONObject();
        obj.put("command", "INVALID_MESSAGE");
        obj.put("info", info);
        Message message = new Message(obj);
        conn.writeMsg(message.encodeJSON());
    }

    public static void sendAuthenticationFail(Connection conn, String info) {
        JSONObject obj = new JSONObject();
        obj.put("command", "AUTHENTICATION_FAIL");
        obj.put("info", info);
        Message message = new Message(obj);
        conn.writeMsg(message.encodeJSON());
    }

    public static void sendLoginFailed(Connection conn, String info) {
        JSONObject obj = new JSONObject();
        obj.put("command", "LOGIN_FAILED");
        obj.put("info", info);
        Message message = new Message(obj);
        conn.writeMsg(message.encodeJSON());
    }

    public static void sendLoginSuccess(Connection conn, String username) {
        JSONObject obj = new JSONObject();
        obj.put("command", "LOGIN_SUCCESS");
        obj.put("info", "logged in as user " + username);
        Message message = new Message(obj);
        conn.writeMsg(message.encodeJSON());
    }

    public static void sendRegistrationFailed(Connection conn, String username) {
        JSONObject obj = new JSONObject();
        obj.put("command", "REGISTER_FAILED");
        obj.put("info", username + " is already registered with the system");
        Message message = new Message(obj);
        conn.writeMsg(message.encodeJSON());
    }

    public static void sendLockRequest(Connection conn, String username, String secret) {
        JSONObject obj = new JSONObject();
        obj.put("command", "LOCK_REQUEST");
        obj.put("username", username);
        obj.put("secret", secret);
        Message message = new Message(obj);
        conn.writeMsg(message.encodeJSON());
    }

    public static void sendLockAllow(Connection conn, String username, String secret) {
        JSONObject obj = new JSONObject();
        obj.put("command", "LOCK_ALLOWED");
        obj.put("username", username);
        obj.put("secret", secret);
        Message message = new Message(obj);
        conn.writeMsg(message.encodeJSON());
    }

    public static void sendLockDeny(Connection conn, String username, String secret) {
        JSONObject obj = new JSONObject();
        obj.put("command", "LOCK_DENIED");
        obj.put("username", username);
        obj.put("secret", secret);
        Message message = new Message(obj);
        conn.writeMsg(message.encodeJSON());
    }

    public static void sendRegistrationSuccess(Connection conn, String username) {
        JSONObject obj = new JSONObject();
        obj.put("command", "REGISTER_SUCCESS");
        obj.put("info", "register success for " + username);
        Message message = new Message(obj);
        conn.writeMsg(message.encodeJSON());
    }

    public static void sendServerAnnounce(Connection conn, String id, Integer load, String hostname, Integer port) {
        JSONObject obj = new JSONObject();
        obj.put("command", "SERVER_ANNOUNCE");
        obj.put("id", id);
        obj.put("load", load);
        obj.put("hostname", hostname);
        obj.put("port", port);
        Message message = new Message(obj);
        conn.writeMsg(message.encodeJSON());
    }

    public static void relayServerAnnounce(Connection conn, JSONObject payload) {
        Message message = new Message(payload);
        conn.writeMsg(message.encodeJSON());
    }

    public static void relayActivityBroadcast(Connection conn, JSONObject payload) {
        Message message = new Message(payload);
        conn.writeMsg(message.encodeJSON());
    }

    public static void sendRedirect(Connection conn, String hostname, Integer port) {
        JSONObject obj = new JSONObject();
        obj.put("command", "REDIRECT");
        obj.put("hostname", hostname);
        obj.put("port", port);
        Message message = new Message(obj);
        conn.writeMsg(message.encodeJSON());
    }

    public static void sendAuthenticate(Connection conn, String secret) {
        JSONObject obj = new JSONObject();
        obj.put("command", "AUTHENTICATE");
        obj.put("secret", secret);
        Message message = new Message(obj);
        conn.writeMsg(message.encodeJSON());
    }

    private String encodeJSON() {
        return payload.toJSONString();
    }
}
