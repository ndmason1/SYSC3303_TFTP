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
import java.util.Arrays;

import tftp.exception.ErrorReceivedException;
import tftp.exception.TFTPException;
import tftp.server.thread.WorkerThread;


public class Receiver
{    
	private final static int DEFAULT_RETRY_TRANSMISSION = 2;

	private DatagramSocket socket;

	private InetAddress senderIP;
	private PacketUtil packetUtil;
	private PacketParser packetParser;
	private FileOutputStream fileWriter = null;
	private byte[] data;

	private WorkerThread ownerThread = null;	// whatever server thread is using this object
	private ProcessType senderProcess; 			// process that is controlling the Sender to this Receiver
	private String threadLabel = "";			// identify the owner thread when sending ACK

	public Receiver(ProcessType senderProcess, DatagramSocket socket,InetAddress ip, int senderPort){		
	
		senderIP = ip;
		this.socket = socket;	
		packetUtil = new PacketUtil(senderIP, senderPort);
		packetParser = new PacketParser(senderIP, senderPort);
		
		this.senderProcess = senderProcess;		

	}

	// extra constructor to allow the Receiver to print messages in the context of a server thread
	public Receiver(WorkerThread ownerThread, ProcessType senderProcess, DatagramSocket socket,InetAddress ip, int senderPort){
		this(senderProcess, socket,ip, senderPort);		
		this.ownerThread = ownerThread;
		threadLabel = ownerThread.getName() + ": ";
	}

	public void receiveFile(DatagramPacket initPacket, File aFile) throws TFTPException {

		int blockNum = 1;
		int oldBlockNum = 1;
		boolean duplicatePacket = false;
		
		createFile(aFile);
		checkDiskFull(aFile, initPacket);
		writeToFile(data, initPacket);
		
		// recv packet, initialize buffer so clearing it doesn't fail
		data = new byte[PacketUtil.BUF_SIZE];
		DatagramPacket receivePacket = null;

		// send ACK for initial data packet
		DatagramPacket sendPacket = packetUtil.formAckPacket(blockNum);
		PacketUtil.sendPacketToProcess(threadLabel, socket, sendPacket, senderProcess, "ACK");
		
		// check if we are done
		boolean done = initPacket.getLength() < 516;		

		while (!done) {
			
			// expect next DATA to have increased block number
			oldBlockNum = blockNum;
			blockNum++;

			boolean packetReceived = false;
			int retransmission = 0;

			while (!packetReceived && retransmission <= DEFAULT_RETRY_TRANSMISSION){
				
				// zero the receive buffer so no lingering data is detected
				Arrays.fill(data, (byte)0);
				
				try {
					receivePacket = PacketUtil.receivePacketOrTimeout(threadLabel, socket, senderProcess, "DATA");
					packetReceived = true;
					
				} catch(SocketTimeoutException e){
					
					printToConsole("Error: Response data packet not received, last ack packet may lost, resending...");
					
					if (retransmission == DEFAULT_RETRY_TRANSMISSION){
						System.out.println("Can not complete tranfer file, terminated");
						return;
					}
					if (!duplicatePacket) {
						PacketUtil.sendPacketToProcess(threadLabel, socket, sendPacket, senderProcess, "ACK");
						retransmission++;
					} 
				}
			
			}

			// parse the response packet to ensure it is correct before continuing
			try {
				duplicatePacket = packetParser.parseDataPacket(receivePacket, blockNum);

			} catch (ErrorReceivedException e) {
				// the other side sent an error packet, don't send a response
				// rethrow so the owner of this Receiver knows whats up
				printToConsole(String.format("ERROR packet received from %s!", senderProcess.name().toLowerCase()));				
				throw e;

			} catch (TFTPException e) {

				// send error packet
				DatagramPacket errPacket = null;
				
				errPacket = packetUtil.formErrorPacket(e.getErrorCode(), e.getMessage(),
						receivePacket.getAddress(), receivePacket.getPort());

				PacketUtil.sendPacketToProcess(threadLabel, socket, errPacket, senderProcess, "ERROR");
				
				// keep going if error was unknown TID
				// otherwise, rethrow so the client UI can print a message
				if (e.getErrorCode() == PacketUtil.ERR_UNKNOWN_TID) {
					oldBlockNum--;
					blockNum--;
					continue;
				}
				else
					throw e;
			}

			// If duplicate data packet we will not write to file
			if (duplicatePacket){
				blockNum = oldBlockNum;
				
			} else {
				
				checkDiskFull(aFile, receivePacket); // First check if disk is full
				writeToFile(data, receivePacket); 	 // If not write the data portion to the file
				
				if (receivePacket.getLength() < 516) {
					done = true;
				}

			}
			
			// blockNum is verified, sending old block num if duplication of packet occured
			sendPacket = packetUtil.formAckPacket(blockNum);			
			PacketUtil.sendPacketToProcess(threadLabel, socket, sendPacket, senderProcess, "ACK");
			
		}
		
		closeFileWriter(fileWriter);
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

			PacketUtil.sendPacketToProcess(threadLabel, socket, error, senderProcess, "ERROR");
				
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