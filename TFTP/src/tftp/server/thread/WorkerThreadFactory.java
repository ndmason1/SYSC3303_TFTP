package tftp.server.thread;

import java.net.DatagramPacket;

import tftp.exception.TFTPPacketException;
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
	public WorkerThread createWorkerThread(DatagramPacket reqPacket) throws TFTPPacketException {		
		
		byte[] data = reqPacket.getData();
		
		if (data[1] == PacketUtil.READ_FLAG) {
			return new ReadHandlerThread(reqPacket);
		} else if (data[1] == PacketUtil.WRITE_FLAG) {
			return new WriteHandlerThread(reqPacket);
		} else {
			throw new TFTPPacketException("expected a request packet");
		}			
	}

}
