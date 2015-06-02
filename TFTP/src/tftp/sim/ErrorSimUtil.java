package tftp.sim;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.SocketException;
import java.net.SocketTimeoutException;

import tftp.net.PacketUtil;

public class ErrorSimUtil {

	/**
	 * Utility function to return a packet that has been modified to trigger an
	 * illegal operation error.
	 * 
	 *  @param originalPacket	the packet to be modified
	 *  @param illegalOpType	the method of modification
	 *  @return 				a modified DatagramPacket
	 */
	public static DatagramPacket getCorruptedPacket(DatagramPacket originalPacket, IllegalOperationType illegalOpType) {

		byte[] newData = originalPacket.getData();
		int newLength = originalPacket.getLength();

		switch (illegalOpType) {
		case OPCODE: // invalidate the opcode field
			newData[0] = 0xf; // set the very first byte to non-zero
			break;

		case FILENAME: // invalidate the filename field (only for RRQ/WRQ)
			// TODO: find a better way to invalidate text fields?
			newData[2] = 0x1; // set the first filename byte to non-printable character
			break;

		case MODE: // invalidate the mode field (only for RRQ/WRQ)
			// TODO: find a better way to invalidate text fields?
			int index = getFilenameLength(newData) + 3; // get the index of the first mode character
			newData[index] = 0x1; // set the first mode byte to non-printable character
			break;

		case BLOCKNUM: // invalidate the block number field (only for DATA/ACK)
			// "invalid" block number depends on what the current block number is
			// for now, just set it to zero - this is valid only for the first ACK in a WRQ
			newData[2] = 0x0;
			newData[3] = 0x0;
			break;

		case ERRCODE: // invalidate the error code field (only for ERROR)
			newData[2] = 0xf; // set the first error code byte to non-zero 
			break;

		case ERRMSG: // invalidate the error message field (only for ERROR)
			// TODO: find a better way to invalidate text fields?
			newData[4] = 0x1; // set the first message byte to non-printable character
			break;
		
		case LENGTH_TOO_SHORT:
			newLength -= 2; // shorten the length by 2 bytes
			break;
		default:
			break;			
		}

		DatagramPacket newPacket = new DatagramPacket(newData, newLength);
		return newPacket;

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

	/**
	 * Utility function to display the opcode of a TFTP packet.
	 * 
	 *  @param packet	the packet
	 */
	public static void printOpcode(DatagramPacket packet) {
		byte[] data = packet.getData();
		System.out.printf("[opcode: %02x]\n", data[1]);
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
	 * Sends a packet to the given process and displays information.
	 * A similar version of this method exists in ErrorSimulator, but this one has more parameters and
	 * can be used by other classes.
	 * 	 
	 *  @param sendSocket		the DatagramSocket to use for sending
	 *  @param sendPAcket		the DatagramPacket to send
	 *  @param recvProcess		the process (client or server) who should be listening for the packet
	 *  @param sendPacketStr	a string describing the packet being sent, which is displayed
	 */
	public static void sendPacketToProcess(DatagramSocket sendSocket, DatagramPacket sendPacket, 
			ProcessType recvProcess, String sendPacketStr) {		
				
		System.out.printf("sending %s packet to %s (IP: %s, port %d) ... ", 
				sendPacketStr, recvProcess, sendPacket.getAddress(), sendPacket.getPort());		
		
		try {
			sendSocket.send(sendPacket);
		} catch (IOException e) {
			System.out.printf("IOException caught sending %s packet: %s", recvProcess, e.getMessage());
			System.out.println("cannot proceed, terminating simulation");
			return;
		}	
		
		PacketType sendType = getPacketType(sendPacket);		
		String label = sendType.name();
		// if DATA or ACK packet, display block number		
		if (sendType == PacketType.DATA || sendType == PacketType.ACK)
			label += " " + getBlockNumber(sendPacket);
		
		System.out.printf("sent %s packet ", label);
		ErrorSimUtil.printOpcode(sendPacket);
	}
	
	/**
	 * Listens for a packet from the given process and displays information.
	 * Sets receivedPacket and receivedPacketType to the packet that was received and its type, respectively.
	 * A similar version of this method exists in ErrorSimulator, but this one returns a DatagramPacket and
	 * can be used by other classes.
	 * 
	 *  @param recvSocket			the DatagramSocket to listen on
	 *  @param sendProcess			the process (client or server) expected to send a packet
	 *  @param expectedPacketStr	a string describing the expected type of packet to receive, which is displayed
	 *  @return 					the packet that was received
	 */
	public static DatagramPacket receivePacketFromProcess(DatagramSocket recvSocket, ProcessType sendProcess, 
			String expectedPacketStr) {
		
		byte data[] = new byte[PacketUtil.BUF_SIZE];		
		DatagramPacket receivePacket = new DatagramPacket(data, data.length);
		
		// listen for a packet from given source process
		// note this function doesn't actually enforce the packet type received, since it might not always matter
		System.out.printf("listening on port %s for %s packet from %s ... ", recvSocket.getLocalPort(), 
				expectedPacketStr, sendProcess);
		try {
			recvSocket.receive(receivePacket);
		} catch (IOException e) {
			System.out.printf("IOException caught receiving %s packet: %s", sendProcess, e.getMessage());
			System.out.println("cannot proceed, terminating simulation");
			System.exit(1);
		}	
		
		PacketType receivedPacketType = ErrorSimUtil.getPacketType(receivePacket);		
		String label = receivedPacketType.name();
		
		// if DATA or ACK packet, display block number		
		if (receivedPacketType == PacketType.DATA || receivedPacketType == PacketType.ACK)
			label += " " + ErrorSimUtil.getBlockNumber(receivePacket);
		
		System.out.printf("received %s packet ", label);
		ErrorSimUtil.printOpcode(receivePacket);
		
		return receivePacket;
	}
	
	public static void setSocketTimeout(DatagramSocket socket, int timeoutMs) {
		try {
			socket.setSoTimeout(timeoutMs);
		} catch (SocketException e) {
			e.printStackTrace();
		}
	}

}
