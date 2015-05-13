package tftp.net;

import java.io.*;
import java.net.*;
import java.util.*;

import tftp.Util;

public class Receiver
{
    private byte[] buffer;
    private DatagramSocket socket;
    private String filename, initString;
    private FileOutputStream fileWriter;
    private DatagramPacket initPacket, receivedPacket;
    private int bytesReceived, bytesToReceive, simulateBadConnection, expectedSegmentID;
    private final boolean simulateMessageFail = false;//true if you want to simulate a bad connection
    
    private InetAddress senderIP;
    private int senderPort;
    private PacketUtil packetUtil;

//    public Receiver(DatagramSocket socket) throws IOException
//    {
//    }
    
    public Receiver(DatagramSocket socket, int senderTID){		

		try {
			senderIP = InetAddress.getLocalHost();
		} catch (UnknownHostException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		senderPort = senderTID;
		
		this.socket = socket;
		
		packetUtil = new PacketUtil(senderIP, senderTID);
	}

    public void receiveFile(DatagramPacket initPacket) {
    	
    	System.out.printf("DATA received\n");
		Util.printPacketInfo(initPacket, false);
		
    	// create recv packet
		byte data[] = new byte[516];
		DatagramPacket receivePacket = new DatagramPacket(data, data.length);
		
		int blockNum = packetUtil.parseDataPacket(initPacket);
		
		// send ACK for initial data packet
		DatagramPacket sendPacket = packetUtil.formAckPacket(blockNum);
		System.out.printf("sending ACK to %s:%d...\n", initPacket.getAddress().toString(), initPacket.getPort());			
		Util.printPacketInfo(sendPacket, true);
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
			System.out.println("waiting for next DATA segment...");
			try {			  
				socket.receive(receivePacket);
			} catch(IOException e) {
				e.printStackTrace();
				System.exit(1);
			}
			System.out.printf("DATA received\n");
			Util.printPacketInfo(receivePacket, false);
			
			if (receivePacket.getLength() < 516) {
				done = true;
			}
			
			// send ACK
			blockNum = packetUtil.parseDataPacket(receivePacket);
			// TODO verify block num
			sendPacket = packetUtil.formAckPacket(blockNum);
			System.out.printf("sending ACK to %s:%d...\n", receivePacket.getAddress().toString(), receivePacket.getPort());			
			Util.printPacketInfo(sendPacket, true);
			try {
				socket.send(sendPacket);
			} catch (IOException e) {
				e.printStackTrace();
				System.exit(1);
			}
		}
		System.out.println("*** finished transfer ***");
		System.out.println("========================================\n");
    }
}