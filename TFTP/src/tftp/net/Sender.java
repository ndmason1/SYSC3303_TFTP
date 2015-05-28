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
	}

	public void sendFile(File theFile) throws TFTPException {
		try {
			fileReader = new FileInputStream(theFile);
		} catch (FileNotFoundException e) {
			throw new TFTPException(e.getMessage(), PacketUtil.ERR_FILE_NOT_FOUND);
		}
		
		try {
			fileLength = fileReader.available();
		} catch (IOException e) {
			throw new TFTPException(e.getMessage(), PacketUtil.ERR_UNDEFINED);
		}
		
		int blockNum = 1;

		byte[] sendBuf = new byte[512]; // need to make this exactly our block size so we only read that much
		byte[] recvBuf = new byte[PacketUtil.BUF_SIZE];

		boolean done = false;
		do		
		{
			try {
				bytesRead = fileReader.read(sendBuf);
			} catch (IOException e) {
				throw new TFTPException(e.getMessage(), PacketUtil.ERR_UNDEFINED);
			}
			if (bytesRead == -1) {
				bytesRead = 0;				
			}
			if (bytesRead < 512) {
				done = true;
			}
			DatagramPacket sendPacket = packetUtil.formDataPacket(sendBuf, bytesRead, blockNum);

			try {
				socket.send(sendPacket);
			} catch (IOException e) {
				throw new TFTPException(e.getMessage(), PacketUtil.ERR_UNDEFINED);
			}

			DatagramPacket reply = new DatagramPacket(recvBuf, recvBuf.length);

			// expect an ACK from the other side

			try {
				socket.receive(reply);
			} catch (IOException e) {
				throw new TFTPException(e.getMessage(), PacketUtil.ERR_UNDEFINED);
			}                

			// parse ACK to ensure it is correct before continuing
			try {
				parser.parseAckPacket(reply, blockNum);

			} catch (TFTPPacketException e) {

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
					throw new TFTPException(ex.getMessage(), PacketUtil.ERR_UNDEFINED);
				}
				// rethrow so the owner of this Sender knows whats up
				throw e;

			} catch (TFTPFileIOException e) {

				// send error packet
				DatagramPacket errPacket = packetUtil.formErrorPacket(e.getErrorCode(), e.getMessage());
				try {			   
					socket.send(errPacket);			   
				} catch (IOException ex) {			   
					return;
				}
				
				// rethrow so the owner of this Sender knows whats up
				throw e;

			} catch (ErrorReceivedException e) {
				// the client sent an error packet, so in most cases don't send a response

				if (e.getErrorCode() == PacketUtil.ERR_UNKNOWN_TID) {
					// send error packet to the unknown TID
					DatagramPacket errPacket = packetUtil.formErrorPacket(e.getErrorCode(), e.getMessage());
					try {			   
						socket.send(errPacket);			   
					} catch (IOException ex) {			   
						throw e;
					}
				}
				// rethrow so the owner of this Sender knows whats up
				throw e;

			} catch (TFTPException e) {
				// this block shouldn't get executed, but needs to be here to compile
				// rethrow so the owner of this Sender knows whats up
				throw e;
			}

			// verify ack
			int ackNum = packetUtil.parseAckPacket(reply);
			if (ackNum != blockNum) {
				// TODO: handle out of sequence block
			}

			blockNum++;

		} while (!done);
	}

}
