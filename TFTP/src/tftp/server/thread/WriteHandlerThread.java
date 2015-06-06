/*
 * WriteHandlerThread.java
 * 
 * Authors: TEAM 1
 * 
 * This file was created specifically for the course SYSC 3303.
 */

package tftp.server.thread;

import java.io.File;
import java.net.DatagramPacket;
import java.net.SocketTimeoutException;

import tftp.exception.ErrorReceivedException;
import tftp.exception.TFTPException;
import tftp.net.OPcodeError;
import tftp.net.PacketUtil;
import tftp.net.ProcessType;
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
		this.directory = super.directory;
	}
	
	/**
	 * Runs this thread, which processes a TFTP write request and starts a 
	 * file transfer.
	 */
	@Override

	public void run() {		
				
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
			DatagramPacket errPacket = packetUtil.formErrorPacket(e.getErrorCode(), e.getMessage(),
						reqPacket.getAddress(), reqPacket.getPort());
			PacketUtil.sendPacketToProcess(getName()+": ", sendReceiveSocket, errPacket, ProcessType.CLIENT, "ERROR");
			
			printToConsole("request cannot be processed, ending this thread");			
			return;
		}			
	
		File f = new File(getDirectory().concat("\\" + filename));
		
		if(f.exists() && !f.canWrite()){    // no write access

			byte errorCode = 2;   //error code 2 : access violation
			DatagramPacket error = OPcodeError.OPerror("ACCESS VIOLATION",errorCode);  //create error packet
			error.setAddress(reqPacket.getAddress());
			error.setPort(reqPacket.getPort());		

			PacketUtil.sendPacketToProcess(getName()+": ", sendReceiveSocket, error, ProcessType.CLIENT, "ERROR");			   
			sendReceiveSocket.close();			   
			return;
		}

		// request is good if we made it here
		// write request, so send an ACK 0		
		DatagramPacket initAck = packetUtil.formAckPacket(0);
		PacketUtil.sendPacketToProcess(getName()+": ", sendReceiveSocket, initAck, ProcessType.CLIENT, "ACK");		
		
		// get the first data packet so we can set up receiver
		
		boolean packetReceived = false;
        int retransmission = 0;
        
        while (!packetReceived && retransmission <= DEFAULT_RETRY_TRANSMISSION){ 
        	try {
		        if (retransmission == DEFAULT_RETRY_TRANSMISSION){
		        	printToConsole("Can not complete sending Request, terminated");
		        	return;
		        }
		        
		        receivePacket = PacketUtil.receivePacketOrTimeout(getName()+": ", sendReceiveSocket, ProcessType.CLIENT, "DATA");		        
        		packetReceived = true;
        		retransmission = 0;
        		
        	} catch (SocketTimeoutException socketTimeoutException) {
        		printToConsole("Error Socket Timeout occured retrieving DATA packet");
        		PacketUtil.sendPacketToProcess(getName()+": ", sendReceiveSocket, initAck, ProcessType.CLIENT, "ACK");
        		retransmission++;
        		return;
        	}
        }
        
		// set up receiver with request packet's port, as this is the client's TID
		Receiver r = new Receiver(this, ProcessType.CLIENT, sendReceiveSocket, reqPacket.getPort());
		try {
			r.receiveFile(receivePacket, f);
			printToConsole("Finished write request for file: " + f.getName());
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
