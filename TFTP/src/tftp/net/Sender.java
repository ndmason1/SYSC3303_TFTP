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






import tftp.Logger;
import tftp.Util;

public class Sender {

	private FileInputStream fileReader;
	private DatagramSocket socket;
	private int fileLength, bytesRead;
	private InetAddress receiverIP;	
	private PacketUtil packetUtil;	
	private Logger logger;

	public Sender(DatagramSocket socket, int receiverTID){
		
		try {
			receiverIP = InetAddress.getLocalHost();
		} catch (UnknownHostException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		this.socket = socket;		
		packetUtil = new PacketUtil(receiverIP, receiverTID);
		logger = Logger.getInstance();
	}

	public void sendFile(File theFile) throws IOException {
		fileReader = new FileInputStream(theFile);
		fileLength = fileReader.available();
		int blockNum = 1;

		logger.log("*** Filename: " + theFile.getName() + " ***");
		logger.log("*** Bytes to send: " + fileLength + " ***");
		
		byte[] sendBuf = new byte[PacketUtil.BUF_SIZE];
		byte[] recvBuf = new byte[PacketUtil.BUF_SIZE];

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
			
			
			logger.log(String.format("Sending segment %d with %d byte payload.", blockNum, bytesRead));
			
			socket.send(sendPacket);
			logger.printPacketInfo(sendPacket, true);
			
			DatagramPacket reply = new DatagramPacket(recvBuf, recvBuf.length);
	        
            try
            {
            	logger.log(String.format("waiting for ACK %d", blockNum));
                socket.receive(reply);                
                logger.printPacketInfo(reply, false);
            } catch (SocketTimeoutException e) {
            	// we are assuming no network errors for now, so ignore this case
            	return;
            }
            
            // verify ack
            int ackNum = packetUtil.parseAckPacket(reply);
            if (ackNum != blockNum) {
            	// TODO: handle out of sequence block
            }
            
			blockNum++;
			
		} while (!done);
		
		logger.log("*** finished transfer ***");
		logger.log("========================================\n");
	}

}
