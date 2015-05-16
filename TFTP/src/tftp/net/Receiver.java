package tftp.net;

import java.io.*;
import java.net.*;
import java.util.*;

import tftp.Logger;
import tftp.Util;

public class Receiver
{    
    private DatagramSocket socket;
    
    private InetAddress senderIP;
    private PacketUtil packetUtil;
    private Logger logger;
    
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

    public void receiveFile(DatagramPacket initPacket) {
		
    	// create recv packet
		byte data[] = new byte[PacketUtil.BUF_SIZE];
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
		
		// check if we are done
    	boolean done = initPacket.getLength() < 516;
		
		while (!done) {
			// wait for response
			logger.debug("waiting for next DATA segment...");
			try {			  
				socket.receive(receivePacket);
			} catch(IOException e) {
				e.printStackTrace();
				System.exit(1);
			}
			logger.debug(String.format("DATA %d received", blockNum));
			logger.logPacketInfo(initPacket, false);
			
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
			} catch (IOException e) {
				e.printStackTrace();
				System.exit(1);
			}
		}
		logger.debug("*** finished transfer ***");
    }
}