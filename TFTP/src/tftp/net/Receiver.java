package tftp.net;

import java.io.*;
import java.net.*;
import java.util.*;
import java.io.File;

import tftp.Logger;
import tftp.Util;

public class Receiver
{    
	private DatagramSocket socket;

	private InetAddress senderIP;
	private PacketUtil packetUtil;
	private Logger logger;
	private String Folder = System.getProperty("user.dir")+"/Client_files";
	static String filename; 			//name of the file

	public Receiver(ISendReceiver owner, DatagramSocket socket, int senderTID){		

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

	public void receiveFile(DatagramPacket initPacket, String directoryPath, String filename) throws IOException {
		logger.debug("first packet length: " + initPacket.getLength());
		logger.debug("first packet data length: " + initPacket.getData().length);
		
		File theFile = new File(directoryPath+filename);
		FileOutputStream fileWriter = new FileOutputStream(theFile);
		
		// if file doesn't exist, then create it
		if (!theFile.exists()) {
			theFile.createNewFile();
		}
		
		// extract data portion
		int dataLength = initPacket.getLength() - 4;
		byte[] data = new byte[dataLength];
		System.arraycopy(initPacket.getData(), 4, data, 0, dataLength);
		// write the data portion to the file
		fileWriter.write(data);

		// create recv packet
		data = new byte[PacketUtil.BUF_SIZE];
		DatagramPacket receivePacket = new DatagramPacket(data, data.length);

		int blockNum = packetUtil.parseDataPacket(initPacket);
		logger.debug(String.format("DATA %d received", blockNum));
		logger.logPacketInfo(initPacket, false);

		// send ACK for initial data packet
		DatagramPacket sendPacket = packetUtil.formAckPacket(blockNum);
		logger.debug(String.format("sending ACK %d", blockNum));			
		logger.logPacketInfo(sendPacket, true);

		try {
			socket.send(sendPacket);
		} catch (IOException e) {
			e.printStackTrace();
			System.exit(1);
		}

		//Check if the disk is already full, If full generate Error code-3
		//Copyright- By Syed Taqi - 2015/05/08
//		
//
//		//String Pathfile = getFolder() + filename;
//		String Pathfile = directoryPath + filename;
//		try {
//			File file = new File(Pathfile);
//			System.out.println("PATH " + Pathfile);
//
//			do {
//				//getUsableSpace() definition taken from here:
//				//http://stackoverflow.com/questions/21439596/difference-between-getfreespace-and-getusablespace-of-file
//				//getUsableSpace() - This method returns the number of bytes available to the virtual 
//				//machine on the partition named by that abstract pathname
//
//
//				if (file.getUsableSpace() < receivePacket.getLength()){
//					socket.close();
//					logger.debug(String.format("(3) Disk full, Can not complete transfer, Disk cleanup required"));
//					throw new IOException("(3):Disk Full, Can not complete transfer, Disk cleanup required");
//
//				}
//
//
//			} while ( receivePacket.getLength() == 512);
//			socket.close();
//		} catch (IOException e){
//			e.printStackTrace();
//			System.exit(1);
//		}
		//-------DiskFull Code by Syed Taqi upto here----//

		// check if we are done
		boolean done = initPacket.getLength() < 516;
		

		while (!done) {
			// wait for response
			logger.debug("waiting for next DATA segment...");
			try {			  
				socket.receive(receivePacket);
				//
			} catch(IOException ex) {
				ex.printStackTrace();
				System.exit(1);
			}
			logger.debug(String.format("DATA %d received", blockNum));
			logger.logPacketInfo(receivePacket, false);
			
			dataLength = receivePacket.getLength() - 4;
			
			// extract data portion			
			data = new byte[dataLength];
			System.arraycopy(receivePacket.getData(), 4, data, 0, dataLength);
			// write the data portion to the file
			fileWriter.write(data);

			if (receivePacket.getLength() < 516) {
				done = true;
			}

			// send ACK
			blockNum = packetUtil.parseDataPacket(receivePacket);
			// TODO verify block num
			sendPacket = packetUtil.formAckPacket(blockNum);
			logger.debug(String.format("sending ACK %d", blockNum));			
			logger.logPacketInfo(sendPacket, true);

			try {
				socket.send(sendPacket);
				//
			} catch (IOException ea) {
				ea.printStackTrace();
				System.exit(1);
			}
		}
		logger.debug("*** finished transfer ***");
		fileWriter.flush();
		fileWriter.close();
	}


	public String getFolder(){
		return Folder;
	}


}