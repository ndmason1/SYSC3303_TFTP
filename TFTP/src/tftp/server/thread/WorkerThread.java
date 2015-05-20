package tftp.server.thread;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;

import tftp.Config;
import tftp.Logger;
import tftp.net.ISendReceiver;

public abstract class WorkerThread extends Thread implements ISendReceiver {
	
	/**
	 * The initial request packet.
	 */
	protected DatagramPacket reqPacket;
	
	/**
	 * The packet used to send messages to the client.
	 */
	protected DatagramPacket sendPacket;
	
	/**
	 * The packet used to receive messages from the client.
	 */
	protected DatagramPacket receivePacket;
	
	/**
	 * A socket which is created and used only for the request to which this thread is assigned.
	 */
	protected DatagramSocket sendReceiveSocket;	
	
	/**
	 * InetAddress of the client machine.
	 */
	protected InetAddress clientIP;
	
	/**
	 * Port number of the client process.
	 */
	protected int clientPort;
	
	/**
	 * Logger object used for logging debug/error messages.
	 */
	protected Logger logger;
		
	/**
	 * Constructs a WorkerThread. 
	 *
	 * @param  reqPacket  the packet containing the client's request
	 */
	protected WorkerThread(DatagramPacket reqPacket) {
		this.reqPacket = reqPacket;
		clientIP = reqPacket.getAddress();
		clientPort = reqPacket.getPort();
		
		try {
			sendReceiveSocket = new DatagramSocket();
		} catch (SocketException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		logger = Logger.getInstance();
	}
	
	/**
	 * Closes the resources used by this thread.
	 */
	protected void cleanup() {
		sendReceiveSocket.close();
	}
	
	/**
	 * Overrides Thread's run method (to be implemented by subclasses).
	 */	
	@Override
	public abstract void run();
	
	/**
	 * Returns a path that should be used by a WorkerThread to find files.
	 * 
	 * @param path	the path that the WorkerThread intends to search for a file 
	 * @return		the Server's main directory 
	 */
	@Override
	public String getStoragePath(String path) {		
		return Config.getServerDirectory();
	}


}
