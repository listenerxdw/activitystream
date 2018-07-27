package activitystreamer.server;

public class ServerConnection {
    private Connection conn;
    private String serverId = null;
    private Integer load = 0;

    public ServerConnection(Connection conn) {
        this.conn = conn;
    }

    public Connection getConn() {
        return this.conn;
    }

    public String getServerId() {
        return this.serverId;
    }

    public void setServerId(String serverId) {
        this.serverId = serverId;
    }

    public Integer getLoad() {
        return this.load;
    }

    public void setLoad(Integer load) {
        this.load = load;
    }
}
