package tftp.sim.thread;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.util.concurrent.LinkedBlockingQueue;

import tftp.sim.ErrorSimUtil;
import tftp.sim.PacketType;
import tftp.sim.ProcessType;

public class PacketReceiver implements Runnable {
	
	private DatagramPacket packet;				// packet object to receive with
	private DatagramSocket socket;				// socket object to receive with
	private LinkedBlockingQueue<DatagramPacket> packetQueue;	// queue to put packets into
	private String expectedPacketStr;			// string of expected packet type
	private boolean running;					// running state
	private ProcessType senderProcess;

	public PacketReceiver(DatagramSocket recvSocket, LinkedBlockingQueue<DatagramPacket> queue, PacketType initPacketType,
			ProcessType sendProcess) {
		// TODO Auto-generated constructor stub
		socket = recvSocket;
		packetQueue = queue;
		running = false;
		expectedPacketStr = initPacketType.name();
		senderProcess = sendProcess;
	}

	@Override
	public void run() {
		
		running = true;
		
		// while there are packets to receive from the client, add them to the queue
		// assuming here that the first packet expected is a request, 
		// but if this doesn't hold, only the messages are affected
		
		while (running) {
			packet = ErrorSimUtil.receivePacketFromProcess(socket, senderProcess, expectedPacketStr);
			packetQueue.add(packet);
			if (expectedPacketStr.equals("RRQ"))
				expectedPacketStr = "ACK";
			else
				expectedPacketStr = "DATA";
		}

	}
	
	public void requestStop() {
		running = false;	
	}

}
