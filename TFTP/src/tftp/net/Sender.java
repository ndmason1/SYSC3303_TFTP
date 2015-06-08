/*
 * Sender.java
 * 
 * Authors: TEAM 1
 * 
 * This file was created specifically for the course SYSC 3303.
 */

package tftp.net;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.Arrays;

import tftp.exception.ErrorReceivedException;
import tftp.exception.TFTPException;
import tftp.server.thread.WorkerThread;

public class Sender {
	
	private final static int DEFAULT_RETRY_TRANSMISSION = 3;

	private FileInputStream fileReader;
	private DatagramSocket socket;
	private int bytesRead;
	private InetAddress receiverIP;	
	private PacketUtil packetUtil;	
	private PacketParser parser;
	
	private WorkerThread ownerThread = null;	// whatever server thread is using this object
	private ProcessType receiverProcess; 		// process that is controlling the Receiver to this Sender 
	private String threadLabel = "";			// identify the owner thread when sending ACK
	private Boolean duplicatePacket = false;
	
	public Sender(ProcessType receiverProcess, DatagramSocket socket,InetAddress ip, int receiverPort){
		receiverIP = ip;
		this.socket = socket;		
		packetUtil = new PacketUtil(receiverIP, receiverPort);
		parser = new PacketParser(receiverIP, receiverPort);
		
		this.receiverProcess = receiverProcess;		
	}
	
	// extra constructor to allow the Receiver to print messages in the context of a server thread
	public Sender(WorkerThread ownerThread, ProcessType receiverProcess, DatagramSocket socket,InetAddress ip, int senderPort){
		this(receiverProcess, socket,ip, senderPort);		
		this.ownerThread = ownerThread;
		threadLabel = ownerThread.getName() + ": ";
	}

	public void sendFile(File theFile) throws TFTPException {
		
		try {
			fileReader = new FileInputStream(theFile);
		} catch (FileNotFoundException e) {
			throw new TFTPException(e.getMessage(), PacketUtil.ERR_FILE_NOT_FOUND);
		}		
		
		int blockNum = 1;
		byte[] sendBuf = new byte[512]; // need to make this exactly our block size so we only read that much
		DatagramPacket sendPacket = null;
		
		boolean done = false;
		do		
		{
			// zero the send buffer so no lingering data is sent
			Arrays.fill(sendBuf, (byte)0);
			
			// if last ACK packet was a duplicate we don't read from file
			if (!duplicatePacket){
				try {
					bytesRead = fileReader.read(sendBuf);
				} catch (IOException e) {
					throw new TFTPException("Error reading data from file: "+e.getMessage(), PacketUtil.ERR_UNDEFINED);
				}
				if (bytesRead == -1) {
					bytesRead = 0;				
				}
				if (bytesRead < 512) {
					done = true;
				}
				// send DATA
				sendPacket = packetUtil.formDataPacket(sendBuf, bytesRead, blockNum);
				PacketUtil.sendPacketToProcess(threadLabel, socket, sendPacket, receiverProcess, "DATA");
			}
			
			DatagramPacket reply = null;
	        
			boolean packetReceived = false;
	        int retransmission = 0;
	        
	        // expect ACK
	        while (!packetReceived && retransmission <= DEFAULT_RETRY_TRANSMISSION){
	        	try {
	        		reply = PacketUtil.receivePacketOrTimeout(threadLabel, socket, receiverProcess, "ACK");
	        		packetReceived = true;
	        		retransmission = 0;
	        		
	        	} catch (SocketTimeoutException ex){
	        		//no response for last Data packet, Data packet maybe lost, resending...
	        		printToConsole("Error: Timed out retreiving ACK Packet, Possible data packet loss, resending...");
	    			
    				if (retransmission == DEFAULT_RETRY_TRANSMISSION){
    					
    					System.out.println("Can not complete sending Request, terminated");
    					return;
    				}
    				System.out.println(sendPacket);
    				if (!duplicatePacket){
    					PacketUtil.sendPacketToProcess(threadLabel, socket, sendPacket, receiverProcess, "DATA");
    				}
					retransmission++;
	        	}   
	        

	        	// parse ACK to ensure it is correct before continuing
	        	try {
	        		duplicatePacket = parser.parseAckPacket(reply, blockNum);
	        	} catch (ErrorReceivedException e) {
	        		// the other side sent an error packet, don't send a response				
	        		// rethrow so the owner of this Sender knows whats up
	        		throw e;

	        	} catch (TFTPException e) {

	        		// send error packet
	        		DatagramPacket errPacket = packetUtil.formErrorPacket(e.getErrorCode(), e.getMessage(),
	        				reply.getAddress(), reply.getPort());				
	        		PacketUtil.sendPacketToProcess(threadLabel, socket, errPacket, receiverProcess, "ERROR");

	        		if (e.getErrorCode() == PacketUtil.ERR_UNKNOWN_TID) {
	        			printToConsole("received packet with unknown TID");
	        			// consider unknown TID a duplicate packet so we still wait for the right ACK
	        			duplicatePacket = true;
	        		} else {
	        			// rethrow so the owner of this Sender knows whats up
	        			throw e;
	        		}
	        	}
	        }
			
			if (!duplicatePacket) { blockNum++; }

		} while (!done);
		
	}
	
	private void printToConsole(String message) {
		if (ownerThread != null) // server thread owns this object
			System.out.printf("%s: %s\n", ownerThread.getName(), message);
		else // client owns this object
			System.out.printf("%s\n", message);
	}

}
