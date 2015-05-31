/*
 * Client.java
 * 
 * Authors: TEAM 1
 * 
 * This file was created specifically for the course SYSC 3303.
 */

package tftp.client;

import java.nio.file.*;
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
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Paths;

import tftp.exception.TFTPException;
import tftp.net.PacketParser;
import tftp.net.PacketUtil;
import tftp.net.Receiver;
import tftp.net.Sender;
import tftp.exception.*;

/**
 * 
 * This class implements a client program that sends TFTP connection requests to a server.
 *
 */
public class Client {	 

	//Private variables
	private DatagramSocket sendReceiveSocket;
	private DatagramPacket sendPacket, receivePacket;

	private int targetPort;
	private InetAddress targetIP; 
	private String directory;
	private String filename;
	private String mode;
	private File theFile;

	//default constructor for testing purposes mainly
	public Client (){
		try {
			sendReceiveSocket = new DatagramSocket();
		} catch (SocketException se) {
			se.printStackTrace();
			System.exit(1);
		}

		try {
			setDirectory(new java.io.File(".").getCanonicalPath().concat(new String("\\src\\tftp\\client\\ClientFiles")));
		} catch (IOException e) {
			System.out.println("Couldn't set up directory for client files! terminating");
			e.printStackTrace();
			cleanup();
			System.exit(1);

		}

		try {
			targetIP = InetAddress.getLocalHost();  // THIS WILL CHANGE IN ITERATION 5
		} catch (UnknownHostException e) {
			System.out.println("Couldn't set target IP address! terminating");
			e.printStackTrace();
			cleanup();
			System.exit(1);
		}
	}

	//New constructor passes on filename and mode so it can be set and used everywhere
	public Client(String file, String aMode) {
		try {
			sendReceiveSocket = new DatagramSocket();
		} catch (SocketException se) {
			se.printStackTrace();
			System.exit(1);
		}

		setFilename(file);
		setMode(aMode);

		try {
			setDirectory(new java.io.File(".").getCanonicalPath().concat(new String("\\src\\tftp\\client\\ClientFiles")));
		} catch (IOException e) {			
			System.out.println("Couldn't set up directory for client files! terminating");
			e.printStackTrace();
			cleanup();
			System.exit(1);
		}
		setFile(new File(getDirectory().concat("\\" + getFilename())));

		try {
			targetIP = InetAddress.getLocalHost();  // THIS WILL CHANGE IN ITERATION 5
		} catch (UnknownHostException e) {
			System.out.println("Couldn't set target IP address! terminating");
			e.printStackTrace();
			cleanup();
			System.exit(1);
		}
	}

	public void cleanup() {
		sendReceiveSocket.close();
	}

	public void retreiveFile(){setFile(new File(getDirectory().concat("\\" + getFilename())));}

	public void checkValidReadOperation() throws TFTPException {


		if (getFile().exists()){

			
			//Checking if user can read the file
			Path path = Paths.get(directory + "\\" + filename);
			if (!Files.isWritable(path)){
				String msg = "Do not have write permission of destination file";
				throw new TFTPException(msg, PacketUtil.ERR_ACCESS_VIOLATION);
			}

			// note that we are not throwing an exception for existing file here 
			// as we are allowing overwrites on either side
		}		
	}

	public void checkValidWriteOperation() throws TFTPException {

		//Checking if the file exists
		if (!getFile().exists()){
			String msg = "source file not found: " + getDirectory();
			System.out.println(msg);
			throw new TFTPException(msg, PacketUtil.ERR_FILE_NOT_FOUND);

		}
	
		Path path = Paths.get(directory + "\\" + filename);
		if (!Files.isReadable(path)){			
			throw new TFTPException("Do not have read permission from source file", PacketUtil.ERR_ACCESS_VIOLATION);
		}
		if (getFile().length() > 33553920L){
			throw new TFTPException("source file is too big! (files >  33MB not supported)", PacketUtil.ERR_UNDEFINED);
		}
	}

