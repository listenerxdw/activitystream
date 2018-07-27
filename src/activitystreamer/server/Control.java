package activitystreamer.server;

import java.io.IOException;
import java.net.Socket;
import java.util.ArrayList;

import activitystreamer.Client;
import akka.cluster.Cluster;
import com.google.gson.JsonObject;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

import activitystreamer.util.Settings;

public class Control extends Thread {
    private static final Logger log = LogManager.getLogger();
    protected static Control control = null;
    private static ArrayList<Connection> connections;
    private static boolean term = false;
    private static Listener listener;

    public Control() {
        // initialize the connections array
        connections = new ArrayList<Connection>();
        // start a listener
        try {
            log.info(Settings.getClientPort());
            listener = new Listener();
        } catch (IOException e1) {
            log.fatal("failed to startup a listening thread: " + e1);
            System.exit(-1);
        }
    }

    public static Control getInstance() {
        if (control == null) {
            control = new Control();
        }
        return control;
    }

    public void initiateConnection() {
        // make a connection to another server if remote hostname is supplied
        if (Settings.getRemoteHostname() != null) {
//            try {
//                Connection serverConnection = outgoingConnection(new Socket(Settings.getRemoteHostname(), Settings.getRemotePort()));
//                Message.sendAuthenticate(serverConnection, Settings.getSecret());
//                ServerManager.addConnection(serverConnection);
//            } catch (IOException e) {
//                log.error("failed to make connection to " + Settings.getRemoteHostname() + ":" + Settings.getRemotePort() + " :" + e);
//                System.exit(-1);
//            }
        }
    }

    /*
     * Processing incoming messages from the connection.
     * Return true if the connection should close.
     */
    public synchronized boolean process(Connection conn, String msg) {
        JSONParser parser = new JSONParser();
        try {
            JSONObject obj = (JSONObject) parser.parse(msg);
            log.info("received JSON input: " + msg);
            String command = (String) obj.get("command");
            if (command == null) {
                Message.sendInvalidMessage(conn, "the received message did not contain a command");
                return true;
            } else {
                switch(command) {
                    case "ACTIVITY_MESSAGE":
                        return ServerManager.receiveActivityMessage(conn, obj);

                    case "REGISTER":
                        return ClientManager.register(conn, obj);

                    case "LOGIN":
                        return ClientManager.login(conn, obj);

                    case "LOGOUT":
                        ClientManager.logout(conn);
                        return true;

                    default:
                        Message.sendInvalidMessage(conn, "the received command is not supported by this server");
                        ClientManager.logout(conn);
                        return true;
                }
            }

        } catch (ParseException e) {
            log.error("invalid JSON input: " + msg);
            Message.sendInvalidMessage(conn, "JSON parse error while parsing message");
            return true;
        }
    }

    /*
     * The connection has been closed by the other party.
     */
    public synchronized void connectionClosed(Connection con) {
        if (!term) {
            ClientManager.logout(con);
            connections.remove(con);
        }
    }

    /*
     * A new incoming connection has been established, and a reference is returned to it
     */
    public synchronized Connection incomingConnection(Socket s) throws IOException {
        log.debug("incoming connection: " + Settings.socketAddress(s));
        Connection c = new Connection(s, false);
        connections.add(c);
        return c;

    }

    /*
     * A new outgoing connection has been established, and a reference is returned to it
     */
    public synchronized Connection outgoingConnection(Socket s) throws IOException {
        log.debug("outgoing connection: " + Settings.socketAddress(s));
        Connection c = new Connection(s, true);
        connections.add(c);
        return c;

    }

    @Override
    public void run() {
        initiateConnection();
        log.info("using activity interval of " + Settings.getActivityInterval() + " milliseconds");
        while (!term) {
            // do something with 5 second intervals in between
            try {
                Thread.sleep(Settings.getActivityInterval());
            } catch (InterruptedException e) {
                log.info("received an interrupt, system is shutting down");
                break;
            }
            if (!term) {
                term = doActivity();
            }

        }
        log.info("closing " + connections.size() + " connections");
        // clean up
        for (Connection connection : connections) {
            connection.closeCon();
        }
        listener.setTerm(true);
    }

    public boolean doActivity() {
        ClusterManager.sendToReplicator("getServerLoad");
        return false;
    }

    public final void setTerm(boolean t) {
        term = t;
    }

    public final ArrayList<Connection> getConnections() {
        return connections;
    }
}
