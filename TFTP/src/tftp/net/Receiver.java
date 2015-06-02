/*
 * Receiver.java
 * 
 * Authors: TEAM 1
 * 
 * This file was created specifically for the course SYSC 3303.
 */

package tftp.net;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;

import tftp.exception.ErrorReceivedException;
import tftp.exception.TFTPException;
import tftp.server.thread.WorkerThread;
import tftp.sim.ErrorSimUtil;
import tftp.sim.PacketType;

public class Receiver
{    
	private final static int DEFAULT_RETRY_TRANSMISSION = 2;

	private DatagramSocket socket;

	private InetAddress senderIP;
	private PacketUtil packetUtil;
	static String filename; 			//name of the file
	private PacketParser packetParser;
	private FileOutputStream fileWriter = null;
	private byte[] data;

	private WorkerThread ownerThread = null; // whatever server thread is using this object

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

	// extra constructor to allow the Receiver to print messages in the context of a server thread
	public Receiver(WorkerThread ownerThread, DatagramSocket socket, int senderPort){
		this(socket, senderPort);		
		this.ownerThread = ownerThread;
	}

	public void receiveFile(DatagramPacket initPacket, File aFile) throws TFTPException {

		int blockNum = 1;
		int oldBlockNum = 1;
		boolean duplicatePacket = false;
		
		// parse the first DATA packet and see if we even need to continue
		try {
			printToConsole("RECEIVER: about to parse DATA packet, expected block number " + oldBlockNum);
			packetParser.parseDataPacket(initPacket, oldBlockNum);
			blockNum = ErrorSimUtil.getBlockNumber(initPacket);

		} catch (ErrorReceivedException e) {
			// the other side sent an error packet, so in most cases don't send a response

			// we could have gotten an error packet from an unknown TID, so we need to respond to that TID
			if (e.getErrorCode() == PacketUtil.ERR_UNKNOWN_TID) {

				DatagramPacket errPacket = packetUtil.formErrorPacket(e.getErrorCode(), e.getMessage());
				// address packet to the unknown TID
				errPacket = packetUtil.formErrorPacket(e.getErrorCode(), e.getMessage(),
						initPacket.getAddress(), initPacket.getPort());
				try {			   
					socket.send(errPacket);
				} catch (IOException ex) { 
					throw new TFTPException(ex.getMessage(), PacketUtil.ERR_UNDEFINED);
				}
			}

			// rethrow so the owner of this Receiver knows whats up
			throw e;

		} catch (TFTPException e) {

			// send error packet
			DatagramPacket errPacket = null;

			if (e.getErrorCode() == PacketUtil.ERR_UNKNOWN_TID) {
				// address packet to the unknown TID
				errPacket = packetUtil.formErrorPacket(e.getErrorCode(), e.getMessage(),
						initPacket.getAddress(), initPacket.getPort());						
			} else {
				// packet will be addressed to recipient as usual					
				errPacket = packetUtil.formErrorPacket(e.getErrorCode(), e.getMessage());
			}

			try {			   
				socket.send(errPacket);			   
			} catch (IOException ex) {			   
				throw new TFTPException(ex.getMessage(), PacketUtil.ERR_UNDEFINED);
			}

			// rethrow so the owner of this Receiver knows whats up
			throw e;
		}

		createFile(aFile);
		writeToFile(data, initPacket);
		
		// create recv packet
		data = new byte[PacketUtil.BUF_SIZE];
		DatagramPacket receivePacket = new DatagramPacket(data, data.length);

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

			boolean PacketReceived = false;
			int retransmission = 0;

			while (!PacketReceived && retransmission <= DEFAULT_RETRY_TRANSMISSION){
				// wait for response
				printToConsole("waiting for next DATA segment...");
				try {			  
					socket.receive(receivePacket);
					PacketReceived = true;
					//
				} catch(SocketTimeoutException e){
					//response data packet not received, last ack packet may lost, resending...
					try {
						if (retransmission == 2){
							System.out.println("Can not complete tranfer file, teminated");
							return;
						}
						socket.send(sendPacket);
						retransmission++;
					} catch (IOException ew) {
						throw new TFTPException(ew.getMessage(), PacketUtil.ERR_UNDEFINED);
					}

				} catch(IOException ex) {
					ex.printStackTrace();
					System.exit(1);
				}
			}
			if (retransmission == DEFAULT_RETRY_TRANSMISSION){
				System.out.println("Can not complete tranfer file, terminated");
				return;
			}

			printToConsole(String.format("DATA %d received", blockNum));
			
			oldBlockNum = blockNum;
			// increment block number so we can check if received packet has expected block number
			blockNum++;

			// parse the response packet to ensure it is correct before continuing
			try {
				printToConsole("RECEIVER: about to parse DATA packet with block number " + blockNum);
				duplicatePacket = packetParser.parseDataPacket(receivePacket, blockNum);

			} catch (ErrorReceivedException e) {
				// the other side sent an error packet, so in most cases don't send a response

				// we could have gotten an error packet from an unknown TID, so we need to respond to that TID
				if (e.getErrorCode() == PacketUtil.ERR_UNKNOWN_TID) {

					DatagramPacket errPacket = packetUtil.formErrorPacket(e.getErrorCode(), e.getMessage());
					// address packet to the unknown TID
					errPacket = packetUtil.formErrorPacket(e.getErrorCode(), e.getMessage(),
							receivePacket.getAddress(), receivePacket.getPort());
					
					try {			   
						socket.send(errPacket);
					} catch (IOException ex) { 
						// TODO fix resource leak
						throw new TFTPException(ex.getMessage(), PacketUtil.ERR_UNDEFINED);
					}
					
					// rethrow so the owner of this Receiver knows whats up 
					// unless Unknown TID, in which case we continue receiving
					if (e.getErrorCode() != PacketUtil.ERR_UNKNOWN_TID)
						throw e;
				}

				// rethrow so the owner of this Receiver knows whats up
				throw e;

			} catch (TFTPException e) {

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
					socket.send(errPacket);			   
				} catch (IOException ex) {			   
					throw new TFTPException(ex.getMessage(), PacketUtil.ERR_UNDEFINED);
				}

				// rethrow so the owner of this Receiver knows whats up
				throw e;
			}

