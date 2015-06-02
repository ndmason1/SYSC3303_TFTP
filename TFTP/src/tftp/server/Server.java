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
			receiveSocket = new DatagramSocket(69);
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

	// TODO: better way to stop threads, likely using interrupt()
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
	
	//Server get functions
	public String getDirectory(){return directory;}
	
	//Server set functions
	public void setDirectory(String aDirectory){directory = aDirectory;}
	
}
