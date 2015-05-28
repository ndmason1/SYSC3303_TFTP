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

import tftp.Logger;
import tftp.Util;
import tftp.exception.TFTPException;
import tftp.server.thread.OPcodeError;

public class Receiver
{    
	private DatagramSocket socket;

	private InetAddress senderIP;
	private PacketUtil packetUtil;
	private Logger logger;
	private String Folder = System.getProperty("user.dir")+"/Client_files";
	static String filename; 			//name of the file

	public Receiver(DatagramSocket socket, int senderTID){		

		try {
			senderIP = InetAddress.getLocalHost();
		} catch (UnknownHostException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		this.socket = socket;		
		packetUtil = new PacketUtil(senderIP, senderTID);
		logger = Logger.getInstance();
	}

	public void receiveFile(DatagramPacket initPacket, String directoryPath, String filename) throws TFTPException {
		logger.debug("first packet length: " + initPacket.getLength());
		logger.debug("first packet data length: " + initPacket.getData().length);
		System.out.println("First packet length" + initPacket.getLength());
		System.out.println("First data length" + initPacket.getData());

		File theFile = new File(directoryPath+filename);
		FileOutputStream fileWriter = null;
		try { // outer try with finally block so fileWriter gets closed

			try {
				fileWriter = new FileOutputStream(theFile);
			} catch (FileNotFoundException e) {
				System.out.println("Undefined Error");
				throw new TFTPException(e.getMessage(), PacketUtil.ERR_UNDEFINED);
			} 

			// if file doesn't exist, then create it
			if (!theFile.exists()) {
				try {
					theFile.createNewFile();
				} catch (IOException e) {
					throw new TFTPException(e.getMessage(), PacketUtil.ERR_UNDEFINED);
				} System.out.println("File Already Exists" + theFile.getName());
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

			int blockNum = packetUtil.parseDataPacket(initPacket);
			logger.debug(String.format("DATA %d received", blockNum));
			logger.logPacketInfo(initPacket, false);
			System.out.println("Data received" + blockNum);


			// send ACK for initial data packet
			DatagramPacket sendPacket = packetUtil.formAckPacket(blockNum);
			logger.debug(String.format("sending ACK %d", blockNum));			
			logger.logPacketInfo(sendPacket, true);
			System.out.println("Sending ACK" + blockNum);

			try {
				socket.send(sendPacket);
			} catch (IOException e) {
				throw new TFTPException(e.getMessage(), PacketUtil.ERR_UNDEFINED);
			}

			System.out.println("Sent ACK Packet for file" + theFile.getName());
			System.out.println("With Opcode of" + sendPacket.getData()[0] + sendPacket.getData()[4]);
			System.out.println("Packet Length of" + sendPacket.getLength());
			System.out.println("to the port" + sendPacket.getPort());
			System.out.println("To the Ip Address" + sendPacket.getAddress());
			// check if we are done
			boolean done = initPacket.getLength() < 516;		

			while (!done) {
				// wait for response
				logger.debug("waiting for next DATA segment...");
				System.out.println("Waiting for next Data segment");
				try {			  
					socket.receive(receivePacket);
					//
				} catch(IOException ex) {
					ex.printStackTrace();
					System.exit(1);
				}
				logger.debug(String.format("DATA %d received", blockNum));
				logger.logPacketInfo(receivePacket, false);
				System.out.println("Data received for file" + theFile.getName());
				System.out.println("Data received with block number of: " + blockNum);
				System.out.println("with an Opcode of: " + receivePacket.getData()[0] + receivePacket.getData()[3]);
				System.out.println("Data received with Length of" + receivePacket.getLength());
				System.out.println("Data received from port" + receivePacket.getPort());



				//Check if the disk is already full, If full generate Error code-3
				//By Syed Taqi - 2015/05/08
				if (theFile.getUsableSpace() < receivePacket.getLength()){				
					String msg = "Disk full, Can not complete transfer, Disk cleanup required";

					byte errorCode = 3;
					DatagramPacket error = packetUtil.formErrorPacket(errorCode, "DISK FULL");	

					try {			   
						socket.send(error);			   
					} catch (IOException ex) {			   

						System.out.println(msg);	
						throw new TFTPException(ex.getMessage(), PacketUtil.ERR_UNDEFINED);

					}	

					logger.debug(msg);

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
				logger.debug(String.format("sending ACK %d", blockNum));			
				logger.logPacketInfo(sendPacket, true);

				System.out.println("Sending ACK with block number" + blockNum);
				System.out.println("For File" + theFile.getName());
				System.out.println("To the port" + sendPacket.getPort());
				System.out.println("To the IP address" + sendPacket.getAddress());
				System.out.println("With an OPCode of: " + sendPacket.getData()[0] + sendPacket.getData()[4]);
				System.out.println("With Packet Length of:" + sendPacket.getLength());

				try {
					socket.send(sendPacket);
					//
				} catch (IOException ea) {
					ea.printStackTrace();
					System.exit(1);
				}
			}
			logger.debug("*** finished transfer ***");
			System.out.println("====Transfer is finished====");
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


	public String getFolder(){
		return Folder;
	}


}