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

import tftp.Config;
import tftp.exception.ErrorReceivedException;
import tftp.exception.InvalidRequestException;
import tftp.exception.TFTPException;
import tftp.exception.TFTPFileIOException;
import tftp.exception.TFTPPacketException;
import tftp.net.PacketUtil;
import tftp.net.Receiver;

/**
 * A specialized thread that processes TFTP write requests received by a TFTP server.
 */
public class WriteHandlerThread extends WorkerThread {
	
	/**
	 * Constructs a WriteHandlerThread. Passes the DatagramPacket argument to 
	 * the WorkerThread constructor. 
	 *
	 * @param  reqPacket  the packet containing the client's request
	 */
	public WriteHandlerThread(DatagramPacket reqPacket) {
		super(reqPacket);
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
			
		} catch (TFTPPacketException e) {
			
			
			// send error packet
			DatagramPacket errPacket = packetUtil.formErrorPacket(e.getErrorCode(), e.getMessage());
			try {			   
				sendReceiveSocket.send(errPacket);			   
			} catch (IOException ex) {	
				System.out.printf("ERROR: IOException: %s\n", ex.getMessage());
				return;
			}
			return;
			
		} catch (TFTPFileIOException e) {
			System.out.printf("ERROR: (%d) %s\n", e.getErrorCode(), e.getMessage());
			
			// send error packet to client
			DatagramPacket errPacket = packetUtil.formErrorPacket(e.getErrorCode(), e.getMessage());
			try {			   
				sendReceiveSocket.send(errPacket);			   
			} catch (IOException ex) {		
				System.out.printf("ERROR: IOException: %s\n", ex.getMessage());
				return;
			}
			return;
			
		} catch (ErrorReceivedException e) {
			// the client sent an error packet, so in most cases don't send a response
			System.out.println("Error packet received from client!");
			System.out.printf("ERROR: (%d) %s\n", e.getErrorCode(), e.getMessage());
			
			if (e.getErrorCode() == PacketUtil.ERR_UNKNOWN_TID) {
				// send error packet to the unknown TID
				DatagramPacket errPacket = packetUtil.formErrorPacket(e.getErrorCode(), e.getMessage());
				try {			   
					sendReceiveSocket.send(errPacket);			   
				} catch (IOException ex) {	
					ex.printStackTrace();
					System.out.printf("ERROR: IOException: %s\n", ex.getMessage());
					return;
				}
			} else return;
			
		} catch (TFTPException e) {
			System.out.printf("ERROR: (%d) %s\n", e.getErrorCode(), e.getMessage());
			// this block shouldn't get executed, but needs to be here to compile
		}
				
			
		// validate file name		
		int i = 2;
		StringBuilder sb = new StringBuilder();
		while (data[i] != 0x00) {
			sb.append((char)data[i]);
			// reject non-printable values
			if (data[i] < 0x20 || data[i] > 0x7F)
				throw new InvalidRequestException(
						String.format("non-printable data inside file name: byte %d",i));			
			i++;
		}
		
		String fullpath = Config.getServerDirectory() + filename;
		
		File f = new File(fullpath);
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

		// move index to start of mode string
		i++;		

		// validate mode string
		sb = new StringBuilder();
		while (data[i] != 0x00) {
			sb.append((char)data[i]);			
			i++;
		}

		String mode = sb.toString();
		if (! (mode.toLowerCase().equals("netascii") || mode.toLowerCase().equals("octet")) )
			throw new InvalidRequestException("invalid mode");		

		// should be at end of packet
		if (i+1 != reqPacket.getLength())
			throw new InvalidRequestException("incorrect packet length");

		// request is good if we made it here
		// write request, so send an ACK 0
		DatagramSocket sendRecvSocket = null;
		try {
			sendRecvSocket = new DatagramSocket();
		} catch (SocketException se) {
			se.printStackTrace();
			System.exit(1);
		}
		
		
		DatagramPacket initAck = packetUtil.formAckPacket(0);
		
		
		try {
			sendRecvSocket.send(initAck);
		} catch (IOException e) {
			System.out.printf("ERROR: IOException: %s\n", e.getMessage());
			e.printStackTrace();
		}
		
		// get the first data packet so we can set up receiver
		data = new byte[PacketUtil.BUF_SIZE];
		receivePacket = new DatagramPacket(data, data.length);
		try {
			sendRecvSocket.receive(receivePacket);
		} catch (IOException e) {
			e.printStackTrace();
		}
		
		
		Receiver r = new Receiver(sendRecvSocket, receivePacket.getPort());
		try {
			r.receiveFile(receivePacket, Config.getServerDirectory(), filename);
		} catch (TFTPException e) {
			System.out.printf("ERROR: (%d) %s\n", e.getErrorCode(), e.getMessage());
		} finally {		
			cleanup();
		}
	}

}
