/*
 * ReadHandlerThread.java
 * 
 * Authors: TEAM 1
 * 
 * This file was created specifically for the course SYSC 3303.
 */

package tftp.server.thread;

import java.nio.file.*;
import java.io.File;
import java.io.IOException;
import java.net.DatagramPacket;
import java.nio.file.Paths;

import tftp.exception.TFTPException;
import tftp.net.PacketUtil;
import tftp.net.Sender;

/**
 * A specialized thread that processes TFTP read requests received by a TFTP server.
 */
public class ReadHandlerThread extends WorkerThread {	
	
	/**
	 * Constructs a ReadHandlerThread. Passes the DatagramPacket argument to 
	 * the WorkerThread constructor. 
	 *
	 * @param  reqPacket  the packet containing the client's request
	 */
	private String directory; 
	
	public ReadHandlerThread(DatagramPacket reqPacket) {
		super("ReadHandler-" + id++, reqPacket);
		this.directory = super.directory;
	}	

	/**
	 * Runs this thread, which processes a TFTP read request and starts a 
	 * file transfer.
	 */
	@Override
	public void run() {
		
		printToConsole("processing request");
		
		PacketUtil packetUtil = new PacketUtil(reqPacket.getAddress(), reqPacket.getPort());
		String filename = null;
		
		// parse the request packet to ensure it is correct before starting the transfer
		try {
			filename = packetParser.parseRRQPacket(reqPacket);		
		} catch (TFTPException e) {
			printToConsole(String.format("ERROR: (%d) %s\n", e.getErrorCode(), e.getMessage()));
			// send error packet
			DatagramPacket errPacket = packetUtil.formErrorPacket(e.getErrorCode(), e.getMessage());
			try {			   
				sendReceiveSocket.send(errPacket);			   
			} catch (IOException ex) {
				printToConsole("IOException occured while attemping to send ERROR packet");
				ex.printStackTrace();
				cleanup();
				return;
			}
			return;
		}
		

	
		//\\//\\//\\ File Not Found - Error Code 1 //\\//\\//\\

		//Opens an input stream
		File f = new File(getDirectory().concat("\\" + filename));
		Path path = Paths.get(getDirectory().concat("\\" + filename));
		System.out.println(getDirectory().concat("\\" + filename));
		if(!f.exists()){    //file doesn't exist

			byte errorCode = PacketUtil.ERR_FILE_NOT_FOUND;   //error code 1 : file not found
			DatagramPacket error= OPcodeError.OPerror("FILE NOT FOUND",errorCode);  //create error packet
			error.setAddress(reqPacket.getAddress());
			error.setPort(reqPacket.getPort());		

			try {			   
				sendReceiveSocket.send(error);
			} catch (IOException ex) {
				printToConsole("IOException occured while attemping to send ERROR packet");
				ex.printStackTrace();
				cleanup();
				return;
			}
			
			cleanup();			   
			return;
		}
		
		if(!Files.isReadable(path)){    // no read access

			byte errorCode = PacketUtil.ERR_ACCESS_VIOLATION;   //error code 2 : access violation
			DatagramPacket error= OPcodeError.OPerror("ACCESS VIOLATION",errorCode);  //create error packet
			error.setAddress(reqPacket.getAddress());
			error.setPort(reqPacket.getPort());		

			try {			   
				sendReceiveSocket.send(error);			   
			}  catch (IOException ex) {
				printToConsole("IOException occured while attemping to send ERROR packet");
				ex.printStackTrace();
				cleanup();
				return;
			}
			
			cleanup();			   
			return;
		}
		
		// request is good if we made it here
		// read request, so start a file transfer
		Sender s = new Sender(this, sendReceiveSocket, clientPort);
		try {
			printToConsole("Sender created");
			s.sendFile(f);
		} catch (TFTPException e) {
			printToConsole(String.format("ERROR: (%d) %s\n", e.getErrorCode(), e.getMessage()));
			if (e.getErrorCode() != PacketUtil.ERR_UNKNOWN_TID) {
				cleanup();
				return;
			}
		}
		
		cleanup();

	}

	//get functions
	public String getDirectory(){return directory;}
	
	//set functions
	public void setDirectory(String aDirectory){directory = aDirectory;}

}