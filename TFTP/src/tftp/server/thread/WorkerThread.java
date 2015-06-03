/*
 * WorkerThread.java
 * 
 * Authors: TEAM 1
 * 
 * This file was created specifically for the course SYSC 3303.
 */

package tftp.server.thread;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;

import tftp.net.PacketParser;
import tftp.sim.ErrorSimulator;

public abstract class WorkerThread extends Thread {
	
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
	protected int defaultTimeout;
	
	protected PacketParser packetParser;
	
	protected static int id = 1;
	
	protected String directory;
	/**
	 * Constructs a WorkerThread. 
	 *
	 * @param  reqPacket  the packet containing the client's request
	 */
	protected WorkerThread(String name, DatagramPacket reqPacket) {
		super(name);
		this.reqPacket = reqPacket;
		clientIP = reqPacket.getAddress();
		clientPort = reqPacket.getPort();
		setDefaultTimeout(ErrorSimulator.TIMEOUT_MS);

		try {
			sendReceiveSocket = new DatagramSocket();
			sendReceiveSocket.setSoTimeout(getDefaultTimeout());
			
		} catch (SocketException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		packetParser = new PacketParser(reqPacket.getAddress(), reqPacket.getPort());
	}
	
	/**
	 * Closes the resources used by this thread.
	 */
	protected void cleanup() {
		sendReceiveSocket.close();
	}
	
	/**
	 * Prints a message to standard output which is prepended with this thread's name.
	 */
	protected void printToConsole(String message) {
		System.out.printf("%s: %s\n", this.getName(), message);
	}
		
	/**
	 * Overrides Thread's run method (to be implemented by subclasses).
	 */	
	
	//get functions
	protected int getDefaultTimeout(){return defaultTimeout;}
	
	//set functions
	protected void setDefaultTimeout(int aDefaultTimeout){defaultTimeout = aDefaultTimeout;}
	@Override
	public abstract void run();
	
	public void setDirectory(String Directory){this.directory = Directory;}
	

}
