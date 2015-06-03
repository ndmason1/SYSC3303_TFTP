/*
 * WriteHandlerThread.java
 * 
 * Authors: TEAM 1
 * 
 * This file was created specifically for the course SYSC 3303.
 */

package tftp.server.thread;

import java.io.File;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.SocketTimeoutException;

import tftp.exception.ErrorReceivedException;
import tftp.exception.TFTPException;
import tftp.net.PacketUtil;
import tftp.net.Receiver;

/**
 * A specialized thread that processes TFTP write requests received by a TFTP server.
 */
public class WriteHandlerThread extends WorkerThread {
	
	private final static int DEFAULT_RETRY_TRANSMISSION = 2;
	private String directory;	
	
	/**
	 * Constructs a WriteHandlerThread. Passes the DatagramPacket argument to 
	 * the WorkerThread constructor. 
	 *
	 * @param  reqPacket  the packet containing the client's request
	 */
	public WriteHandlerThread(DatagramPacket reqPacket) {
		super("WriteHandler-" + id++, reqPacket);
	}
	
	/**
	 * Runs this thread, which processes a TFTP write request and starts a 
	 * file transfer.
	 */
	@Override

	public void run() {		
		
		byte[] data = reqPacket.getData();
		
		PacketUtil packetUtil = new PacketUtil(reqPacket.getAddress(), reqPacket.getPort());
		String filename = null;
		
		// parse the request packet to ensure it is correct before starting the transfer
		try {
			filename = packetParser.parseWRQPacket(reqPacket);
			
		} catch (ErrorReceivedException e) {
			// the other side sent an error pack``````````````` (%d) %s\n", e.getErrorCode(), e.getMessage()));			
			printToConsole("ERROR packet received from client!");
			printToConsole(String.format("ERROR: (%d) %s\n", e.getErrorCode(), e.getMessage()));
			return;
			
		} catch (TFTPException e) {
			
			printToConsole(String.format("ERROR: (%d) %s\n", e.getErrorCode(), e.getMessage()));

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
				System.out.println("Error occured sending ERROR packet");
				ex.printStackTrace();
				cleanup();
				return;
			}
			
			printToConsole("request cannot be processed, ending this thread");			
			return;
		}	
		
		try {
			setDirectory(new java.io.File(".").getCanonicalPath().concat(new String("\\src\\tftp\\server\\ServerFiles")));
		} catch (IOException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		}
	
		File f = new File(getDirectory().concat("\\" + filename));
		
		if(f.exists() && !f.canWrite()){    // no write access

			byte errorCode = 2;   //error code 2 : access violation
			DatagramPacket error = OPcodeError.OPerror("ACCESS VIOLATION",errorCode);  //create error packet
			error.setAddress(reqPacket.getAddress());
			error.setPort(reqPacket.getPort());		

			try {			   
				sendReceiveSocket.send(error);			   
			} catch (IOException ex) {			   
				ex.printStackTrace();
			}			   
			sendReceiveSocket.close();			   
			return;
		}

		// request is good if we made it here
		// write request, so send an ACK 0
		
		System.out.println("sending ACK 0");
		DatagramPacket initAck = packetUtil.formAckPacket(0);
		try {
			sendReceiveSocket.send(initAck);
		} 
		catch (IOException e) {
			printToConsole("Error occured sending ACK 0 packet");
			e.printStackTrace();
			cleanup();
			return;
		} 
		
		
		// get the first data packet so we can set up receiver
		data = new byte[PacketUtil.BUF_SIZE];
		receivePacket = new DatagramPacket(data, data.length);
		
		boolean PacketReceived = false;
        int retransmission = 0;
        
        while (!PacketReceived && retransmission <= DEFAULT_RETRY_TRANSMISSION){ 
        	try {
		        if (retransmission == DEFAULT_RETRY_TRANSMISSION){
		        	System.out.println("Can not complete sending Request, terminated");
		        	return;
		        }
        		sendReceiveSocket.receive(receivePacket);
        		PacketReceived = true;
        		retransmission++;
        	} catch (SocketTimeoutException socketTimeoutException) {
        		printToConsole("Error Socket Timeout occured retrieving DATA packet");
        		return;
        	} catch (IOException e) {
        		e.printStackTrace();
        	}
        }
		// set up receiver with request packet's port, as this is the client's TID
		Receiver r = new Receiver(this, sendReceiveSocket, reqPacket.getPort());
		try {
			printToConsole("calling receiver.receiveFile()");
			r.receiveFile(receivePacket, f);
		} catch (TFTPException e) {
			printToConsole(String.format("ERROR: (%d) %s\n", e.getErrorCode(), e.getMessage()));
		} finally {		
			cleanup();
		}
	}
	
	
	//get functions
	public String getDirectory(){
		return directory;
	}
	
	//set functions
	public void setDirectory(String aDirectory){directory = aDirectory;}

}
