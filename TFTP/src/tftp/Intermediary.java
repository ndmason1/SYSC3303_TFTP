/*
 * Intermediary.java
 * 
 * Author: Nigel Mason
 * Last updated: 07/05/2015
 * 
 * This file was created specifically for the course SYSC 3303.
 * Copyright (C) Nigel Mason, 2015 - All rights reserved
 */

package tftp;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.UnknownHostException;

/**
 * 
 * This class implements a program that relays messages exchanged between a client and a server.
 *
 */
public class Intermediary {
	private DatagramPacket sendPacket, receivePacket;

	private DatagramSocket sendReceiveSocket;
	private DatagramSocket receiveSocket;
	
	private int reqID; // unique identifier for each request and its response

	public Intermediary()
	{
		try {
			receiveSocket = new DatagramSocket(68);
			sendReceiveSocket = new DatagramSocket();			
			
		} catch (SocketException se) {
			se.printStackTrace();
			System.exit(1);
		} 
		
		reqID = 1;
		System.out.println("===== INTERMEDIATE HOST STARTED =====\n");
	}

	public void cleanup() {
		receiveSocket.close();
		sendReceiveSocket.close();
	}

	public void relay()
	{
		while (true) {
			byte data[] = new byte[Util.BUF_SIZE];
			receivePacket = new DatagramPacket(data, data.length);		
	
			// wait for a request from the client
			try {
				System.out.println("Waiting for request to relay...\n");
				receiveSocket.receive(receivePacket);
			} catch (IOException e) {
				System.out.print("IO Exception: likely:");
				System.out.println("Receive Socket Timed Out.\n" + e);
				e.printStackTrace();
				System.exit(1);
			}
			
			System.out.printf("Received request from client (request ID #%d)\n", reqID);
			Util.printPacketInfo(receivePacket, false);
	
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
		
			// send the datagram packet to the server via the sendrecv socket 
			try {
				sendReceiveSocket.send(sendPacket);
			} catch (IOException e) {
				e.printStackTrace();
				System.exit(1);
			}
			
			System.out.println("\nRelaying request to server...");
			Util.printPacketInfo(sendPacket, true);
			
			System.out.println("Request sent.");
			System.out.println("Waiting for response from server...\n");		
	
			byte replyData[] = new byte[Util.BUF_SIZE];
			receivePacket = new DatagramPacket(replyData, replyData.length);
	
			// wait for server's response
			try {			  
				sendReceiveSocket.receive(receivePacket);
			} catch(IOException e) {
				e.printStackTrace();
				System.exit(1);
			}
				
			System.out.printf("Response received for request ID #%d:\n", reqID);
			Util.printPacketInfo(receivePacket, false);
	
			// create send packet from response data
			sendPacket = new DatagramPacket(replyData, receivePacket.getLength(), clientAddr, clientPort);
			
			// create ephemeral socket to send response to client
			DatagramSocket sendSocket = null;
			try {
				sendSocket = new DatagramSocket();
			} catch (SocketException e) {				
				e.printStackTrace();
				System.exit(1);
			}
			
			System.out.println("\nRelaying response to client:");
			Util.printPacketInfo(sendPacket, true);
	
			try {
				sendSocket.send(sendPacket);
			} catch (IOException e) {
				e.printStackTrace();
				System.exit(1);
			}			
			
			sendSocket.close();			
			System.out.println("========================================\n");			
			reqID++;
		}
	}	

	public static void main(String[] args) {
		Intermediary in = new Intermediary();
		try {
			in.relay();
		} finally {
			in.cleanup();
		}
	}
}
