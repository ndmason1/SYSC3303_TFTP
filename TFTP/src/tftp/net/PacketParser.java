/*
 * PacketParser.java
 * 
 * Authors: TEAM 1
 * 
 * This file was created specifically for the course SYSC 3303.
 */


package tftp.net;

import java.net.DatagramPacket;
import java.net.InetAddress;

import tftp.exception.ErrorReceivedException;
import tftp.exception.TFTPException;
import tftp.exception.TFTPFileIOException;
import tftp.exception.TFTPPacketException;

/**
 * PacketParser objects can be used to check that packet fields are correct 
 * so that further processing can continue.
 */
public class PacketParser {

	private InetAddress expectedIP; // IP portion of TID - if this is null, accept any TID
	private int expectedPort;		// port portion of TID - if this is 0, accept any TID
	
	/**
	 * Constructs a PacketParser with default values.
	 * These values ensure that packets that are parsed may contain any TID.
	 * (this useful for parsing request packets) 
	 *  
	 * @return a new PacketParser object 
	 */
	public PacketParser() {
		this.expectedIP = null;
		this.expectedPort = 0;
	}
	
	/**
	 * Constructs a PacketParser.
	 *
	 * @param  expectedIP	the INetAddress that the new PacketParser expects from packets that it parses 
	 * @param  expectedPort	the port number that the new PacketParser expects from packets that it parses
	 * @return a new PacketParser object 
	 */
	public PacketParser(InetAddress expectedIP, int expectedPort) {
		this.expectedIP = expectedIP;
		this.expectedPort = expectedPort;
	}

	/**
	 * Parses a RRQ packet.
	 *
	 * @param  packet	the packet containing the client's request
	 * @return 			the filename of the file requested for reading
	 * @throws 			TFTPPacketException if the packet is badly formatted/corrupted 
	 */
	public String parseRRQPacket(DatagramPacket packet) throws TFTPException {
		byte[] data = packet.getData();
		
		// the packet could be an error packet, so check this first
		if (data[1] != PacketUtil.ERROR_FLAG)
			parseErrorPacket(packet);

		// check opcode
		if (data[0] != 0 || data[1] != PacketUtil.READ_FLAG)
			throw new TFTPPacketException("bad op code, expected RRQ", PacketUtil.ERR_ILLEGAL_OP);

		// parse the rest of the request
		return parseRequestPacket(packet);
	}

	/**
	 * Parses a WRQ packet.
	 *
	 * @param  packet	the packet containing the client's request
	 * @return 			the filename of the file requested for writing
	 * @throws 			TFTPPacketException if the packet is badly formatted/corrupted 
	 */
	public String parseWRQPacket(DatagramPacket packet) throws TFTPException {
		byte[] data = packet.getData();
		
		// the packet could be an error packet, so check this first
		if (data[1] != PacketUtil.ERROR_FLAG)
			parseErrorPacket(packet);

		// check opcode
		if (data[0] != 0 || data[1] != PacketUtil.WRITE_FLAG)
			throw new TFTPPacketException("bad op code, expected WRQ", PacketUtil.ERR_ILLEGAL_OP);

		// parse the rest of the request
		return parseRequestPacket(packet);
	}
	
	/**
	 * Parses a DATA packet.
	 *
	 * @param  packet	the packet containing a data block
	 * @throws 			TFTPPacketException if the packet is badly formatted/corrupted 
	 */
	public void parseDataPacket(DatagramPacket packet, int expectedBlockNum) throws TFTPException {
		byte[] data = packet.getData();
		
		// the packet could be an error packet, so check this first
		if (data[1] != PacketUtil.ERROR_FLAG)
			parseErrorPacket(packet);
		
		// check TID
		checkTID(packet);

		// check opcode
		if (data[0] != 0 || data[1] != PacketUtil.DATA_FLAG)
			throw new TFTPPacketException("bad op code, expected DATA", PacketUtil.ERR_ILLEGAL_OP);
		
		// check that the block number is what we expect
		int blockNum = PacketUtil.getBlockNumberInt(data[2], data[3]);
		if (blockNum != expectedBlockNum) {
			// TODO: handle this error - not sure which type of error this qualifies as 
		}
		
		// check that there is no "extra" data beyond this packet's set length		
		for (int i = packet.getLength(); i < data.length; i++) {
			if (data[i] != 0) 
				throw new TFTPPacketException("data found beyond packet length", PacketUtil.ERR_ILLEGAL_OP);
		}			
	}
	
