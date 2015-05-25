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
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;

import tftp.Logger;
import tftp.exception.ErrorReceivedException;
import tftp.exception.TFTPException;
import tftp.exception.TFTPFileIOException;
import tftp.exception.TFTPPacketException;

public class Sender {

	private FileInputStream fileReader;
	private DatagramSocket socket;
	private int fileLength, bytesRead;
	private InetAddress receiverIP;	
	private PacketUtil packetUtil;	
	private Logger logger;
	private PacketParser parser;

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
		logger = Logger.getInstance();
	}

	public void sendFile(File theFile) throws IOException, TFTPException {
		fileReader = new FileInputStream(theFile);
		fileLength = fileReader.available();
		int blockNum = 1;

		logger.debug("*** Filename: " + theFile.getName() + " ***");
		logger.debug("*** Bytes to send: " + fileLength + " ***");

		byte[] sendBuf = new byte[512]; // need to make this exactly our block size so we only read that much
		byte[] recvBuf = new byte[PacketUtil.BUF_SIZE];

		boolean done = false;
		do		
		{
			bytesRead = fileReader.read(sendBuf);
			if (bytesRead == -1) {
				bytesRead = 0;				
			}
			if (bytesRead < 512) {
				done = true;
			}
			DatagramPacket sendPacket = packetUtil.formDataPacket(sendBuf, bytesRead, blockNum);


			logger.debug(String.format("Sending segment %d with %d byte payload.", blockNum, bytesRead));

			socket.send(sendPacket);
			logger.logPacketInfo(sendPacket, true);

			DatagramPacket reply = new DatagramPacket(recvBuf, recvBuf.length);

			// expect an ACK from the other side
			try
			{
				logger.debug(String.format("waiting for ACK %d", blockNum));
				socket.receive(reply);                
				logger.logPacketInfo(reply, false);

				// parse ACK to ensure it is correct before continuing
				try {
					parser.parseAckPacket(reply, blockNum);

				} catch (TFTPPacketException e) {

					logger.error(e.getMessage());

					// send error packet
					DatagramPacket errPacket = null;
					
					if (e.getErrorCode() == PacketUtil.ERR_UNKNOWN_TID) {
						// address packet to the unknown TID
						errPacket = packetUtil.formErrorPacket(e.getErrorCode(), e.getMessage(),
								reply.getAddress(), reply.getPort());						
					} else {
						// packet will be addressed to recipient as usual					
						errPacket = packetUtil.formErrorPacket(e.getErrorCode(), e.getMessage());
					}
					
					try {			   
						socket.send(errPacket);			   
					} catch (IOException ex) {			   
						logger.error(ex.getMessage());						
						throw ex;
					}
					// rethrow so the owner of this Sender knows whats up
					throw e;

				} catch (TFTPFileIOException e) {

					logger.error(e.getMessage());

					// send error packet
					DatagramPacket errPacket = packetUtil.formErrorPacket(e.getErrorCode(), e.getMessage());
					try {			   
						socket.send(errPacket);			   
					} catch (IOException ex) {			   
						logger.error(ex.getMessage());
						return;
					}
					
					// rethrow so the owner of this Sender knows whats up
					throw e;

				} catch (ErrorReceivedException e) {
					// the client sent an error packet, so in most cases don't send a response

					logger.error(e.getMessage());

					if (e.getErrorCode() == PacketUtil.ERR_UNKNOWN_TID) {
						// send error packet to the unknown TID
						DatagramPacket errPacket = packetUtil.formErrorPacket(e.getErrorCode(), e.getMessage());
						try {			   
							socket.send(errPacket);			   
						} catch (IOException ex) {			   
							logger.error(ex.getMessage());
							throw e;
						}
					}
					// rethrow so the owner of this Sender knows whats up
					throw e;

				} catch (TFTPException e) {
					// this block shouldn't get executed, but needs to be here to compile
					logger.error(e.getMessage());
					// rethrow so the owner of this Sender knows whats up
					throw e;
				}
			} catch (SocketTimeoutException e) {
				// we are assuming no network errors for now, so ignore this case
				logger.error(e.getMessage());
				return;
			}

			// verify ack
			int ackNum = packetUtil.parseAckPacket(reply);
			if (ackNum != blockNum) {
				// TODO: handle out of sequence block
			}

			blockNum++;

		} while (!done);

		logger.debug("*** finished transfer ***");
		logger.debug("========================================\n");
	}

}
