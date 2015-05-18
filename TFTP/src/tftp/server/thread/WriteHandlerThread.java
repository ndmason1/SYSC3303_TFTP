package tftp.server.thread;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.SocketException;

import tftp.exception.InvalidRequestException;
import tftp.net.PacketUtil;
import tftp.net.Receiver;

/**
 * A specialized thread that processes TFTP write requests received by a TFTP server.
 */
public class WriteHandlerThread extends WorkerThread {
	
	/**
	 * Constructs a WriteHandlerThread. Passes the DatagramPacket argument to 
	 * the WorkerThread constructor. 
	 *
	 * @param  reqPacket  the packet containing the client's request
	 */
	public WriteHandlerThread(DatagramPacket reqPacket) {
		super(reqPacket);
	}
	
	/**
	 * Runs this thread, which processes a TFTP write request and starts a 
	 * file transfer.
	 */
	@Override
	public void run() {
		byte[] data = reqPacket.getData();
		
		// validate file name		
		int i = 2;
		StringBuilder sb = new StringBuilder();
		while (data[i] != 0x00) {
			sb.append((char)data[i]);
			// reject non-printable values
			if (data[i] < 0x20 || data[i] > 0x7F)
				throw new InvalidRequestException(
						String.format("non-printable data inside file name: byte %d",i));			
			i++;
		}
		String filename = sb.toString();

		// move index to start of mode string
		i++;		

		// validate mode string
		sb = new StringBuilder();
		while (data[i] != 0x00) {
			sb.append((char)data[i]);			
			i++;
		}

		String mode = sb.toString();
		if (! (mode.toLowerCase().equals("netascii") || mode.toLowerCase().equals("octet")) )
			throw new InvalidRequestException("invalid mode");		

		// should be at end of packet
		if (i+1 != reqPacket.getLength())
			throw new InvalidRequestException("incorrect packet length");

		// request is good if we made it here
		// write request, so send an ACK 0
		DatagramSocket sendRecvSocket = null;
		try {
			sendRecvSocket = new DatagramSocket();
		} catch (SocketException se) {
			se.printStackTrace();
			System.exit(1);
		}
		
		PacketUtil packetUtil = new PacketUtil(reqPacket.getAddress(), reqPacket.getPort());
		DatagramPacket initAck = packetUtil.formAckPacket(0);
		
		logger.logPacketInfo(initAck, true);
		
		try {
			sendRecvSocket.send(initAck);
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		// get the first data packet so we can set up receiver
		data = new byte[PacketUtil.BUF_SIZE];
		receivePacket = new DatagramPacket(data, data.length);
		try {
			sendRecvSocket.receive(receivePacket);
		} catch (IOException e) {
			e.printStackTrace();
		}
		
		logger.logPacketInfo(receivePacket, false);
		
		Receiver r = new Receiver(sendRecvSocket, receivePacket.getPort());
		r.receiveFile(receivePacket);
		
		cleanup();
	}

}
