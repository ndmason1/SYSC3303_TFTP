/*
 * Intermediary.java
 * TEAM 1
 * 
 * Last updated: 07/05/2015
 * 
 * This file was created specifically for the course SYSC 3303.
 * 
 */

package tftp.sim;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.UnknownHostException;

import tftp.ILogUser;
import tftp.Logger;
import tftp.Util;
import tftp.net.PacketUtil;

/**
 * 
 * This class implements a an error simulator that can modify messages exchanged 
 * between a TFTP client and server.
 *
 */
public class ErrorSimulator implements ILogUser {
	private DatagramPacket sendPacket, receivePacket;

	private DatagramSocket sendReceiveSocket;
	private DatagramSocket receiveSocket;
	private Logger logger;

	public ErrorSimulator()
	{
		try {
			receiveSocket = new DatagramSocket(68);
			sendReceiveSocket = new DatagramSocket();			
			
		} catch (SocketException se) {
			se.printStackTrace();
			System.exit(1);
		}
		
		logger = Logger.getInstance();
		System.out.println("===== ERROR SIMULATOR STARTED =====\n");
	}

	public void cleanup() {
		receiveSocket.close();
		sendReceiveSocket.close();
	}

	// relay packets unmodified
	public void relay()
	{
		while (true) {
			byte data[] = new byte[PacketUtil.BUF_SIZE];
			receivePacket = new DatagramPacket(data, data.length);		
	
			// wait for a request from the client
			try {				
				receiveSocket.receive(receivePacket);
			} catch (IOException e) {
				System.out.print("IO Exception: likely:");
				System.out.println("Receive Socket Timed Out.\n" + e);
				e.printStackTrace();
				System.exit(1);
			}
			
			logger.debug(String.format("received packet from client at address %s:%d", 
					receivePacket.getAddress().toString(), receivePacket.getPort()) );
			logger.logPacketInfo(receivePacket, false);
	
			byte[] reqData = receivePacket.getData();
			InetAddress clientAddr = receivePacket.getAddress();
			int clientPort = receivePacket.getPort();
			int reqLength = receivePacket.getLength();
	
			// create send packet from request data
			try {
				sendPacket = new DatagramPacket(reqData, reqLength, InetAddress.getLocalHost(), 69);
			} catch (UnknownHostException e) {
				e.printStackTrace();
				System.exit(1);
			}
			
			logger.debug(String.format("sending packet to server at address %s:%d", 
					sendPacket.getAddress().toString(), sendPacket.getPort()) );
			logger.logPacketInfo(receivePacket, true);
		
			// send the datagram packet to the server via the sendrecv socket 
			try {
				sendReceiveSocket.send(sendPacket);
			} catch (IOException e) {
				e.printStackTrace();
				System.exit(1);
			}		
	
			data = new byte[PacketUtil.BUF_SIZE];
			receivePacket = new DatagramPacket(data, data.length);
	
			// wait for server's response
			try {			  
				sendReceiveSocket.receive(receivePacket);
			} catch(IOException e) {
				e.printStackTrace();
				System.exit(1);
			}
				
			logger.debug(String.format("received packet from server at address %s:%d", 
					receivePacket.getAddress().toString(), receivePacket.getPort()) );
			logger.logPacketInfo(receivePacket, false);
	
			// create send packet from response data
			sendPacket = new DatagramPacket(data, receivePacket.getLength(), clientAddr, clientPort);
			
			// create ephemeral socket to send response to client
			DatagramSocket sendSocket = null;
			try {
				sendSocket = new DatagramSocket();
			} catch (SocketException e) {				
				e.printStackTrace();
				System.exit(1);
			}
			
			logger.debug(String.format("sending packet to client at address %s:%d", 
					sendPacket.getAddress().toString(), sendPacket.getPort()) );
			logger.logPacketInfo(receivePacket, true);
	
			try {
				sendSocket.send(sendPacket);
			} catch (IOException e) {
				e.printStackTrace();
				System.exit(1);
			}			
			
			sendSocket.close();
		}
	}
	
	public static void main(String[] args) {
		Logger.getInstance().setLabel("errsim");
		ErrorSimulator in = new ErrorSimulator();
		try {
			in.relay();
		} finally {
			in.cleanup();
		}
	}

	@Override
	public String getLogLabel() {
		// TODO Auto-generated method stub
		return null;
	}
}
