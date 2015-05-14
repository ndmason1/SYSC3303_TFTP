/*
 * Server.java
 * 
 * Author: Nigel Mason
 * Last updated: 07/05/2015
 * 
 * This file was created specifically for the course SYSC 3303.
 * Copyright (C) Nigel Mason, 2015 - All rights reserved
 */

package tftp.server;

import java.io.File;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;

import tftp.exception.InvalidRequestException;
import tftp.exception.TFTPPacketException;
import tftp.Logger;
import tftp.Util;
import tftp.net.PacketUtil;
import tftp.net.Receiver;
import tftp.net.Sender;
import tftp.server.thread.WorkerThreadFactory;

/**
 * 
 * This class implements a TFTP server.
 *
 */
public class Server {
	private DatagramPacket receivePacket;
	private DatagramSocket receiveSocket;
	
	private WorkerThreadFactory threadFactory;
	private Logger logger;
	
	public Server()
	{
		try {
			receiveSocket = new DatagramSocket(69);
		} catch (SocketException se) {
			se.printStackTrace();
			System.exit(1);
		}
		
		threadFactory = new WorkerThreadFactory();		
		logger = Logger.getInstance();
	}

	public void cleanup() {
		receiveSocket.close();
	}

	public void serveRequests()
	{
		while(true) {
			byte data[] = new byte[PacketUtil.BUF_SIZE];
			receivePacket = new DatagramPacket(data, data.length);			
			receivePacket.getLength();

			// wait for request to come  in
			try {
				receiveSocket.receive(receivePacket);
			} catch (IOException e) {
				logger.log("IO Exception: likely:");
				logger.log("Receive Socket Timed Out.\n" + e);
				e.printStackTrace();
				System.exit(1);
			}			
			
			logger.log("Request received. Creating handler thread...");
			// spawn a thread to process request
			try {
				Thread worker = threadFactory.createWorkerThread(receivePacket);
				worker.start();
			} catch (TFTPPacketException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			
						
			//processRequest(receivePacket);
		}
	}

//	private void processRequest(DatagramPacket reqPacket) {
//		byte[] data = reqPacket.getData();
//		// check for valid request format
//		if (data[0] != 0x00) 
//			throw new InvalidRequestException("corrupt data at byte 0");
//
//		if (data[1] < 1 || data[1] > 4)
//			throw new InvalidRequestException("bad op code");
//		
//		try {
//			switch(data[1]) {
//			case 1:				
//				processReadRequest(reqPacket);				
//				break;
//			case 2:				
//				processWriteRequest(reqPacket);				
//				break;
//			default:
//				
//			}
//		} catch (SocketException e) {
//			// TODO Auto-generated catch block
//			e.printStackTrace();
//		}
//	}
//	
//	private void processReadRequest(DatagramPacket reqPacket) throws SocketException {
//		
//		byte[] data = reqPacket.getData();
//		
//		// validate file name		
//		int i = 2;
//		StringBuilder sb = new StringBuilder();
//		while (data[i] != 0x00) {
//			sb.append((char)data[i]);
//			// reject non-printable values
//			if (data[i] < 0x20 || data[i] > 0x7F)
//				throw new InvalidRequestException(
//						String.format("non-printable data inside file name: byte %d",i));			
//			i++;
//		}
//		String filename = sb.toString();
//
//		// move index to start of mode string
//		i++;		
//
//		// validate mode string
//		sb = new StringBuilder();
//		while (data[i] != 0x00) {
//			sb.append((char)data[i]);			
//			i++;
//		}
//
//		String mode = sb.toString();
//		if (! (mode.toLowerCase().equals("netascii") || mode.toLowerCase().equals("octet")) )
//			throw new InvalidRequestException("invalid mode");		
//
//		// should be at end of packet
//		if (i+1 != reqPacket.getLength())
//			throw new InvalidRequestException("incorrect packet length");
//
//		// request is good if we made it here
//		// read request, so start a file transfer
//		Sender s = new Sender(new DatagramSocket(), reqPacket.getPort());
//		try {			
//			s.sendFile(new File(filename));
//		} catch (IOException e) {
//			// TODO Auto-generated catch block
//			e.printStackTrace();
//		}
//	}
//	
//	private void processWriteRequest(DatagramPacket reqPacket) throws SocketException {
//		
//		byte[] data = reqPacket.getData();
//		
//		// validate file name		
//		int i = 2;
//		StringBuilder sb = new StringBuilder();
//		while (data[i] != 0x00) {
//			sb.append((char)data[i]);
//			// reject non-printable values
//			if (data[i] < 0x20 || data[i] > 0x7F)
//				throw new InvalidRequestException(
//						String.format("non-printable data inside file name: byte %d",i));			
//			i++;
//		}
//		String filename = sb.toString();
//
//		// move index to start of mode string
//		i++;		
//
//		// validate mode string
//		sb = new StringBuilder();
//		while (data[i] != 0x00) {
//			sb.append((char)data[i]);			
//			i++;
//		}
//
//		String mode = sb.toString();
//		if (! (mode.toLowerCase().equals("netascii") || mode.toLowerCase().equals("octet")) )
//			throw new InvalidRequestException("invalid mode");		
//
//		// should be at end of packet
//		if (i+1 != reqPacket.getLength())
//			throw new InvalidRequestException("incorrect packet length");
//
//		// request is good if we made it here
//		// write request, so send an ACK 0
//		DatagramSocket sendRecvSocket = null;
//		try {
//			sendRecvSocket = new DatagramSocket();
//		} catch (SocketException se) {
//			se.printStackTrace();
//			System.exit(1);
//		}
//		
//		PacketUtil packetUtil = new PacketUtil(reqPacket.getAddress(), reqPacket.getPort());
//		DatagramPacket initAck = packetUtil.formAckPacket(0);
//		
//		logger.printPacketInfo(initAck, true);
//		
//		try {
//			sendRecvSocket.send(initAck);
//		} catch (IOException e) {
//			// TODO Auto-generated catch block
//			e.printStackTrace();
//		}
//		
//		// get the first data packet so we can set up receiver
//		try {
//			sendRecvSocket.receive(receivePacket);
//		} catch (IOException e) {
//			e.printStackTrace();
//		}
//		
//		logger.printPacketInfo(receivePacket, false);
//		
//		Receiver r = new Receiver(sendRecvSocket, receivePacket.getPort());
//		r.receiveFile(receivePacket);
//		
//	}

	public static void main( String args[] )
	{
		Server s = new Server();
		try {
			s.serveRequests();
		} finally {
			s.cleanup();
		}
	}
}
