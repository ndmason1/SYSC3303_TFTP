/*
 * ClientUI.java
 * 
 * Authors: TEAM 1
 * 
 * This file was created specifically for the course SYSC 3303.
 */

package tftp.client;

import java.io.IOException;
import java.util.Scanner;

import tftp.exception.ErrorReceivedException;
import tftp.exception.TFTPException;
import tftp.net.PacketUtil;
import tftp.server.Server;
import tftp.sim.ErrorSimulator;

public class ClientUI {

	private Scanner keyboard;
	private Client client;
	
	public ClientUI() {
		keyboard = new Scanner(System.in);
		client = new Client();
	}
	
	public void showUI() {		
		System.out.println("\nWelcome to the TFTP client. [v1.1 - LOCALHOST ONLY]");
		
		boolean check = true;
		
		while (check){
			System.out.println("Error Simulator on? y/n?");
			String errorSimulator = keyboard.nextLine();
		
			if (errorSimulator.toLowerCase().equals("yes") || errorSimulator.toLowerCase().equals("y"))
			{
				client.setPortNum(ErrorSimulator.LISTEN_PORT);
				check = false;
			}
			if (errorSimulator.toLowerCase().equals("no") || errorSimulator.toLowerCase().equals("n"))
			{
				client.setPortNum(Server.SERVER_PORT);
				check = false;
			}
		}
		
		check = true; 
		
		while (check){
			System.out.println("Do you wish to use the default client directory path? y/n?");
			String diskFullPath = keyboard.nextLine();
		
			if (diskFullPath.toLowerCase().equals("yes") || diskFullPath.toLowerCase().equals("y"))
			{
				try {
					client.setDirectory(new java.io.File(".").getCanonicalPath().concat(new String("\\src\\tftp\\client\\ClientFiles")));
				} catch (IOException e) {			
					System.out.println("Couldn't set up directory for client files! terminating");
					e.printStackTrace();
					cleanup();
					System.exit(1);
				}
				check = false;
			}
			if (diskFullPath.toLowerCase().equals("no") || diskFullPath.toLowerCase().equals("n"))
			{
				System.out.println("Please enter in a valid target directory path: ");
				diskFullPath = keyboard.nextLine();
				//TODO check valid directory path
				client.setDirectory(diskFullPath);
				check = false;
			}
		}
		
		printHelp();
				
		while (true) {
			System.out.print("> ");
			String input = keyboard.nextLine();			
			if (!input.isEmpty()) {				
				if (input.length() == 1 && (input.charAt(0) == 'Q' || input.charAt(0) == 'q') )
				{
					System.out.println("\nTerminating.");
					System.exit(0);
				}
				else parseInput(input);				
			}
		}
		
	}
	
	private void printHelp() {
		System.out.println("Please enter your file transfer request in the following format:\n");
		System.out.println("<filename> <request type> \n");
		System.out.println("Acceptable request types: 'r' (read) or 'w' (write)");
		System.out.println("Example request :\n");
		System.out.println("file.txt r \n");
		System.out.println("Press Q at any time to quit.\n");
	}
	
	//Three different parameters expected
	private void parseInput(String input) {
		String[] args = input.split("\\s");
		if (args.length < 2) {
			System.out.println("Not enough arguments.");
			printHelp();
			return;
		}
		
		String filename = args[0];
		String type = args[1].toLowerCase();
		
		client.setFilename(filename);
		client.setMode("octet");
		
		client.retreiveFile();

		
		if (type.equals("r")) {
			// read request
			// check that the local (destination) file can be written to
			try {
				client.checkValidReadOperation();
			} catch (TFTPException e) {
				System.out.println("ERROR: (" + e.getErrorCode() + ")" + " " + e.getMessage());
				System.out.println("Could not complete request, please try again.");
				return;
			}
			
			// send the request
			try {
				client.sendReadRequest();	
			} catch (ErrorReceivedException e) {
				System.out.println("Error packet received from server!");
				System.out.println("ERROR: (" + e.getErrorCode() + ")" + " " + e.getMessage());
				System.out.println("Could not complete request, please try again.");
				return;		
			} catch (TFTPException e) {				
				System.out.println("ERROR: (" + e.getErrorCode() + ")" + " " + e.getMessage());				
				System.out.println("Could not complete request, please try again.");
				return;
			}
			System.out.println("Read of file " + client.getFilename() + " into directory " + client.getDirectory() + " finished.\n");
			
		} else if (type.equals("w")) {
			// write request
			// check that local (source) file exists and can be read from
			try {
				client.checkValidWriteOperation();			
			} catch (ErrorReceivedException e) {
				System.out.println("Error packet received from server!");
				System.out.println("ERROR: (" + e.getErrorCode() + ")" + " " + e.getMessage());
				System.out.println("Could not complete request, please try again.");
				return;			
			} catch (TFTPException e) {				
				System.out.println("ERROR: (" + e.getErrorCode() + ")" + " " + e.getMessage());
				System.out.println("Could not complete request, please try again.");
				return;
			}
			
			try{
			    client.sendWriteRequest();
			}catch (TFTPException e){
				if (e.getErrorCode() != PacketUtil.ERR_UNKNOWN_TID) {
					System.out.println("ERROR: (" + e.getErrorCode() + ")" + " " + e.getMessage());
					System.out.println("Could not complete request, please try again.");
					return;
				}
			}
			
			System.out.println("Write of file " + client.getFilename() + " from directory " + client.getDirectory() + " finished.\n");
		} else {
			System.out.println("Invalid request type.");
			printHelp();
		}
	}
	
	private void cleanup() {
		keyboard.close();
		client.cleanup();
		
	}
	
	public static void main(String args[]) {

		ClientUI ui = new ClientUI(); 
		try {
			ui.showUI();
		} finally {
			ui.cleanup();
		}
	}

	
	

}
