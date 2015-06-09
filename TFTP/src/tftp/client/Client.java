/*
 * Client.java
 * 
 * Authors: TEAM 1
 * 
 * This file was created specifically for the course SYSC 3303.
 */

package tftp.client;

import java.io.File;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import tftp.exception.TFTPException;
import tftp.net.PacketParser;
import tftp.net.PacketUtil;
import tftp.net.ProcessType;
import tftp.net.Receiver;
import tftp.net.Sender;
import tftp.sim.ErrorSimulator;
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
			
			//set socket timeout to 2 sec
			//sendReceiveSocket.setSoTimeout(2*1000);
			sendReceiveSocket.setSoTimeout(ErrorSimulator.TIMEOUT_MS);
		} catch (SocketException se) {
			se.printStackTrace();
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
			//sendReceiveSocket.setSoTimeout(5*1000);
			sendReceiveSocket.setSoTimeout(ErrorSimulator.TIMEOUT_MS);
		} catch (SocketException se) {
			se.printStackTrace();
			System.exit(1);
		}

		setFilename(file);
		setMode(aMode);

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
				String msg = "ACCESS VIOLATION:\n !!! No Write Permission of Client side File(" + filename +") !!!";
				throw new TFTPException(msg, PacketUtil.ERR_ACCESS_VIOLATION);
			}

			// note that we are not throwing an exception for existing file here 
			// as we are allowing overwrites on either side
		}		
	}

	public void checkValidWriteOperation() throws TFTPException {

		//Checking if the file exists
		if (!getFile().exists()){
			String msg = "CLIENT: FILE NOT FOUND(" + filename +") IN THE DIRECTORY\n" + getDirectory();
			
			throw new TFTPException(msg, PacketUtil.ERR_FILE_NOT_FOUND);

		}
	
		Path path = Paths.get(directory + "\\" + filename);
		if (!Files.isReadable(path)){			
			throw new TFTPException("ACCESS VIOLATION:\n !!! No Read Permission of Client side File(" + filename +") !!!", PacketUtil.ERR_ACCESS_VIOLATION);
		}
		if (getFile().length() > 33553920L){
			throw new TFTPException("source file is too big! (files >  33MB not supported)", PacketUtil.ERR_UNDEFINED);
		}
	}

	public void sendReadRequest() throws TFTPException{		

		System.out.println("Starting read of file " + getFilename() + " from server...");

		// set up PacketUtil object to generate packets with
		PacketUtil packetUtil = new PacketUtil(targetIP, targetPort);		

		// send request packet to server
		sendPacket = packetUtil.formRrqPacket(getFilename(), getMode());		
		PacketUtil.sendPacketToProcess("", sendReceiveSocket, sendPacket, ProcessType.SERVER, "RRQ");	    

		// get server response - the port it is sent from should be used as the server TID
        boolean packetReceived = false;
        int retransmission = 0;
        
        while (!packetReceived && retransmission <= PacketUtil.DEFAULT_RETRY_TRANSMISSION){
        	try {			  
        		receivePacket = PacketUtil.receivePacketOrTimeout("", sendReceiveSocket, ProcessType.SERVER, "DATA");
        		packetReceived = true;
        		
        	} catch(SocketTimeoutException ex){
        		
        		System.out.println("Error: Timed out while waiting for DATA Packet");
        		
    			if (retransmission == PacketUtil.DEFAULT_RETRY_TRANSMISSION){
    				throw new TFTPException(String.format("Could not reach server after %d retries, aborting request", 
    						retransmission), PacketUtil.ERR_UNDEFINED);
    			}    			

        		System.out.println("possible RRQ packet loss, resending...");
    			
    			PacketUtil.sendPacketToProcess("", sendReceiveSocket, sendPacket, ProcessType.SERVER, "RRQ");
    			retransmission ++;
        	} 
		}
        
        
		PacketParser parser = new PacketParser(targetIP, receivePacket.getPort());
		
		try {
			//receive first data packet with block #1
			parser.parseDataPacket(receivePacket, 1);
		} catch(ErrorReceivedException e) {
			// the other side sent an error packet, don't send a response
			// rethrow so the client UI can print a message
			throw e;

		} catch(TFTPException e){
			
			// send error packet to TID of packet					
			DatagramPacket errPacket = packetUtil.formErrorPacket(e.getErrorCode(), e.getMessage(),
						receivePacket.getAddress(), receivePacket.getPort());

			PacketUtil.sendPacketToProcess("", sendReceiveSocket, errPacket, ProcessType.SERVER, "ERROR");

			// unknown TID will never happen here since we just learned the server TID from this packet
			// rethrow so the client UI can print a message
			throw e;
		}
		
		// request is good, set up a receiver to proceed with the transfer
		Receiver r = new Receiver(ProcessType.SERVER, sendReceiveSocket, targetIP, receivePacket.getPort());
		r.receiveFile(receivePacket, getFile());
	}

	public void sendWriteRequest() throws TFTPException {
		
		System.out.println("Starting write of file : " + getFilename() + " to server...");
		
		// set up PacketUtil object to generate packets with
		PacketUtil packetUtil = new PacketUtil(targetIP, targetPort);		
		
		// create send packet
		sendPacket = packetUtil.formWrqPacket(getFilename(), getMode());

		// send packet to server
		PacketUtil.sendPacketToProcess("", sendReceiveSocket, sendPacket, ProcessType.SERVER, "WRQ");
        boolean packetReceived = false;
        int retransmission = 0;
        
        while (!packetReceived && retransmission <= PacketUtil.DEFAULT_RETRY_TRANSMISSION){
        	try {			  
        		receivePacket = PacketUtil.receivePacketOrTimeout("", sendReceiveSocket, ProcessType.SERVER, "ACK");
        		packetReceived = true;
        		
        	} catch(SocketTimeoutException ex){
        		
        		System.out.println("Error: Timed out while waiting for DATA Packet");		
        		
    			if (retransmission == PacketUtil.DEFAULT_RETRY_TRANSMISSION){
    				throw new TFTPException(String.format("No response received after %d retries, aborting request", 
    						retransmission), PacketUtil.ERR_UNDEFINED);    				
    			}    			

        		System.out.println("possible WRQ packet loss, resending...");        
    			
    			PacketUtil.sendPacketToProcess("", sendReceiveSocket, sendPacket, ProcessType.SERVER, "WRQ");	
    			retransmission ++;
        	}
		}
        
        
		PacketParser parser = new PacketParser(receivePacket.getAddress(), receivePacket.getPort());

		// parse ACK 0 packet
		try{
			parser.parseAckPacket(receivePacket, 0);
		} catch(ErrorReceivedException e) {
			// the other side sent an error packet, don't send a response
			// rethrow so the client UI can print a message
			throw e;

		} catch(TFTPException e){
			// send error packet
			DatagramPacket errPacket = null;	
					
			errPacket = packetUtil.formErrorPacket(e.getErrorCode(), e.getMessage(),
					receivePacket.getAddress(), receivePacket.getPort());
			
			PacketUtil.sendPacketToProcess("", sendReceiveSocket, errPacket, ProcessType.SERVER, "ERROR");			

			// rethrow so the client UI can print a message
			throw e;
		}

		// set up a sender to proceed with the transfer

		Sender s = new Sender(ProcessType.SERVER, sendReceiveSocket,receivePacket.getAddress(), receivePacket.getPort());
		try {
			s.sendFile(getFile());
		} catch (TFTPException e) {
			// just throw it, client UI can print message
			throw e;
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

	//set server ip function
	public void setIP(InetAddress ip){targetIP = ip;}
}