	// phasing this method out, PacketUtil's formRrqPacket() will be used instead
	private byte[] prepareReadRequestPayload() {		

		int msgLength = getFilename().length() + getMode().length() + 4; 
		byte msg[] = new byte[msgLength];

		// preamble
		msg[0] = 0x00;
		msg[1] = 0x01;

		// filename
		byte[] fbytes = getFilename().getBytes(); 
		System.arraycopy(fbytes, 0, msg, 2, fbytes.length);
		msg[fbytes.length + 2] = 0x00;

		// mode
		byte[] mbytes = getMode().getBytes(); 
		System.arraycopy(mbytes, 0, msg, 3+fbytes.length, mbytes.length);
		msg[fbytes.length + mbytes.length + 3] = 0x00;

		return msg;
	}

	// phasing this method out, PacketUtil's formWrqPacket() will be used instead
	private byte[] prepareWriteRequestPayload() {

		int msgLength = getFilename().length() + getMode().length() + 4; 
		byte msg[] = new byte[msgLength];

		// preamble
		msg[0] = 0x00;
		msg[1] = 0x02;

		// filename
		byte[] fbytes = getFilename().getBytes(); 
		System.arraycopy(fbytes, 0, msg, 2, fbytes.length);
		msg[fbytes.length + 2] = 0x00;

		// mode
		byte[] mbytes = getMode().getBytes(); 
		System.arraycopy(mbytes, 0, msg, 3+fbytes.length, mbytes.length);
		msg[fbytes.length + mbytes.length + 3] = 0x00;

		return msg;
	}	

	public void sendReadRequest() throws TFTPException{		

		System.out.println("Starting read of file " + getFilename() + " from server...");

		// set up PacketUtil object to generate packets with
		PacketUtil packetUtil = new PacketUtil(targetIP, targetPort);		

		// create send packet
		sendPacket = packetUtil.formRrqPacket(getFilename(), getMode());

		try {
			sendReceiveSocket.send(sendPacket);
		} catch (IOException e) {
			System.out.println("Error sending request packet!");
			e.printStackTrace();
			cleanup();
			return;
		}

		byte data[] = new byte[PacketUtil.BUF_SIZE];
		receivePacket = new DatagramPacket(data, data.length);		

		// get server response - the port it is sent from should be used as the server TID
		try {			  
			sendReceiveSocket.receive(receivePacket);

		} catch(IOException e) {
			System.out.println("Error receiving response to request packet!");
			e.printStackTrace();
			cleanup();
			return;
		}

		PacketParser parser = new PacketParser(targetIP, receivePacket.getPort());

		try {
			//receive first data packet with block #1
			parser.parseDataPacket(receivePacket, 1);
		} catch(ErrorReceivedException e) {
			// the other side sent an error packet, so in most cases don't send a response

			if (e.getErrorCode() == PacketUtil.ERR_UNKNOWN_TID) {
				// send error packet to the unknown TID
				DatagramPacket errPacket = packetUtil.formErrorPacket(e.getErrorCode(), e.getMessage());
				try {			   
					sendReceiveSocket.send(errPacket);
				} catch (IOException ex) { 
					throw new TFTPException(ex.getMessage(), PacketUtil.ERR_UNDEFINED);
				}
			}

			// rethrow so the client UI can print a message
			throw e;

		} catch(TFTPException e){
			// send error packet
			DatagramPacket errPacket = null;			

			if (e.getErrorCode() == PacketUtil.ERR_UNKNOWN_TID) {
				// address packet to the unknown TID
				errPacket = packetUtil.formErrorPacket(e.getErrorCode(), e.getMessage(),
						receivePacket.getAddress(), receivePacket.getPort());						
			} else {
				// packet will be addressed to recipient as usual					
				errPacket = packetUtil.formErrorPacket(e.getErrorCode(), e.getMessage());
			}

			try {			   
				sendReceiveSocket.send(errPacket);			   
			} catch (IOException ex) {			   
				System.out.println("Error sending ERROR packet!");
				e.printStackTrace();
				cleanup();
				return;
			}

			// rethrow so the client UI can print a message
			throw e;
		}

		// request is good, set up a receiver to proceed with the transfer
		Receiver r = new Receiver(sendReceiveSocket, receivePacket.getPort());
		r.receiveFile(receivePacket, getFile());
	}