	/**
	 * Parses an ACK packet.
	 *
	 * @param  packet	the packet containing an ACK in response to a data block
	 * @throws 			TFTPPacketException if the packet is badly formatted/corrupted 
	 */
	public void parseAckPacket(DatagramPacket packet, int expectedBlockNum) throws TFTPException {
		byte[] data = packet.getData();
		
		// the packet could be an error packet, so check this first
		if (data[1] != PacketUtil.ERROR_FLAG)
			parseErrorPacket(packet);		
		
		// check TID
		checkTID(packet);

		// check opcode
		if (data[0] != 0 || data[1] != PacketUtil.ACK_FLAG)
			throw new TFTPPacketException("bad op code, expected ACK", PacketUtil.ERR_ILLEGAL_OP);
		
		// check that the block number is what we expect
		int blockNum = PacketUtil.getBlockNumberInt(data[2], data[3]);
		if (blockNum != expectedBlockNum) {
			// TODO: handle this error - not sure which type of error this qualifies as 
		}
		
		if (packet.getLength() > 4)
			throw new TFTPPacketException("incorrect packet length", PacketUtil.ERR_ILLEGAL_OP);
	}
	
	/**
	 * Parses an ERROR packet.
	 *
	 * @param  packet	the packet containing an error code and message
	 * @throws 			TFTPPacketException if the packet is badly formatted/corrupted
	 * @throws 			TFTPPacketException if the packet has an unknown TID
	 * @throws 			TFTPFileIOException if the packet contains a file I/O error
	 */
	public void parseErrorPacket(DatagramPacket packet) throws TFTPException {
		byte[] data = packet.getData();

		// check TID
		checkTID(packet);
		
		// check opcode
		if (data[0] != 0 || data[1] != PacketUtil.ERROR_FLAG)
			throw new TFTPPacketException("bad op code, expected ERROR", PacketUtil.ERR_ILLEGAL_OP);
		
		// check error code
		// expect first byte to be 0 since error codes only go up to 7		
		if (data[2] != 0 || data[3] < 0 || data[3] > 7)
			throw new TFTPPacketException("unknown error code", PacketUtil.ERR_ILLEGAL_OP);
		
		byte errCode = data[3];
		
		// check message		
		int i = 4;
		StringBuilder sb = new StringBuilder();
		while (data[i] != 0x00) {
			sb.append((char)data[i]);
			// reject non-printable values
			if (data[i] < 0x20 || data[i] > 0x7F)
				throw new TFTPPacketException("non-character byte inside error message", PacketUtil.ERR_ILLEGAL_OP);			
			i++;
		}
		String errMsg = sb.toString();
		
		// check length
		i++;
		if (i != packet.getLength()) {
			throw new TFTPPacketException("packet length mismatch", PacketUtil.ERR_ILLEGAL_OP);
		}
		
		// the error packet itself is fine, so throw ErrorReceived to let other objects know
		// that an error occurred on the other end
		throw new ErrorReceivedException(errMsg, errCode);
	}

	/**
	 * Parses a request packet.
	 *
	 * @param  packet	the packet containing the client's request
	 * @return 			the filename of the file requested
	 * @throws 			TFTPPacketException if the packet is badly formatted/corrupted 
	 */
	private String parseRequestPacket(DatagramPacket packet) throws TFTPPacketException {
		byte[] data = packet.getData();
		
		// validate file name		
		int i = 2;
		StringBuilder sb = new StringBuilder();
		while (data[i] != 0x00) {
			sb.append((char)data[i]);
			// reject non-printable values
			if (data[i] < 0x20 || data[i] > 0x7F)
				throw new TFTPPacketException("non-printable data inside file name", PacketUtil.ERR_ILLEGAL_OP);			
			i++;
		}
		String filename = sb.toString();
		// TODO : check for character not typically allowed in file names

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
			throw new TFTPPacketException("invalid mode", PacketUtil.ERR_ILLEGAL_OP);

		// should be at end of packet
		if (i+1 != packet.getLength())			
			throw new TFTPPacketException("incorrect packet length", PacketUtil.ERR_ILLEGAL_OP);

		return filename;
	}
	
	/**
	 * Checks a packet's transfer ID (IP address + port) to confirm it is expected
	 *
	 * @param  packet	the packet to check
	 * @throws 			TFTPPacketException if the packet's TID is not known 
	 */
	private void checkTID(DatagramPacket packet) throws TFTPPacketException {
		if (expectedIP != null && !packet.getAddress().equals(expectedIP)) 
			throw new TFTPPacketException("unrecognized source IP address", PacketUtil.ERR_UNKNOWN_TID);
		if (expectedPort != 0 && packet.getPort() != expectedPort) 
			throw new TFTPPacketException("unrecognized source port", PacketUtil.ERR_UNKNOWN_TID);
	}

}
