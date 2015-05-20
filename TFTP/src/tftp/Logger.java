/*
 * Logger.java
 * 
 * Authors: TEAM 1
 * 
 * This file was created specifically for the course SYSC 3303.
 */

package tftp;

import java.io.BufferedWriter;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.net.DatagramPacket;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.concurrent.ConcurrentLinkedQueue;

import javax.naming.ConfigurationException;

public class Logger {
		
	/*
	 * Values taken from http://stackoverflow.com/questions/7745885/log4j-logging-hierarchy-order 
	 */
	public enum LogLevel {
		FATAL(50000),
		ERROR(40000),
		WARN(30000),
		INFO(20000),
		DEBUG(10000),
		TRACE(5000);
	
		public int value;
		
		private LogLevel(int value) {
			this.value = value;
		}
	}

	private LogLevel level;	
	private String logFilename;
	private ConcurrentLinkedQueue<String> msgQueue;
	
	private final int MAX_QUEUE_SIZE = 1; // TODO: fix class so we can have a bigger queue size
	
	private static Logger instance = null;
	
	private Logger() {
		try {
			level = Config.getLogLevel();
		} catch (ConfigurationException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		logFilename = "tftp_log_unknown"; // this should be set by the log user after setting up the Logger
		
		
		msgQueue = new ConcurrentLinkedQueue<String>();
	}
	
	public static Logger getInstance() {
		if (instance == null)
			instance = new Logger();
		return instance;
	}
	
	public void fatal(String msg) {
		// fatal errors always reported
		enqueue("[FATAL] " + msg);
	}
	
	public void error(String msg) {
		if (level.value <= LogLevel.ERROR.value)
			enqueue("[ERROR] " + msg);
	}
	
	public void warn(String msg) {
		if (level.value <= LogLevel.WARN.value)
			enqueue("[WARN] " + msg);
	}
	
	public void info(String msg) {
		if (level.value <= LogLevel.INFO.value)
			enqueue("[INFO] " + msg);
	}
	
	public void debug(String msg) {
		if (level.value <= LogLevel.DEBUG.value)		
			enqueue("[DEBUG] " + msg);
	}
	
	public void trace(String msg) {
		if (level.value <= LogLevel.TRACE.value)
			enqueue("[TRACE] " + msg);
	}
	
	public void logPacketInfo(DatagramPacket packet, boolean sent) {
		if (level.value <= LogLevel.DEBUG.value) {
			String addrLabel = sent ? "Destination" : "Source";
			enqueue("==============================");
			enqueue(String.format("Packet length: %d\n", packet.getLength()));
			enqueue(String.format("%s address: %s:%d\n", addrLabel, packet.getAddress().getHostAddress(),
					packet.getPort()));
			enqueue(String.format("First four bytes: %02x %02x %02x %02x\n", packet.getData()[0], packet.getData()[1],
					packet.getData()[2], packet.getData()[3]));
			enqueue("==============================");
		}
		
	}
	
	private void enqueue(String msg) {		
		msgQueue.add(msg);
		if (msgQueue.size() == MAX_QUEUE_SIZE) {
			flushMessages();
		}
	}
	
	/* 
	 * Opens the log file and writes all messages in the queue.
	 * The try-with-resource statement ensures that the file is closed after writing.
	 */
	public void flushMessages() {
		// TODO: fix so this appends rather than overwrites current log
		try (Writer writer = new BufferedWriter(new OutputStreamWriter(
				new FileOutputStream(logFilename, true), "utf-8"))) {			
			while (!msgQueue.isEmpty()) {				
				writer.write(msgQueue.poll()+"\n");				
			}
			writer.flush();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

	}

	public void setLabel(String label) {
		// assuming our working directory is top level project directory
		// name log file with timestamp so new ones are created each run
		StringBuilder sb = new StringBuilder();
		sb.append("log/tftp_");
		sb.append(label);
		sb.append("_log_");
		sb.append(new SimpleDateFormat("yyyyMMddhhmm'.txt'").format(new Date()));
		
		logFilename = sb.toString();
	}

}
