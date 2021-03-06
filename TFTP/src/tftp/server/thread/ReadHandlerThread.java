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
import java.net.DatagramPacket;
import java.nio.file.Paths;

import tftp.exception.TFTPException;
import tftp.net.OPcodeError;
import tftp.net.PacketUtil;
import tftp.net.ProcessType;
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
		
		PacketUtil packetUtil = new PacketUtil(reqPacket.getAddress(), reqPacket.getPort());
		String filename = null;
		
		// parse the request packet to ensure it is correct before starting the transfer
		try {
			filename = packetParser.parseRRQPacket(reqPacket);		
		} catch (TFTPException e) {
			printToConsole(String.format("ERROR: (%d) %s\n", e.getErrorCode(), e.getMessage()));
			// send error packet
			DatagramPacket errPacket = packetUtil.formErrorPacket(e.getErrorCode(), e.getMessage());
			PacketUtil.sendPacketToProcess(getName()+": ", sendReceiveSocket, errPacket, ProcessType.CLIENT, "ERROR");
			return;
		}
		
		if(lock.isWriteLock(filename)){
			printToConsole(String.format("ERROR: (%d) %s\n", PacketUtil.ERR_ACCESS_VIOLATION, "ACCESS VIOLATION, File is locked, can not access"));
			// send error packet
			DatagramPacket errPacket = packetUtil.formErrorPacket(PacketUtil.ERR_ACCESS_VIOLATION, "ACCESS VIOLATION, File is locked, can not access");
			PacketUtil.sendPacketToProcess(getName()+": ", sendReceiveSocket, errPacket, ProcessType.CLIENT, "ERROR");
			return;
		}
		
		if (!lock.isReadLock(filename)){
			lock.addReader(filename);
		}
		
		//\\//\\//\\ File Not Found - Error Code 1 //\\//\\//\\

		//Opens an input stream
		File f = new File(getDirectory().concat("\\" + filename));
		Path path = Paths.get(getDirectory().concat("\\" + filename));
		
		if(!f.exists()){    //file doesn't exist

			byte errorCode = PacketUtil.ERR_FILE_NOT_FOUND;   //error code 1 : file not found
			DatagramPacket error= OPcodeError.OPerror("SERVER: FILE(" + filename + ") NOT FOUND",errorCode);  //create error packet
		System.out.println(getDirectory());
			error.setAddress(reqPacket.getAddress());
			error.setPort(reqPacket.getPort());
			
			PacketUtil.sendPacketToProcess(getName()+": ", sendReceiveSocket, error, ProcessType.CLIENT, "ERROR");
			
			cleanup();
			lock.deleteReader(filename);
			return;
		}
		
		if(!Files.isReadable(path)){    // no read access

			byte errorCode = PacketUtil.ERR_ACCESS_VIOLATION;   //error code 2 : access violation
			DatagramPacket error= OPcodeError.OPerror("ACCESS VIOLATION",errorCode);  //create error packet
			error.setAddress(reqPacket.getAddress());
			error.setPort(reqPacket.getPort());		

			PacketUtil.sendPacketToProcess(getName()+": ", sendReceiveSocket, error, ProcessType.CLIENT, "ERROR");			
			
			cleanup();	
			lock.deleteReader(filename);
			return;
		}
		
		// request is good if we made it here
		// read request, so start a file transfer
		Sender s = new Sender(this, ProcessType.CLIENT, sendReceiveSocket,clientIP, clientPort);
		try {			
			s.sendFile(f);
			printToConsole("Finished read request for file: " + f.getName());
		} catch (TFTPException e) {
			printToConsole(String.format("ERROR: (%d) %s\n", e.getErrorCode(), e.getMessage()));
			if (e.getErrorCode() != PacketUtil.ERR_UNKNOWN_TID) {
				cleanup();
				lock.deleteReader(filename);
				return;
			}
		}
		
		lock.deleteReader(filename);
		
		cleanup();

	}

	//get functions
	public String getDirectory(){return directory;}
	
	//set functions
	public void setDirectory(String aDirectory){directory = aDirectory;}

}