	public void sendWriteRequest() throws TFTPException {

		
		System.out.println("Starting write of file : " + getFilename() + " to server...");
		byte[] payload = prepareWriteRequestPayload();


		System.out.println("Starting write of file + " + getFilename() + " from server...");
		
		// set up PacketUtil object to generate packets with
		PacketUtil packetUtil = new PacketUtil(targetIP, targetPort);		


		checkValidWriteOperation();
		// create send packet
		sendPacket = packetUtil.formWrqPacket(getFilename(), getMode());

		// send packet to server
		try {
			sendReceiveSocket.send(sendPacket);
		} catch (IOException e) {
			System.out.println("Error sending request packet!");
			e.printStackTrace();
			cleanup();
			return;
		}

		// create recv packet
		byte data[] = new byte[PacketUtil.BUF_SIZE];
		receivePacket = new DatagramPacket(data, data.length);

		try {			  
			sendReceiveSocket.receive(receivePacket);

		} catch(IOException e) {
			System.out.println("Error receiving response to request packet!");
			e.printStackTrace();
			cleanup();
			return;
		}

		PacketParser parser = new PacketParser(receivePacket.getAddress(), receivePacket.getPort());

		try{
			parser.parseAckPacket(receivePacket, 0);
		} catch(ErrorReceivedException e) {
			// the other side sent an error packet, so in most cases don't send a response

			if (e.getErrorCode() == PacketUtil.ERR_UNKNOWN_TID) {
				// send error packet to the unknown TID
				DatagramPacket errPacket = packetUtil.formErrorPacket(e.getErrorCode(), e.getMessage());
				try {			   
					sendReceiveSocket.send(errPacket);
				} catch (IOException ex) { 
					throw new TFTPException(ex.getMessage(), PacketUtil.ERR_UNDEFINED);
				}
			}

			// rethrow so the client UI can print a message
			throw e;

		} catch(TFTPException e){
			// send error packet
			DatagramPacket errPacket = null;			

			if (e.getErrorCode() == PacketUtil.ERR_UNKNOWN_TID) {
				// address packet to the unknown TID
				errPacket = packetUtil.formErrorPacket(e.getErrorCode(), e.getMessage(),
						receivePacket.getAddress(), receivePacket.getPort());						
			} else {
				// packet will be addressed to recipient as usual					
				errPacket = packetUtil.formErrorPacket(e.getErrorCode(), e.getMessage());
			}

			try {			   
				sendReceiveSocket.send(errPacket);			   
			} catch (IOException ex) {			   
				System.out.println("Error sending ERROR packet!");
				e.printStackTrace();
				cleanup();
				return;
			}

			// rethrow so the client UI can print a message
			throw e;
		}

		// set up a sender to proceed with the transfer

		Sender s = new Sender(sendReceiveSocket, receivePacket.getPort());
		try {
			s.sendFile(getFile());
		} catch (TFTPException e) {
			// just throw it, client UI can print message
			throw e;
//			System.out.println("ERROR CODE " + e.getErrorCode());
//			System.out.println(e.getMessage());
		}
	}

	//Client get functions
	public String getDirectory(){return directory;}
	public String getFilename(){return filename;}
	public int getPortNum(){return targetPort;}
	public String getMode(){return mode;}
	public File getFile(){return theFile;}

	//Client set functions
	public void setFilename(String aFilename){filename = aFilename;}
	public void setDirectory(String aDirectory){directory = aDirectory;}
	public void setPortNum(int aPort){targetPort = aPort;}
	public void setMode(String aMode){mode = aMode;}
	public void setFile(File aFile){theFile = aFile;}

}



