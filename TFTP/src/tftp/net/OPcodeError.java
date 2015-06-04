/*
 * OPcodeError.java
 * 
 * Authors: TEAM 1
 * 
 * This file was created specifically for the course SYSC 3303.
 */

package tftp.net;

import java.net.DatagramPacket;

public class OPcodeError {
	private static byte[] OpcodeErr(byte[] data, byte[] msg, byte errorCode){
		// opcode
		data[0] = 0;
		data[1] = 5;
		// error code 
		data[2] = 0;
		data[3] = errorCode;            
		for(int i = 0; i < msg.length; i++) {
			data[i+4] = msg[i];
		}

		data[data.length-1] = 0;
		return data;
	}

	public static DatagramPacket OPerror(String errorMsg, byte num){
		byte[] msg = errorMsg.getBytes();
		byte[] data = new byte[msg.length + 5];            
		data = OpcodeErr(data, msg, num);
		DatagramPacket packet = new DatagramPacket(data, data.length);		
		return packet;
	}

	public static byte getError(DatagramPacket packet){
		return packet.getData()[3];
	}
}
