/*
 * Client.java
 * 
 * Author: Nigel Mason
 * Last updated: 07/05/2015
 * 
 * This file was created specifically for the course SYSC 3303.
 * Copyright (C) Nigel Mason, 2015 - All rights reserved
 */

package tftp.client;

import java.io.File;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.UnknownHostException;


import tftp.Logger;
import tftp.Util;
import tftp.net.PacketUtil;
import tftp.net.Receiver;
import tftp.net.Sender;

/**
 * 
 * This class implements a client program that sends TFTP connection requests to a server.
 *
 */
public class Client{	 

	private DatagramSocket sendReceiveSocket;
	private DatagramPacket sendPacket, receivePacket;
	private Logger logger;
	
	public Client() {
		try {
			sendReceiveSocket = new DatagramSocket();
		} catch (SocketException se) {
			se.printStackTrace();
			System.exit(1);
		}

		logger = Logger.getInstance();				
	}

	public void cleanup() {
		sendReceiveSocket.close();
	}

	private byte[] prepareReadRequestPayload(String filename, String mode) {		

		int msgLength = filename.length() + mode.length() + 4; 
		byte msg[] = new byte[msgLength];

		// preamble
		msg[0] = 0x00;
		msg[1] = 0x01;

		// filename
		byte[] fbytes = filename.getBytes(); 
		System.arraycopy(fbytes, 0, msg, 2, fbytes.length);
		msg[fbytes.length + 2] = 0x00;

		// mode
		byte[] mbytes = mode.getBytes(); 
		System.arraycopy(mbytes, 0, msg, 3+fbytes.length, mbytes.length);
		msg[fbytes.length + mbytes.length + 3] = 0x00;

		return msg;
	}

	private byte[] prepareWriteRequestPayload(String filename, String mode) {

		int msgLength = filename.length() + mode.length() + 4; 
		byte msg[] = new byte[msgLength];

		// preamble
		msg[0] = 0x00;
		msg[1] = 0x02;

		// filename
		byte[] fbytes = filename.getBytes(); 
		System.arraycopy(fbytes, 0, msg, 2, fbytes.length);
		msg[fbytes.length + 2] = 0x00;

		// mode
		byte[] mbytes = mode.getBytes(); 
		System.arraycopy(mbytes, 0, msg, 3+fbytes.length, mbytes.length);
		msg[fbytes.length + mbytes.length + 3] = 0x00;

		return msg;
	}	

	public void sendReadRequest(String filename, String mode) {

		logger.info(String.format("Starting read of file %s from server...", filename));

		byte[] payload = prepareReadRequestPayload(filename, mode);		

		// create send packet
		try {
			sendPacket = new DatagramPacket(payload, payload.length, InetAddress.getLocalHost(), 69);
		} catch (UnknownHostException e) {
			e.printStackTrace();
			System.exit(1);
		}

		logger.logPacketInfo(sendPacket, true);


		try {
			sendReceiveSocket.send(sendPacket);
		} catch (IOException e) {
			e.printStackTrace();
			System.exit(1);
		}

		byte data[] = new byte[Util.BUF_SIZE];
		receivePacket = new DatagramPacket(data, data.length);		

		try {			  
			sendReceiveSocket.receive(receivePacket);
			
		} catch(IOException e) {
			e.printStackTrace();
			System.exit(1);
		}

		// TODO: verify data packet
		// assume our request is good, set up a receiver to proceed with the transfer
		Receiver r = new Receiver(sendReceiveSocket,receivePacket.getPort());
		r.receiveFile(receivePacket);		
	}

	public void sendWriteRequest(String filename, String mode) {
		logger.info(String.format("Starting write of file %s from server...", filename));

		byte[] payload = prepareWriteRequestPayload(filename, mode);

		// create send packet
		try {
			sendPacket = new DatagramPacket(payload, payload.length, InetAddress.getLocalHost(), 69);
		} catch (UnknownHostException e) {
			e.printStackTrace();
			System.exit(1);
		}

		logger.logPacketInfo(sendPacket, true);

		// send packet to server
		try {
			sendReceiveSocket.send(sendPacket);
		} catch (IOException e) {
			e.printStackTrace();
			System.exit(1);
		}


		// create recv packet
		byte data[] = new byte[PacketUtil.BUF_SIZE];
		receivePacket = new DatagramPacket(data, data.length);

		try {			  
			sendReceiveSocket.receive(receivePacket);
			
		} catch(IOException e) {
			e.printStackTrace();
			System.exit(1);
		}
		logger.logPacketInfo(receivePacket, false);

		// TODO: verify ack packet


		// assume our request is good, set up a sender to proceed with the transfer

		Sender s = new Sender(sendReceiveSocket,receivePacket.getPort());
		try {
			s.sendFile(new File(filename));
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	public static void main(String[] args) {
		Client c = new Client();

		//		c.sendReadRequest("etc/test_a_0B.txt", "octet");
		//		c.sendReadRequest("etc/test_b_40B.txt", "octet");
		//		c.sendReadRequest("etc/test_c_512B.txt", "octet");
		//		c.sendReadRequest("etc/test_d_984B.txt", "octet");
		//		c.sendReadRequest("etc/test_e_15MB.jar", "octet");

		c.sendWriteRequest("etc/test_a_0B.txt", "octet");
		c.sendWriteRequest("etc/test_b_40B.txt", "octet");
		c.sendWriteRequest("etc/test_c_512B.txt", "octet");

		c.cleanup();
	}
}



