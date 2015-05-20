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
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.UnknownHostException;



import java.nio.file.AccessDeniedException;
import java.nio.file.FileAlreadyExistsException;

import javax.annotation.processing.FilerException;

import tftp.ILogUser;
import tftp.Logger;
import tftp.Util;
import tftp.net.PacketUtil;
import tftp.net.Receiver;
import tftp.net.ISendReceiver;
import tftp.net.Sender;

/**
 * 
 * This class implements a client program that sends TFTP connection requests to a server.
 *
 */
public class Client implements ILogUser, ISendReceiver {	 

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
	
	public void checkValidReadOperation(String path) throws AccessDeniedException, FileAlreadyExistsException {
		
		File theFile = new File(path);
		
		//Checking if the file already exists?
		if (theFile.exists()){
			
			//Checking if user can read the file
			if (!theFile.canWrite()){
				String msg = "cannot write to destination file";
				logger.error(msg);
				throw new AccessDeniedException(msg);
			}
			
			throw new FileAlreadyExistsException("destination file exists");			

		}
		
	}
	
	public void checkValidWriteOperation(String path) throws FileNotFoundException, AccessDeniedException {
		
		File theFile = new File(path);
		
		//Checking if the file exists
		if (!theFile.exists()){
			String msg = "source file not found: " + path;
			logger.error(msg);
			throw new FileNotFoundException("source file does not exist");
		}
		if (!theFile.canRead()){			
			throw new AccessDeniedException("cannot read from source file");
		}
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

	public void sendReadRequest(String fullpath, String mode) throws IOException {
		
		String[] pathSegments = fullpath.split("\\"+File.separator);
		String filename = pathSegments[pathSegments.length-1];
		String dirpath = fullpath.substring(0, fullpath.length() - filename.length());
		logger.debug("p[ath name is " + dirpath);

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
		Receiver r = new Receiver(this, sendReceiveSocket, receivePacket.getPort());
		r.receiveFile(receivePacket, dirpath, filename);
	}

	public void sendWriteRequest(String fullpath, String mode) {
		
		String[] pathSegments = fullpath.split("\\"+File.separator);
		String filename = pathSegments[pathSegments.length-1];
		
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

		Sender s = new Sender(this, sendReceiveSocket,receivePacket.getPort());
		try {
			s.sendFile(new File(fullpath));
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	@Override
	public String getStoragePath(String path) {		
		return path;
	}

	@Override
	public String getLogLabel() {
		// TODO Auto-generated method stub
		return null;
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