			// If duplicate data packet we will not write to file
			if (duplicatePacket){
				blockNum = oldBlockNum;		
			}
			else{
				checkDiskFull(aFile, receivePacket); // First check if disk is full
				writeToFile(data, receivePacket); 	 // If not write the data portion to the file
				
				if (receivePacket.getLength() < 516) {
					done = true;
				}

				// send ACK
				blockNum = packetUtil.parseDataPacket(receivePacket);
			}
			
			// BlockNum is verified, sending old block num if duplication of packet occured
			sendPacket = packetUtil.formAckPacket(blockNum);

			printToConsole(String.format("sending ACK %d", blockNum));

			try {
				socket.send(sendPacket);
				//
			} catch (IOException ea) {
				ea.printStackTrace();
				System.exit(1);
			}
		}
		closeFileWriter(fileWriter);
		printToConsole("*** finished transfer ***");
	} 

	private void closeFileWriter(FileOutputStream fileWriter) throws TFTPException {
		try {
			fileWriter.flush();
			fileWriter.close();
		} catch (IOException e) {
			throw new TFTPException("Error closing FileOutputStream: "+e.getMessage(), PacketUtil.ERR_UNDEFINED);
		}
	}

	private void createFile(File aFile) throws TFTPException{

		File theFile = aFile;

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
	}
	
	private void writeToFile(byte[] data, DatagramPacket receivePacket) throws TFTPException{
		
		int dataLength = receivePacket.getLength() - 4;

		// extract data portion			
		data = new byte[dataLength];
		System.arraycopy(receivePacket.getData(), 4, data, 0, dataLength);

		// write the data portion to the file
		try {
			fileWriter.write(data);
		} catch (IOException e) {
			throw new TFTPException(e.getMessage(), PacketUtil.ERR_UNDEFINED);
		}

	}
	
	private void checkDiskFull(File aFile, DatagramPacket receivePacket) throws TFTPException{
		
		//Check if the disk is already full, If full generate Error code-3
		//By Syed Taqi - 2015/05/08
		if (aFile.getUsableSpace() < receivePacket.getLength()){				
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
		
	}

	private void printToConsole(String message) {
		if (ownerThread != null) // server thread owns this object
			System.out.printf("%s: %s\n", ownerThread.getName(), message);
		else // client owns this object
			System.out.printf("%s\n", message);
	}
}