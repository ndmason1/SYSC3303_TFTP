/*
 * PacketUtil.java
 * 
 * Authors: TEAM 1
 * 
 * This file was created specifically for the course SYSC 3303.
 */

package tftp.net;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.SocketTimeoutException;

public class PacketUtil {
	
	private InetAddress receiverIP;
	private int receiverPort;
	
	public final static byte READ_FLAG = 0x01;
	public final static byte WRITE_FLAG = 0x02;
	public final static byte ACK_FLAG = 0x04;
	public final static byte DATA_FLAG = 0x03;
	public final static byte ERROR_FLAG = 0x05;
	
	public final static byte ERR_UNDEFINED = 0x00;
	public final static byte ERR_FILE_NOT_FOUND = 0x01;
	public final static byte ERR_ACCESS_VIOLATION = 0x02;
	public final static byte ERR_DISK_FULL = 0x03;
	public final static byte ERR_ILLEGAL_OP = 0x04;
	public final static byte ERR_UNKNOWN_TID = 0x05;
	public final static byte ERR_FILE_EXISTS = 0x06;
	public final static byte ERR_USER_NOT_FOUND = 0x07;
	
	public final static int BUF_SIZE = 1024;
	
	public PacketUtil(InetAddress receiverIP, int receiverPort) {
		this.receiverIP = receiverIP;
		this.receiverPort = receiverPort;
	}

	public DatagramPacket formRrqPacket(String filename, String mode) {
		return formReqPacket(filename, mode, READ_FLAG);
	}

	public DatagramPacket formWrqPacket(String filename, String mode) {
		return formReqPacket(filename, mode, WRITE_FLAG);
	}
	
	public DatagramPacket formReqPacket(String filename, String mode, byte flag) {

		int msgLength = filename.length() + mode.length() + 4; 
		byte msg[] = new byte[msgLength];

		// opcode
		msg[0] = 0x00;
		msg[1] = flag;

		// filename
		byte[] fbytes = filename.getBytes(); 
		System.arraycopy(fbytes, 0, msg, 2, fbytes.length);
		msg[fbytes.length + 2] = 0x00;

		// mode
		byte[] mbytes = mode.getBytes(); 
		System.arraycopy(mbytes, 0, msg, 3+fbytes.length, mbytes.length);
		msg[fbytes.length + mbytes.length + 3] = 0x00;

		DatagramPacket packet = new DatagramPacket(msg, msgLength, receiverIP, receiverPort);
		return packet;
	}

	public DatagramPacket formDataPacket(byte[] data, int dataLength, int blockNum) {

		int msgLength = dataLength + 4; 
		byte msg[] = new byte[msgLength];

		// opcode
		msg[0] = 0;
		msg[1] = DATA_FLAG;

		// block number
		byte[] blockBytes = getBlockNumberBytes(blockNum);
		System.arraycopy(blockBytes, 0, msg, 2, 2);

		// data
		if (dataLength > 0)
			System.arraycopy(data, 0, msg, 4, dataLength);		

		DatagramPacket packet = new DatagramPacket(msg, msgLength, receiverIP, receiverPort);
		return packet;
	}

	public DatagramPacket formAckPacket(int blockNum) {

		int msgLength = 4; 
		byte msg[] = new byte[msgLength];

		// opcode
		msg[0] = 0;
		msg[1] = ACK_FLAG;

		// block number
		byte[] blockBytes = getBlockNumberBytes(blockNum);
		System.arraycopy(blockBytes, 0, msg, 2, 2);

		DatagramPacket packet = new DatagramPacket(msg, msgLength, receiverIP, receiverPort);
		return packet;
	}
	
	public DatagramPacket formErrorPacket(int errCode, String errMsg) {
		// TODO: make consistent with the rest of this class
		DatagramPacket errorPacket = OPcodeError.OPerror(errMsg, (byte)errCode);
		errorPacket.setAddress(receiverIP);
		errorPacket.setPort(receiverPort);
		return errorPacket;
	}
	
	public DatagramPacket formErrorPacket(int errCode, String errMsg, InetAddress recvIP, 
			int recvPort) {
		// TODO: make consistent with the rest of this class
		DatagramPacket errorPacket = OPcodeError.OPerror(errMsg, (byte)errCode);
		errorPacket.setAddress(recvIP);
		errorPacket.setPort(recvPort);
		return errorPacket;
	}
	
