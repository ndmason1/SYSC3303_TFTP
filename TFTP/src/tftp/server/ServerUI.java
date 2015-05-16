package tftp.server;

import java.util.Scanner;

public class ServerUI {

	private Scanner keyboard;	
	
	public ServerUI() {
		keyboard = new Scanner(System.in);		
	}
	
	public void showUI() {
		System.out.println("TFTP server running [v1.0 - LOCALHOST ONLY] (press Q to terminate) ");
		System.out.println("Waiting for requests...");
		
		ServerThread st = new ServerThread();
		st.start();
		
		String input = keyboard.nextLine();
		input = input.replaceAll(" ", "");
		if (!input.isEmpty())
			if (input.charAt(0) == 'Q' || input.charAt(0) == 'q')
			{
				System.out.println("\nFinishing remaining transfers and terminating...");
				st.shutdown();
				//System.exit(0);
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
	}

}
