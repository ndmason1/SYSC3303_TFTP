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
import tftp.sim.ErrorSimUtil;

public class Sender {
	
	private final static int DEFAULT_RETRY_TRANSMISSION = 2;

	private FileInputStream fileReader;
	private DatagramSocket socket;
	private int fileLength, bytesRead;
	private InetAddress receiverIP;	
	private PacketUtil packetUtil;	
	private PacketParser parser;
	
	private WorkerThread ownerThread = null; // whatever server thread is using this object

	public Sender(DatagramSocket socket, int receiverPort){

		try {
			receiverIP = InetAddress.getLocalHost();
		} catch (UnknownHostException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		this.socket = socket;		
		packetUtil = new PacketUtil(receiverIP, receiverPort);
		parser = new PacketParser(receiverIP, receiverPort);
	}
	
	// extra constructor to allow the Receiver to print messages in the context of a server thread
	public Sender(WorkerThread ownerThread, DatagramSocket socket, int senderPort){
		this(socket, senderPort);		
		this.ownerThread = ownerThread;
	}

	public void sendFile(File theFile) throws TFTPException {
		try {
			fileReader = new FileInputStream(theFile);
		} catch (FileNotFoundException e) {
			throw new TFTPException(e.getMessage(), PacketUtil.ERR_FILE_NOT_FOUND);
		}
		
		try {
			fileLength = fileReader.available();
		} catch (IOException e) {
			throw new TFTPException(e.getMessage(), PacketUtil.ERR_UNDEFINED);
		}
		
		int blockNum = 1;

		byte[] sendBuf = new byte[512]; // need to make this exactly our block size so we only read that much
		byte[] recvBuf = new byte[PacketUtil.BUF_SIZE];

		boolean done = false;

		do		
		{
			// zero the send buffer so no lingering data is sent
			Arrays.fill(sendBuf, (byte)0);
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
			
			DatagramPacket sendPacket = packetUtil.formDataPacket(sendBuf, bytesRead, blockNum);

			printToConsole(String.format("Sending DATA block %d with %d byte payload.", blockNum, bytesRead));
			try {
				socket.send(sendPacket); 
			} catch (IOException e) {
				throw new TFTPException(e.getMessage(), PacketUtil.ERR_UNDEFINED);
			}

			DatagramPacket reply = new DatagramPacket(recvBuf, recvBuf.length);

			// expect an ACK from the other side
			printToConsole(String.format("waiting for ACK %d", blockNum));
	        
			boolean PacketReceived = false;
	        int retransmission = 0;
	        
	        while (!PacketReceived && retransmission <= DEFAULT_RETRY_TRANSMISSION){
	        	try {
	        		socket.receive(reply);	        		
	        		PacketReceived = true;
	        	} catch (SocketTimeoutException ex){
	        		//no response for last Data packet, Data packet maybe lost, resending...
	        		printToConsole("Error: Timed out retreiving ACK Packet, Possible data packet loss, resending...");
	    			try {
	    		        if (retransmission == DEFAULT_RETRY_TRANSMISSION){
	    		        	System.out.println("Can not complete sending Request, terminated");
	    		        	return;
	    		        }
	    				socket.send(sendPacket);
	    				retransmission++;
	    			} catch (IOException e) {
	    				throw new TFTPException(e.getMessage(), PacketUtil.ERR_UNDEFINED);
	    			}
	        	} catch (IOException e) {
	        		throw new TFTPException(e.getMessage(), PacketUtil.ERR_UNDEFINED);
	        	}     
	        }
	        
			// parse ACK to ensure it is correct before continuing
			try {
				parser.parseAckPacket(reply, blockNum);
			} catch (ErrorReceivedException e) {
				// the other side sent an error packet, don't send a response				
				// rethrow so the owner of this Sender knows whats up
				throw e;
				
			} catch (TFTPException e) {

				// send error packet
				DatagramPacket errPacket = null;
				
				if (e.getErrorCode() == PacketUtil.ERR_UNKNOWN_TID) {
					printToConsole("SENDER: received packet with unknown TID");
					// address packet to the unknown TID
					errPacket = packetUtil.formErrorPacket(e.getErrorCode(), e.getMessage(),
							reply.getAddress(), reply.getPort());						
				} else {
					// packet will be addressed to recipient as usual					
					errPacket = packetUtil.formErrorPacket(e.getErrorCode(), e.getMessage(),
							reply.getAddress(), reply.getPort());		

				}
				
				try {			   
					socket.send(errPacket);			   
				} catch (IOException ex) {			   
					throw new TFTPException(ex.getMessage(), PacketUtil.ERR_UNDEFINED);
				}
				
				// rethrow so the owner of this Sender knows whats up
				throw e;
			}
			blockNum++;

		} while (!done);
		
	}
	
	private void printToConsole(String message) {
		if (ownerThread != null) // server thread owns this object
			System.out.printf("%s: %s\n", ownerThread.getName(), message);
		else // client owns this object
			System.out.printf("%s\n", message);
	}

}
