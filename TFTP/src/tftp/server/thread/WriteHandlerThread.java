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
import java.net.DatagramSocket;
import java.net.SocketException;

import tftp.exception.ErrorReceivedException;
import tftp.exception.TFTPException;
import tftp.net.PacketUtil;
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
	}
	
	/**
	 * Runs this thread, which processes a TFTP write request and starts a 
	 * file transfer.
	 */
	@Override
	public void run() {
		
		System.out.println("Started read handler thread");
		
		byte[] data = reqPacket.getData();
		
		PacketUtil packetUtil = new PacketUtil(reqPacket.getAddress(), reqPacket.getPort());
		String filename = null;
		
		// parse the request packet to ensure it is correct before starting the transfer
		try {
			filename = packetParser.parseWRQPacket(reqPacket);
			
		} catch (ErrorReceivedException e) {
			// the other side sent an error packet, so in most cases don't send a response
						
			printToConsole("ERROR packet received from Client!");
			printToConsole(String.format("ERROR: (%d) %s\n", e.getErrorCode(), e.getMessage()));

			// we could have gotten an error packet from an unknown TID, so we need to respond to that TID
			if (e.getErrorCode() == PacketUtil.ERR_UNKNOWN_TID) {
				
				DatagramPacket errPacket = packetUtil.formErrorPacket(e.getErrorCode(), e.getMessage());
				// address packet to the unknown TID
				errPacket = packetUtil.formErrorPacket(e.getErrorCode(), e.getMessage(),
						receivePacket.getAddress(), receivePacket.getPort());
				try {			   
					sendReceiveSocket.send(errPacket);
				} catch (IOException ex) { 
					printToConsole("Error occured sending ERROR packet to unknown TID");
					ex.printStackTrace();
					cleanup();
					return;
				}
			}
			
			printToConsole("request cannot be processed, ending this thread");			
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
		// write request, so send an ACK 0
		DatagramSocket sendRecvSocket = null;
		try {
			sendRecvSocket = new DatagramSocket();
		} catch (SocketException se) {
			printToConsole("Error occured creating socket for write request");
			se.printStackTrace();
			cleanup();
			return;
		}
		
		System.out.println("seding ACK 0");
		DatagramPacket initAck = packetUtil.formAckPacket(0);
		try {
			sendRecvSocket.send(initAck);
		} catch (IOException e) {
			printToConsole("Error occured sending ACK 0 packet");
			e.printStackTrace();
			cleanup();
			return;
		}
		
		// get the first data packet so we can set up receiver
		data = new byte[PacketUtil.BUF_SIZE];
		receivePacket = new DatagramPacket(data, data.length);
		try {
			sendRecvSocket.receive(receivePacket);
		} catch (IOException e) {
			e.printStackTrace();
		}
		
		
		Receiver r = new Receiver(this, sendRecvSocket, receivePacket.getPort());
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
