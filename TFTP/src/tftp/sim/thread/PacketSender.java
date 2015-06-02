
package tftp.sim.thread;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.concurrent.LinkedBlockingQueue;

import tftp.sim.ErrorSimUtil;
import tftp.sim.ProcessType;

public class PacketSender implements Runnable {
	
	private DatagramPacket packet;								// packet object to receive with
	private DatagramSocket socket;								// socket object to receive with
	private LinkedBlockingQueue<DatagramPacket> packetQueue;	// queue to take packets from
	private boolean running;									// running state
	private ProcessType receiverProcess;
	private InetAddress receiverIP;
	private int receiverPort;

	public PacketSender(DatagramSocket sendSocket, LinkedBlockingQueue<DatagramPacket> queue, 
			ProcessType recvProcess, InetAddress recvIP, int recvPort) {
		
		socket = sendSocket;
		packetQueue = queue;
		running = false;
		receiverProcess = recvProcess;
		receiverIP = recvIP;
		receiverPort = recvPort;
	}

	@Override
	public void run() {
		running = true;
		
		while (running) {
			try {
				packet = packetQueue.take();
			} catch (InterruptedException e) {
				System.out.println("PacketSender interrupted");
				return;
			}
			
			packet.setAddress(receiverIP);
			packet.setPort(receiverPort);
			String sendPacketStr = ErrorSimUtil.getPacketType(packet).name();
			ErrorSimUtil.sendPacketToProcess(socket, packet, receiverProcess, sendPacketStr);
		}

	}
	
	public void requestStop() {
		running = false;	
	}

}