	/**
	 * Sends a packet to the given process and displays information. 
	 * 
	 * 	@param senderLabel		a string describing the sender (useful for identifying server threads) 
	 *  @param sendSocket		the DatagramSocket to use for sending
	 *  @param sendPacket		the DatagramPacket to send
	 *  @param recvProcess		the process (client or server) who should be listening for the packet
	 *  @param sendPacketStr	a string describing the packet being sent, which is displayed
	 */
	public static void sendPacketToProcess(String senderLabel, DatagramSocket sendSocket, DatagramPacket sendPacket, 
			ProcessType recvProcess, String sendPacketStr) {		
				
		System.out.printf("%ssending %s packet to %s (IP: %s, port %d) ... ", 
				senderLabel, sendPacketStr, recvProcess, sendPacket.getAddress(), sendPacket.getPort());		
		
		try {
			sendSocket.send(sendPacket);
		} catch (IOException e) {
			System.out.printf("IOException caught sending %s packet: %s", recvProcess, e.getMessage());
			System.out.println("cannot proceed, terminating simulation");
			return;
		}	
		
		PacketType sendType = getPacketType(sendPacket);		
		String packetLabel = sendType.name();
		// if DATA or ACK packet, display block number
		// if ERROR, display error code
		if (sendType == PacketType.DATA || sendType == PacketType.ACK)
			packetLabel += " " + getBlockNumber(sendPacket);
		else if (sendType == PacketType.ERROR)
			packetLabel += " " + getErrorCode(sendPacket);
		
		System.out.printf("\n%s  sent %s packet ", senderLabel, packetLabel);
		printOpcodeAndLength(sendPacket);
	}
	
	/**
	 * Listens for a packet from the given process and displays information.
	 * The socket may timeout, in which case a SocketTimeoutException is thrown.
	 *
	 *  @param receiverLabel		a string describing the receiver (useful for identifying server threads) 
	 *  @param recvSocket			the DatagramSocket to listen on
	 *  @param sendProcess			the process (client or server) expected to send a packet
	 *  @param expectedPacketStr	a string describing the expected type of packet to receive, which is displayed
	 *  @throws SocketTimeoutException 	if a timeout was set and has expired
	 */
	public static DatagramPacket receivePacketOrTimeout(String receiverLabel, DatagramSocket recvSocket, 
			ProcessType sendProcess, String expectedPacketStr) throws SocketTimeoutException {

		byte data[] = new byte[PacketUtil.BUF_SIZE];		
		DatagramPacket receivePacket = new DatagramPacket(data, data.length);
		
		// listen for a packet from given source process
		System.out.printf("%slistening on port %s for %s packet from %s ... ", receiverLabel, recvSocket.getLocalPort(), 
				expectedPacketStr, sendProcess);
		try {
			recvSocket.receive(receivePacket);
		} catch (SocketTimeoutException e) {
			// throw so the caller can detect timeout
			throw e; 
		} catch (IOException e) {
			System.out.printf("IOException caught receiving %s packet: %s", sendProcess, e.getMessage());			
			return null;
		}
		
		PacketType receivedPacketType = getPacketType(receivePacket);		
		String packetLabel = receivedPacketType.name();
		
		// if DATA or ACK packet, display block number
		// if ERROR, display error code
		if (receivedPacketType == PacketType.DATA || receivedPacketType == PacketType.ACK)
			packetLabel += " " + getBlockNumber(receivePacket);
		else if (receivedPacketType == PacketType.ERROR)
			packetLabel += " " + getErrorCode(receivePacket);
		
		System.out.printf("\n%s  received %s packet ", receiverLabel, packetLabel);
		printOpcodeAndLength(receivePacket);
		
		return receivePacket;
	}
	
	
	/**
	 * Listens for a packet from the given process and displays information.
	 * 
	 *  @param receiverLabel		a string describing the receiver (useful for identifying server threads)
	 *  @param recvSocket			the DatagramSocket to listen on
	 *  @param sendProcess			the process (client or server) expected to send a packet
	 *  @param expectedPacketStr	a string describing the expected type of packet to receive, which is displayed
	 *  @return 					the packet that was received
	 */
	public static DatagramPacket receivePacketFromProcess(String receiverLabel, DatagramSocket recvSocket, 
			ProcessType sendProcess, String expectedPacketStr) {
		
		byte data[] = new byte[PacketUtil.BUF_SIZE];		
		DatagramPacket receivePacket = new DatagramPacket(data, data.length);
		
		// listen for a packet from given source process
		System.out.printf("%slistening on port %s for %s packet from %s ... ", receiverLabel, recvSocket.getLocalPort(), 
				expectedPacketStr, sendProcess);
		try {
			recvSocket.receive(receivePacket);
		} catch (IOException e) {
			System.out.printf("IOException caught receiving %s packet: %s", sendProcess, e.getMessage());
			System.out.println("cannot proceed, terminating simulation");
			System.exit(1);
		}	
		
		PacketType receivedPacketType = getPacketType(receivePacket);		
		String packetLabel = receivedPacketType.name();
		
		// if DATA or ACK packet, display block number
		// if ERROR, display error code
		if (receivedPacketType == PacketType.DATA || receivedPacketType == PacketType.ACK)
			packetLabel += " " + getBlockNumber(receivePacket);
		else if (receivedPacketType == PacketType.ERROR)
			packetLabel += " " + getErrorCode(receivePacket);

		System.out.printf("\n%s  received %s packet ", receiverLabel, packetLabel);
		printOpcodeAndLength(receivePacket);
		
		return receivePacket;
	}
	


