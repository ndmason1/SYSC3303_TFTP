/*
 * Server.java
 * 
 * Authors: TEAM 1
 * 
 * This file was created specifically for the course SYSC 3303.
 */


package tftp.server;

import java.io.File;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.util.HashSet;
import java.util.Set;

import tftp.exception.InvalidRequestException;
import tftp.exception.TFTPPacketException;
import tftp.Config;
import tftp.Logger;
import tftp.Util;
import tftp.net.PacketUtil;
import tftp.net.Receiver;
import tftp.net.Sender;
import tftp.server.thread.WorkerThread;
import tftp.server.thread.WorkerThreadFactory;

/**
 * 
 * This class implements a TFTP server.
 *
 */
public class Server {
	private DatagramPacket receivePacket;
	private DatagramSocket receiveSocket;
	
	private WorkerThreadFactory threadFactory;
	private HashSet<WorkerThread> activatedThreads;
	private Logger logger;
	private boolean acceptNewConnections;
	private int threadCount;
	private String mainDirectory;	
	
	public Server()
	{
		try {
			receiveSocket = new DatagramSocket(69);
		} catch (SocketException se) {
			se.printStackTrace();
			System.exit(1);
		}
		
		threadFactory = new WorkerThreadFactory();		
		logger = Logger.getInstance();
		
		activatedThreads = new HashSet<WorkerThread>();
		acceptNewConnections = true;
		threadCount = 0;
		mainDirectory = Config.getServerDirectory();
	}

	public void cleanup() {
		logger.flushMessages();
		receiveSocket.close();
	}

	public void serveRequests()
	{
		while(acceptNewConnections) {
			byte data[] = new byte[PacketUtil.BUF_SIZE];
			receivePacket = new DatagramPacket(data, data.length);			
			receivePacket.getLength();

			// wait for request to come  in
			try {
				receiveSocket.receive(receivePacket);
				
			} catch (IOException e) {
				logger.error("IO Exception: likely:");
				logger.error("Receive Socket Timed Out.\n" + e);
				e.printStackTrace();
				System.exit(1);
			}			
			System.out.println("received packet on port 69");
			logger.logPacketInfo(receivePacket, false);
			logger.info(String.format("Request received. Creating handler thread %d", threadCount));
			// spawn a thread to process request
			WorkerThread worker = null;
			try {
				worker = threadFactory.createWorkerThread(receivePacket);				
				worker.start();
			} catch (TFTPPacketException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			activatedThreads.add(worker);
		}
	}

	public void finishProcessing() {
		acceptNewConnections = false;
		for (Thread t : activatedThreads) {
			try {
				t.join();
			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
	}
	
}
