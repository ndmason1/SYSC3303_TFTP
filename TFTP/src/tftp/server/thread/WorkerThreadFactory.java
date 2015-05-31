/*
 * WorkerThreadFactory.java
 * 
 * Authors: TEAM 1
 * 
 * This file was created specifically for the course SYSC 3303.
 */


package tftp.server.thread;

import java.net.DatagramPacket;

import tftp.exception.TFTPException;
import tftp.net.PacketUtil;

/**
 * A factory class for creating WorkerThread instances.
 */
public class WorkerThreadFactory {	

	/**
	 * Creates and returns a WorkerThread. The type of WorkerThread returned
	 * is determined by the request type.
	 * 
	 * @param  reqPacket  the packet containing the client's request
	 * @return            a new WorkerThread
	 */
	public WorkerThread createWorkerThread(DatagramPacket reqPacket) throws TFTPException {		
		
		byte[] data = reqPacket.getData();
		
		if (data[1] == PacketUtil.READ_FLAG) {
			return new ReadHandlerThread(reqPacket);
		} else if (data[1] == PacketUtil.WRITE_FLAG) {
			return new WriteHandlerThread(reqPacket);
		} else {
			throw new TFTPException("expected a request packet", PacketUtil.ERR_ILLEGAL_OP);
		}			
	}
}
