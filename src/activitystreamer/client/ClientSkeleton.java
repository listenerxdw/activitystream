package activitystreamer.client;

import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import activitystreamer.util.Settings;
import org.json.simple.parser.ParseException;

public class ClientSkeleton extends Thread {
	private static final Logger log = LogManager.getLogger();
	private static ClientSkeleton clientSolution;
	private static ClientConnection clientConn;
	private TextFrame textFrame;
	private boolean isLoggedIn = false;

	public static ClientSkeleton getInstance() {
		if(clientSolution==null){
			clientSolution = new ClientSkeleton();
		}
		return clientSolution;
	}
	
	public ClientSkeleton() {
		textFrame = new TextFrame();

		initiateConnection(Settings.getRemoteHostname(),Settings.getRemotePort());

		if (Settings.getUsername().equals("anonymous")) {
			sendInitialLogin();
		} else if (Settings.getSecret()==null) {
			Settings.setSecret(Settings.nextSecret());
			log.info("No User secret given, set secret "+Settings.getSecret()+" for it.");
			sendInitialRegistration();
		}
		else
			sendInitialRegistration();
		start();
	}

	public void initiateConnection(String remoteHostname,int remotePort) {
		if (Settings.getRemoteHostname() !=null) {
			try {
				Socket socket = new Socket(remoteHostname, remotePort);
				clientConn = new ClientConnection(socket);
			} catch (IOException e) {
				log.error("failed to make connection to " + Settings.getRemoteHostname() + ":" + Settings.getRemotePort() + " :" + e);
				System.exit(-1);
			}
		}
		else{
			log.error("No RemoteHost Information found in Command Line arguments.");
			System.exit(-1);
		}
	}

	public void sendActivityObject(JSONObject activityObj){
		JSONObject obj = new JSONObject();
		obj.put("command", "ACTIVITY_MESSAGE");
		obj.put("username",Settings.getUsername());
		obj.put("secret",Settings.getSecret());
		obj.put("activity",activityObj);
		clientConn.writeMsg(obj.toJSONString());
	}


	public void run(){
	}


	public void process(ClientConnection conn, String msg) {
		JSONParser parser = new JSONParser();
		try {
			JSONObject obj = (JSONObject) parser.parse(msg);
			textFrame.setOutputText(obj);
			log.info("received JSON input from Server: " + msg);
			String command = (String) obj.get("command");
			switch (command) {
				case "AUTHENTICATION_FAIL":
					isLoggedIn = false;
					break;
				case "LOGIN_FAILED":
					isLoggedIn = false;
					break;
				case "INVALID_MESSAGE":
					conn.setTerm(true);
					break;
				case "REDIRECT":
					Settings.setRemoteHostname((String) obj.get("hostname"));
					Settings.setRemotePort((int) (long)obj.get("port"));
					initiateConnection(Settings.getRemoteHostname(), Settings.getRemotePort());
					sendInitialLogin();
					conn.setTerm(true);
					break;
				case "REGISTER_FAILED":
					initiateConnection(Settings.getRemoteHostname(), Settings.getRemotePort());
					sendInitialLogin();
					conn.setTerm(true);
					break;
				case "REGISTER_SUCCESS":
					sendInitialLogin();
					break;
				case "LOGIN_SUCCESS":
					isLoggedIn = true;
				default:
					log.info("Received command: " + command);
			}
		} catch (ParseException e) {
			log.error("invalid JSON input: " + msg);
		}
	}

	public void disconnect() {
		if (isLoggedIn) {
			sendLogout();
		}
		clientConn.closeCon();
	}

	public void sendLogout() {
		JSONObject obj = new JSONObject();
		obj.put("command", "LOGOUT");
		clientConn.writeMsg(obj.toJSONString());
	}

	public void sendInitialRegistration(){
		JSONObject obj = new JSONObject();
		obj.put("command", "REGISTER");
		obj.put("username",Settings.getUsername());
		obj.put("secret",Settings.getSecret());
		clientConn.writeMsg(obj.toJSONString());
	}

	public void sendInitialLogin(){
		JSONObject obj = new JSONObject();
		obj.put("command", "LOGIN");
		obj.put("username",Settings.getUsername());
		obj.put("secret",Settings.getSecret());
		clientConn.writeMsg(obj.toJSONString());
	}

	
}
