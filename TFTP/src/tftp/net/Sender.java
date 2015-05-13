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





import tftp.Util;

public class Sender {

	private int segmentID;
	private int reSendCount;
	private byte[] msg, buffer;
	private FileInputStream fileReader;
	private DatagramSocket socket;
	private int fileLength, currentPos, bytesRead;    
	private final int packetOverhead = 106; // packet overhead

	private InetAddress receiverIP;
	private int receiverPort;
	
	private PacketUtil packetUtil;


	private final static byte READ_FLAG = 0x01;
	private final static byte WRITE_FLAG = 0x02;
	private final static byte ACK_FLAG = 0x04;
	private final static byte DATA_FLAG = 0x03;

	public Sender(DatagramSocket socket, int receiverTID){
		

		try {
			receiverIP = InetAddress.getLocalHost();
		} catch (UnknownHostException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		receiverPort = receiverTID;
		this.socket = socket;		
		packetUtil = new PacketUtil(receiverIP, receiverTID);
	}

	public Sender(DatagramSocket socket, DatagramPacket initPacket, int recvTID) throws IOException
	{
		msg = new byte[512];
		buffer = new byte[512];
		socket = socket;

		//setup DatagramSocket with correct Inetaddress and port of receiver
		socket.connect(initPacket.getAddress(), initPacket.getPort());
		segmentID = 0;

		receiverIP = InetAddress.getLocalHost();
		receiverPort = recvTID;
	}

	public void sendFile(File theFile) throws IOException {
		fileReader = new FileInputStream(theFile);
		fileLength = fileReader.available();
		int blockNum = 1;
		int currentPos = 0;

		System.out.println("*** Filename: " + theFile.getName() + " ***");
		System.out.println("*** Bytes to send: " + fileLength + " ***");
		
		byte[] sendBuf = new byte[512];
		byte[] recvBuf = new byte[512];

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
			
			
			System.out.println("Sending segment " + blockNum + " with " + bytesRead + " byte payload.");
			
			socket.send(sendPacket);
			Util.printPacketInfo(sendPacket, true);
			
			DatagramPacket reply = new DatagramPacket(recvBuf, recvBuf.length);
	        
            try
            {
            	System.out.println("waiting for ACK...");
                socket.receive(reply);                
                Util.printPacketInfo(reply, false);
            } catch (SocketTimeoutException e) {
            	// we are assuming no errors for now, so ignore this case
            	return;
            }
            
            // verify ack
            int ackNum = packetUtil.parseAckPacket(reply);
            if (ackNum != blockNum) {
            	// TODO: handle out of sequence block
            }
            
			blockNum++;
			currentPos += bytesRead;
			
		} while (!done);
		
		System.out.println("*** finished transfer ***");
		System.out.println("========================================\n");
	}

	public static void main(String args[]) {
	}




}