	/**
	 * Returns the block number of an ACK or DATA packet. 
	 * 
	 *  @param packet	the packet to inspect
	 *  @return 		the block number
	 */
	public static int getBlockNumber(DatagramPacket packet) {
		PacketType type = getPacketType(packet);
		if (type != PacketType.DATA && type != PacketType.ACK)
			throw new IllegalArgumentException();
		
		return PacketUtil.getBlockNumberInt(packet.getData()[2], packet.getData()[3]);
	}
	
	/**
	 * Returns the error code from an ERROR packet. 
	 * 
	 *  @param packet	the packet to inspect
	 *  @return 		the error code
	 */
	public static int getErrorCode(DatagramPacket packet) {
		PacketType type = getPacketType(packet);
		if (type != PacketType.ERROR)
			throw new IllegalArgumentException();
		
		return packet.getData()[3];
	}
	
	/**
	 * Utility function to display the opcode of a TFTP packet.
	 * 
	 *  @param packet	the packet
	 */
	public static void printOpcodeAndLength(DatagramPacket packet) {
		byte[] data = packet.getData();		
		System.out.printf("[opcode: %02x, length: %db]\n", data[1], packet.getLength());
	}	
	
	/**
	 * Utility function to check if a packet is an ERROR packet.
	 * 
	 *  @param packet	the packet
	 *  @return			true if ERROR packet
	 */
	public static boolean isErrorPacket(DatagramPacket packet) {
		return packet.getData()[1] == PacketUtil.ERROR_FLAG;
	}
	
	/**
	 * Get the type of a TFTP packet.   
	 * 
	 *  @param packet	the packet to inspect
	 *  @return 		the type of the packet
	 */
	public static PacketType getPacketType(DatagramPacket packet) {
		
		switch (packet.getData()[1]) {
		case PacketUtil.READ_FLAG:
			return PacketType.RRQ;
			
		case PacketUtil.WRITE_FLAG:
			return PacketType.WRQ;
			
		case PacketUtil.DATA_FLAG:
			return PacketType.DATA;
			
		case PacketUtil.ACK_FLAG:
			return PacketType.ACK;
			
		case PacketUtil.ERROR_FLAG:
			return PacketType.ERROR;
			
		default:
			// we should only be parsing server or client packets so 
			// only genuine network errors will cause this
			return null; 
		}
	}
	
	/**
	 * Utility function to return the length of a filename contained in a RRQ or WRQ packet.
	 * 
	 *  @param data		the contents of a request packet
	 *  @return 		the length of the string
	 */
	public static int getFilenameLength (byte[] data) {

		int length = 0;
		for (int i = 2; i < data.length; i++) {
			if (data[i] == 0) {
				length = i - 2;
				break;
			}
		}

		return length;
	}
	
	/**
	 * Utility function to return the error message contained in an ERROR packet.
	 * 
	 *  @param data		the contents of an ERROR packet
	 *  @return 		the error message
	 */
	public static String getErrMessage(byte[] data) {

		StringBuilder sb = new StringBuilder();
		
		for (int i = 4; i < data.length; i++) {
			sb.append((char)data[i]);
			if (data[i] == 0)				
				break;
		}

		return sb.toString();
	}
	
	public static void setSocketTimeout(DatagramSocket socket, int timeoutMs) {
		try {
			socket.setSoTimeout(timeoutMs);
		} catch (SocketException e) {
			e.printStackTrace();
		}
	}

	public static byte[] getBlockNumberBytes(int blockNum) {
		byte[] bytes = new byte[2];
		bytes[1] = (byte) (blockNum & 0xFF);
		bytes[0] = (byte) ((blockNum >> 8) & 0xFF);
		return bytes;		
	}
	
	public static int getBlockNumberInt(byte high, byte low) {
		int val = ((high & 0xff) << 8) | (low & 0xff);
		return val;		
	}
		
	public static void main(String args[]) {
		int blockNum = 0;
		byte[] bytes = PacketUtil.getBlockNumberBytes(blockNum);
		System.out.println(Integer.toHexString(bytes[0] & 0xFF) + " " + Integer.toHexString(bytes[1] & 0xFF));
		int newNum = PacketUtil.getBlockNumberInt(bytes[0], bytes[1]);
		System.out.println(newNum);
	}

}
