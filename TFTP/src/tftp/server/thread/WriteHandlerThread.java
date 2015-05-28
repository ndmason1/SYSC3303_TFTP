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
		byte[] data = reqPacket.getData();

		PacketUtil packetUtil = new PacketUtil(reqPacket.getAddress(), reqPacket.getPort());
		String filename = null;

		// parse the request packet to ensure it is correct before starting the transfer
		try {
			filename = packetParser.parseWRQPacket(reqPacket);

		} catch (TFTPPacketException e) {

			logger.error(e.getMessage());
			System.out.println("Sending Write Request  for the file" + filename);
			System.out.println("Sending Write Request with Opcode" + reqPacket.getData()[0] + reqPacket.getData()[2]);
			System.out.println("Sending Write Request to the Port" + reqPacket.getPort());
			System.out.println("Sending Write Request to an Ip address" + reqPacket.getAddress());

			// send error packet
			DatagramPacket errPacket = packetUtil.formErrorPacket(e.getErrorCode(), e.getMessage());
			try {			   
				sendReceiveSocket.send(errPacket);			   
			} catch (IOException ex) {			   
				logger.error(ex.getMessage());

				return;
			}
			System.out.println("Error packet sent to port, "+ errPacket.getPort());
			System.out.println("Error packet sent to port, "+ errPacket.getPort());
			System.out.println("Error Packet sent to IP Address: " + errPacket.getAddress());
			System.out.println("Error Packet sent with an Opcode: " + errPacket.getData()[0] + errPacket.getData()[5]);
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
			// the client sent an error packet, so in most cases don't send a response

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
		if(!f.canWrite()){    // no write access

			byte errorCode = 2;   //error code 2 : access violation
			DatagramPacket error= OPcodeError.OPerror("ACCESS VIOLATION",errorCode);  //create error packet
			error.setAddress(reqPacket.getAddress());
			error.setPort(reqPacket.getPort());		

			try {			   
				sendReceiveSocket.send(error);			   
			} catch (IOException ex) {			   
				ex.printStackTrace();
			}			   

			System.out.println("Violation of access occured, Error Code:" + errorCode);
			System.out.println("Opcode is: " + error.getData()[0] + error.getData()[2]);
			System.out.println("Error sent to port" + error.getPort());
			System.out.println("Error sent to IP address" + error.getAddress());

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

		logger.logPacketInfo(initAck, true);

		try {
			sendRecvSocket.send(initAck);
		} catch (IOException e) {
			// TODO Auto-generated catch block
			logger.error(e.getMessage());
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

		logger.logPacketInfo(receivePacket, false);

		Receiver r = new Receiver(sendRecvSocket, receivePacket.getPort());
		try {
			r.receiveFile(receivePacket, Config.getServerDirectory(), filename);
		} catch (TFTPException e) {
			logger.error(e.getMessage());
		} finally {		
			cleanup();
		}
	}

}
