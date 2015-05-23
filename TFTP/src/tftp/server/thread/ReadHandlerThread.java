/*
 * ReadHandlerThread.java
 * 
 * Authors: TEAM 1
 * 
 * This file was created specifically for the course SYSC 3303.
 */

package tftp.server.thread;

import java.io.File;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.SocketException;

import tftp.Config;
import tftp.exception.ErrorReceivedException;
import tftp.exception.InvalidRequestException;
import tftp.exception.TFTPException;
import tftp.exception.TFTPFileIOException;
import tftp.exception.TFTPPacketException;
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
	public ReadHandlerThread(DatagramPacket reqPacket) {
		super(reqPacket);
	}	

	/**
	 * Runs this thread, which processes a TFTP read request and starts a 
	 * file transfer.
	 */
	@Override
	public void run() {
		byte[] data = reqPacket.getData();
		PacketUtil packetUtil = new PacketUtil(reqPacket.getAddress(), reqPacket.getPort());
		String filename = null;
		
		// parse the request packet to ensure it is correct before starting the transfer
		try {
			filename = packetParser.parseRRQPacket(reqPacket);
			
		} catch (TFTPPacketException e) {
			
			logger.error(e.getMessage());
			
			// send error packet
			DatagramPacket errPacket = packetUtil.formErrorPacket(e.getErrorCode(), e.getMessage());
			try {			   
				sendReceiveSocket.send(errPacket);			   
			} catch (IOException ex) {			   
				logger.error(ex.getMessage());
				return;
			}
			return;
			
		} catch (TFTPFileIOException e) {
			
			logger.error(e.getMessage());
			
			// send error packet to client
			DatagramPacket errPacket = packetUtil.formErrorPacket(e.getErrorCode(), e.getMessage());
			try {			   
				sendReceiveSocket.send(errPacket);			   
			} catch (IOException ex) {			   
				logger.error(ex.getMessage());
				return;
			}
			return;
			
		} catch (ErrorReceivedException e) {
			
			logger.error(e.getMessage());
			
			if (e.getErrorCode() == PacketUtil.ERR_UNKNOWN_TID) {
				// send error packet to the unknown TID
				DatagramPacket errPacket = packetUtil.formErrorPacket(e.getErrorCode(), e.getMessage());
				try {			   
					sendReceiveSocket.send(errPacket);			   
				} catch (IOException ex) {			   
					logger.error(ex.getMessage());
					return;
				}
			} else return;
			
		} catch (TFTPException e) {
			// this block shouldn't get executed, but needs to be here to compile
			logger.error(e.getMessage());
		}
		
		String fullpath = Config.getServerDirectory() + filename;
		
		//\\//\\//\\ File Not Found - Error Code 1 //\\//\\//\\

		//Opens an input stream
		File f = new File(fullpath);
		if(!f.exists()){    //file doesn't exist

			byte errorCode = 1;   //error code 1 : file not found
			DatagramPacket error= OPcodeError.OPerror("FILE NOT FOUND",errorCode);  //create error packet
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
		
		if(!f.canRead()){    // no read access

			byte errorCode = 2;   //error code 2 : access violation
			DatagramPacket error= OPcodeError.OPerror("ACCESS VIOLATION",errorCode);  //create error packet
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
		// read request, so start a file transfer
		Sender s = new Sender(sendReceiveSocket, clientPort);
		try {			
			s.sendFile(new File(fullpath));
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		cleanup();

	}

}