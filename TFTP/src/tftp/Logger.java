package tftp;

import java.net.DatagramPacket;

public class Logger {

	private boolean debug;
	private static Logger instance = null;
	
	private Logger() {
		debug = Config.getDebug();
	}
	
	public static Logger getInstance() {
		if (instance == null)
			instance = new Logger();
		return instance;
	}
	
	public void log(String msg) {
		if (debug)
			System.out.println(msg);
	}
	
	public void printPacketInfo(DatagramPacket packet, boolean sent) {
		if (debug) {
			Util.printPacketInfo(packet, sent);
		}
	}
	

}
