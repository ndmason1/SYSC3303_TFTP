package tftp.server.thread;

import java.net.DatagramPacket;

import tftp.exception.TFTPPacketException;
import tftp.net.PacketUtil;

public class WorkerThreadFactory {

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
