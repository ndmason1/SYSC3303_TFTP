package tftp.net;

import java.net.DatagramPacket;
import java.net.InetAddress;

public class PacketUtil {
	
	private InetAddress receiverIP;
	private int receiverPort;
	
	private final static byte READ_FLAG = 0x01;
	private final static byte WRITE_FLAG = 0x02;
	private final static byte ACK_FLAG = 0x04;
	private final static byte DATA_FLAG = 0x03;	
	
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
		msg[1] = READ_FLAG;

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
	
	/* return block number or -1 if bad packet */
	public int parseAckPacket(DatagramPacket packet) {
		byte[] data = packet.getData();
		if (data[0] != 0) return -1;
		if (data[1] != 4) return -1;
		return getBlockNumberInt(data[2], data[3]);
	}
	
	/* return block number or -1 if bad packet */
	public int parseDataPacket(DatagramPacket packet) {
		byte[] data = packet.getData();
		if (data[0] != 0) return -1;
		if (data[1] != 3) return -1;
		return getBlockNumberInt(data[2], data[3]);
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
		System.out.printf("%02x %02x\n", bytes[0], bytes[1]);
		int newNum = PacketUtil.getBlockNumberInt(bytes[0], bytes[1]);
		System.out.printf("%d\n", newNum);
	}

}
