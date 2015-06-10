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
import java.util.Arrays;

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
			// the other side sent an error packet, don't send response			
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
		
		if(lock.isWriteLock(filename) || lock.isReadLock(filename)){
			printToConsole(String.format("ERROR: (%d) %s\n", PacketUtil.ERR_ACCESS_VIOLATION, "ACCESS VIOLATION, File is locked, can not access"));
			// send error packet
			DatagramPacket errPacket = packetUtil.formErrorPacket(PacketUtil.ERR_ACCESS_VIOLATION, "ACCESS VIOLATION, File is locked, can not access");
			PacketUtil.sendPacketToProcess(getName()+": ", sendReceiveSocket, errPacket, ProcessType.CLIENT, "ERROR");
			return;
		}
				
		lock.addWriter(filename);
			
		File f = new File(getDirectory().concat("\\" + filename));
		
		if(f.exists() && !f.canWrite()){    // no write access

			byte errorCode = 2;   //error code 2 : access violation
			DatagramPacket error = OPcodeError.OPerror(new String("ACCESS VIOLATION: No Write Permission of Server side File(" + filename +")"),errorCode);  //create error packet
			error.setAddress(reqPacket.getAddress());
			error.setPort(reqPacket.getPort());		

			PacketUtil.sendPacketToProcess(getName()+": ", sendReceiveSocket, error, ProcessType.CLIENT, "ERROR");			   
			sendReceiveSocket.close();
			lock.deleteWriter(filename);
			return;
		}

		// request is good if we made it here
		// write request, so send an ACK 0		
		DatagramPacket initAck = packetUtil.formAckPacket(0);
		PacketUtil.sendPacketToProcess(getName()+": ", sendReceiveSocket, initAck, ProcessType.CLIENT, "ACK");		
		
		// get the first data packet so we can set up receiver
		
		boolean correctData = false;
        while (!correctData) {

        	boolean packetReceived = false;
        	int retransmission = 0;
        	
        	while (!packetReceived && retransmission <= PacketUtil.DEFAULT_RETRY_TRANSMISSION){

        		try {
        			receivePacket = PacketUtil.receivePacketOrTimeout(getName()+": ", sendReceiveSocket, ProcessType.CLIENT, "DATA");
        			packetReceived = true;

        		} catch(SocketTimeoutException e){

        			printToConsole("Error: Timed out while waiting for DATA Packet");

        			if (retransmission == PacketUtil.DEFAULT_RETRY_TRANSMISSION){
        				System.out.println("Maximum retries reached with no response");
        				System.out.println("Can not complete transfer");
        				lock.deleteWriter(filename);
        				return;
        			}					

        			printToConsole("possible ACK packet loss, resending...");
        			PacketUtil.sendPacketToProcess(getName()+": ", sendReceiveSocket, initAck, ProcessType.CLIENT, "ACK");
        			
        			retransmission++;
        		}

        	}
        
	        // parse the first DATA packet to ensure it is correct before continuing
	        try {
	        	packetParser.parseDataPacket(receivePacket, 1);
	
	        } catch (ErrorReceivedException e) {
	        	// the other side sent an error packet, don't send a response        	
	        	printToConsole("ERROR packet received from client!");		
	        	printToConsole(String.format("ERROR: (%d) %s\n", e.getErrorCode(), e.getMessage()));
	        	lock.deleteWriter(filename);
				return;        	
	
	        } catch (TFTPException e) {
	
	        	printToConsole(String.format("ERROR: (%d) %s\n", e.getErrorCode(), e.getMessage()));
	        	
	        	// send error packet
	        	DatagramPacket errPacket = packetUtil.formErrorPacket(e.getErrorCode(), e.getMessage(),
	        			receivePacket.getAddress(), receivePacket.getPort());	
	        	PacketUtil.sendPacketToProcess(getName()+": ", sendReceiveSocket, errPacket, ProcessType.CLIENT, "ERROR");
	
	        	// keep going if error was unknown TID
	        	// otherwise, rethrow so the client UI can print a message
	        	if (e.getErrorCode() == PacketUtil.ERR_UNKNOWN_TID)
	        		continue;
	        	else {
	        		lock.deleteWriter(filename);
	        		return;
	        	}
	        }
	        
	        // if we get here, DATA 1 is good
	        correctData = true;
        }
        
		// set up receiver with request packet's port, as this is the client's TID
		Receiver r = new Receiver(this, ProcessType.CLIENT, sendReceiveSocket,reqPacket.getAddress(), reqPacket.getPort());
		try {
			r.receiveFile(receivePacket, f);
			printToConsole("Finished write request for file: " + f.getName());
		} catch (TFTPException e) {
			printToConsole(String.format("ERROR: (%d) %s\n", e.getErrorCode(), e.getMessage()));
		} finally {
			lock.deleteWriter(filename);
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
