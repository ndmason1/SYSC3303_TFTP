/*
 * Receiver.java
 * 
 * Authors: TEAM 1
 * 
 * This file was created specifically for the course SYSC 3303.
 */

package tftp.net;

import java.io.*;
import java.net.*;
import java.util.*;
import java.io.File;

import tftp.exception.ErrorReceivedException;
import tftp.exception.TFTPException;
import tftp.exception.TFTPFileIOException;
import tftp.exception.TFTPPacketException;
import tftp.server.thread.OPcodeError;

public class Receiver
{    
	private DatagramSocket socket;

	private InetAddress senderIP;
	private PacketUtil packetUtil;
	static String filename; 			//name of the file
	private PacketParser packetParser;

	public Receiver(DatagramSocket socket, int senderPort){		

		try {
			senderIP = InetAddress.getLocalHost();
		} catch (UnknownHostException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		this.socket = socket;	
		packetUtil = new PacketUtil(senderIP, senderPort);
		packetParser = new PacketParser(senderIP, senderPort);
		
	}

	public void receiveFile(DatagramPacket initPacket, File aFile) throws TFTPException {
		System.out.println("RECEIVER: top of receiveFile()");
		
		// parse the first DATA packet and see if we even need to set up 
		
		
		File theFile = aFile;
		FileOutputStream fileWriter = null;
		try { // outer try with finally block so fileWriter gets closed

			try {
				fileWriter = new FileOutputStream(theFile);
			} catch (FileNotFoundException e) {
				throw new TFTPException(e.getMessage(), PacketUtil.ERR_UNDEFINED);
			} 

			// if file doesn't exist, then create it
			if (!theFile.exists()) {
				try {
					theFile.createNewFile();
				} catch (IOException e) {
					throw new TFTPException(e.getMessage(), PacketUtil.ERR_UNDEFINED);
				}
			}

			// extract data portion
			int dataLength = initPacket.getLength() - 4;
			byte[] data = new byte[dataLength];
			System.arraycopy(initPacket.getData(), 4, data, 0, dataLength);
			// write the data portion to the file
			try {
				fileWriter.write(data);
			} catch (IOException e) {
				throw new TFTPException(e.getMessage(), PacketUtil.ERR_UNDEFINED);
			}

			// create recv packet
			data = new byte[PacketUtil.BUF_SIZE];
			DatagramPacket receivePacket = new DatagramPacket(data, data.length);

			int blockNum = 1;
			packetParser.parseDataPacket(initPacket, blockNum);

			// send ACK for initial data packet
			DatagramPacket sendPacket = packetUtil.formAckPacket(blockNum);

			try {
				socket.send(sendPacket);
			} catch (IOException e) {
				throw new TFTPException(e.getMessage(), PacketUtil.ERR_UNDEFINED);
			}

			// check if we are done
			boolean done = initPacket.getLength() < 516;		

			while (!done) {
				// wait for response
				System.out.println("waiting for next DATA segment...");
				try {			  
					socket.receive(receivePacket);
					//
				} catch(IOException ex) {
					ex.printStackTrace();
					System.exit(1);
				}
				System.out.println(String.format("DATA %d received", blockNum));
				
				
				// increment block number so we can check if received packet has expected block number
				blockNum++;
				
				// parse the response packet to ensure it is correct before continuing
				try {
					System.out.println("RECEIVER: about to parse DATA packet with block number " + blockNum);
					packetParser.parseDataPacket(receivePacket, blockNum);

				} catch (TFTPPacketException e) {
					e.printStackTrace();

					// send error packet
					DatagramPacket errPacket = packetUtil.formErrorPacket(e.getErrorCode(), e.getMessage());
					try {			   
						socket.send(errPacket);			   
					} catch (IOException ex) {			
						ex.printStackTrace();
						return;
					}
					return;

				} catch (TFTPFileIOException e) {
					e.printStackTrace();

					// send error packet to client
					DatagramPacket errPacket = packetUtil.formErrorPacket(e.getErrorCode(), e.getMessage());
					try {			   
						socket.send(errPacket);			   
					} catch (IOException ex) {		
						ex.printStackTrace();
						return;
					}
					return;

				} catch (ErrorReceivedException e) {
					// the client sent an error packet, so in most cases don't send a response
					e.printStackTrace();

					if (e.getErrorCode() == PacketUtil.ERR_UNKNOWN_TID) {
						// send error packet to the unknown TID
						DatagramPacket errPacket = packetUtil.formErrorPacket(e.getErrorCode(), e.getMessage());
						try {			   
							socket.send(errPacket);			   
						} catch (IOException ex) {	
							ex.printStackTrace();
							return;
						}
					} else return;

				} catch (TFTPException e) {
					e.printStackTrace();
					// this block shouldn't get executed, but needs to be here to compile
				}
				//Check if the disk is already full, If full generate Error code-3
				//By Syed Taqi - 2015/05/08
				if (theFile.getUsableSpace() < receivePacket.getLength()){				
					String msg = "Disk full, Can not complete transfer, Disk cleanup required";

					byte errorCode = 3;
					DatagramPacket error = packetUtil.formErrorPacket(errorCode, "DISK FULL");	

					try {			   
						socket.send(error);			   
					} catch (IOException ex) {			   
						throw new TFTPException(ex.getMessage(), PacketUtil.ERR_UNDEFINED);
					}	

					throw new TFTPException(msg, PacketUtil.ERR_DISK_FULL);

				}

				dataLength = receivePacket.getLength() - 4;

				// extract data portion			
				data = new byte[dataLength];
				System.arraycopy(receivePacket.getData(), 4, data, 0, dataLength);
				// write the data portion to the file
				try {
					fileWriter.write(data);
				} catch (IOException e) {
					throw new TFTPException(e.getMessage(), PacketUtil.ERR_UNDEFINED);
				}

				if (receivePacket.getLength() < 516) {
					done = true;
				}

				// send ACK
				blockNum = packetUtil.parseDataPacket(receivePacket);
				// TODO verify block num
				sendPacket = packetUtil.formAckPacket(blockNum);
				System.out.println(String.format("sending ACK %d", blockNum));		
				

				try {
					socket.send(sendPacket);
					//
				} catch (IOException ea) {
					ea.printStackTrace();
					System.exit(1);
				}
			}
			System.out.println("*** finished transfer ***");
		} finally {
			closeFileWriter(fileWriter);
		}

	}

	private void closeFileWriter(FileOutputStream fileWriter) throws TFTPException {
		try {
			fileWriter.flush();
			fileWriter.close();
		} catch (IOException e) {
			throw new TFTPException(e.getMessage(), PacketUtil.ERR_UNDEFINED);
		}
	}
}