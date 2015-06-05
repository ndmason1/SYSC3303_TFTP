/*
 * ServerUI.java
 * 
 * Authors: TEAM 1
 * 
 * This file was created specifically for the course SYSC 3303.
 */


package tftp.server;

import java.io.File;
import java.io.IOException;
import java.util.Scanner;

public class ServerUI {

	private Scanner keyboard;
	private ServerThread st;
	
	public ServerUI() {
		keyboard = new Scanner(System.in);		
	}
	
	public void showUI() {
		System.out.println("TFTP server running [v1.0 - LOCALHOST ONLY] (press Q to terminate) ");		
		
		st = new ServerThread();
		
		promptDirectory();
		
		st.start();		
		System.out.println("Waiting for requests...");
		
		String input = keyboard.nextLine();
		input = input.replaceAll(" ", "");
		if (!input.isEmpty())
			if (input.charAt(0) == 'Q' || input.charAt(0) == 'q')
			{
				System.out.println("\nFinishing remaining transfers and terminating...");
				st.shutdown();
			}
		
	}
	
	private void promptDirectory() {
		boolean check = true; 
		Server server = st.getServer();
		
		while (check){
			System.out.println("Do you wish to use the default server directory path? y/n?");
			String diskFullPath = keyboard.nextLine();
		
			if (diskFullPath.toLowerCase().equals("yes") || diskFullPath.toLowerCase().equals("y"))
			{
				try {
					server.setDirectory(new java.io.File(".").getCanonicalPath().concat(new String("\\src\\tftp\\server\\ServerFiles")));
					System.out.println("Using default server directory!");
				} catch (IOException e) {			
					System.out.println("Couldn't set up directory for client files! terminating");
					e.printStackTrace();
					server.cleanup();
					System.exit(1);
				}
				check = false;
			}
			if (diskFullPath.toLowerCase().equals("no") || diskFullPath.toLowerCase().equals("n"))
			{
				while(true){
					System.out.println("Please enter in a valid target directory path: ");
					diskFullPath = keyboard.nextLine();
					//TODO check valid directory path
					File file = new File(diskFullPath);
					if (file.isDirectory()){
						server.setDirectory(diskFullPath);
						check = false;
						System.out.println("Successfully changed server directory!");
						break;
					}
					System.out.println("Invalide directory path, please enter again!");
				}
				
			}
		}
	}
	
	public static void main(String args[]) {
		
		new ServerUI().showUI();
	}
	
	class ServerThread extends Thread {
		private Server server;
		
		public ServerThread() {
			super("TFTPServer");
			server = new Server();
		}
		
		@Override
		public void run() {
			try {
				server.serveRequests();
			} finally {				
				server.cleanup();
			}
		}
		
		public void shutdown() {
			server.finishProcessing();
		}
		
		public Server getServer() { return server; }
	}

}
