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
	}

	public void receiveFile(DatagramPacket initPacket, String directoryPath, String filename) throws TFTPException {
		
		File theFile = new File(directoryPath+filename);
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

			int blockNum = packetUtil.parseDataPacket(initPacket);
			System.out.println(String.format("DATA %d received", blockNum));

			// send ACK for initial data packet
			DatagramPacket sendPacket = packetUtil.formAckPacket(blockNum);
			System.out.println(String.format("sending ACK %d", blockNum));

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


	public String getFolder(){
		return Folder;
	}


}