package activitystreamer.server;

public class KnownServer {
    private String serverId = null;
    private Integer load = 0;
    private String hostName = null;
    private Integer port = 0;


    public KnownServer(String serverId) {
        this.serverId = serverId;
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

    public String getHostName() { return this.hostName; }

    public void setHostName(String hostName) { this.hostName = hostName; }

    public Integer getPort() { return this.port; }

    public void setPort(Integer port) { this.port = port; }
}
