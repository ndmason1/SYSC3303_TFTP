package tftp.sim;

import java.net.DatagramPacket;

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
			int index = PacketUtil.getFilenameLength(newData) + 3; // get the index of the first mode character
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

}
