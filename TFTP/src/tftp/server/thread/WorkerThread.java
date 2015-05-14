package tftp.server.thread;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;

import tftp.Logger;

public abstract class WorkerThread extends Thread {
	
	protected DatagramPacket reqPacket, sendPacket, receivePacket;
	protected DatagramSocket sendReceiveSocket;	
	protected InetAddress clientIP;
	protected int clientPort;
	protected Logger logger;

	public WorkerThread(DatagramPacket reqPacket) {
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
	
	protected void cleanup() {
		sendReceiveSocket.close();
	}
	
	@Override
	public abstract void run();


}
