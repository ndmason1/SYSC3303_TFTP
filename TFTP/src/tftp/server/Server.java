/*
 * Server.java
 * 
 * Authors: TEAM 1
 * 
 * This file was created specifically for the course SYSC 3303.
 */


package tftp.server;


import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.SocketException;
import java.util.HashSet;

import tftp.exception.TFTPException;
import tftp.net.PacketUtil;
import tftp.server.thread.WorkerThread;
import tftp.server.thread.WorkerThreadFactory;

/**
 * 
 * This class implements a TFTP server.
 *
 */
public class Server {
	
	public static final int SERVER_PORT = 69; 
	
	//private variables
	private DatagramPacket receivePacket;
	private DatagramSocket receiveSocket;
	
	private WorkerThreadFactory threadFactory;
	private HashSet<WorkerThread> activatedThreads;
	private boolean acceptNewConnections;
	private int threadCount;
	private String directory;
	
	public Server()
	{
		try {
			receiveSocket = new DatagramSocket(SERVER_PORT);
		} catch (SocketException se) {
			se.printStackTrace();
			System.exit(1);
		}
		
		threadFactory = new WorkerThreadFactory();	
		threadFactory = new WorkerThreadFactory();
		
		activatedThreads = new HashSet<WorkerThread>();
		acceptNewConnections = true;
		threadCount = 1;
	}

	public void cleanup() {
		// close the socket
		receiveSocket.close();
	}

	public void serveRequests()
	{
		while(acceptNewConnections) {
			byte data[] = new byte[PacketUtil.BUF_SIZE];
			receivePacket = new DatagramPacket(data, data.length);			
			receivePacket.getLength();

			// wait for request to come in
			try {
				receiveSocket.receive(receivePacket);				
			} catch (SocketException e) { 
				// likely the socket was closed because the server is shutting down
				System.out.printf("Stopped listening on port %d.\n", SERVER_PORT);
				
			} catch (IOException e) {
				System.out.println("IOexception listening for packets in main server thread");
				e.printStackTrace();
				System.exit(1);
			}
			
			System.out.println( (String.format("Request received. Creating handler thread %d", threadCount++)) );
			// spawn a thread to process request
			WorkerThread worker = null;
			try {
				worker = threadFactory.createWorkerThread(receivePacket);				
				worker.start();
			} catch (TFTPException e) {
				System.out.println("ERROR: (" + e.getErrorCode() + ")" + " " + e.getMessage());				
			}
			activatedThreads.add(worker);
		}
	}

	
	public void finishProcessing() {
		acceptNewConnections = false;
		for (Thread t : activatedThreads) {			
			try {
				// wait for threads to finish - those blocked while waiting to receive packet should time out
				t.join();
			} catch (InterruptedException e) {
				System.out.printf("Thread %s was interrupted and did not finish processing\n", t.getName());				
			}
		}
		cleanup();
	}
	
	//Server get functions
	public String getDirectory(){return directory;}
	
	//Server set functions
	public void setDirectory(String aDirectory){directory = aDirectory;}
	
}
