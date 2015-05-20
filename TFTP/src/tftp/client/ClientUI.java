package tftp.client;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.AccessDeniedException;
import java.nio.file.FileAlreadyExistsException;
import java.util.Scanner;

import tftp.Logger;

public class ClientUI {

	private Scanner keyboard;
	private Client client;
	
	public ClientUI() {
		keyboard = new Scanner(System.in);
		client = new Client();
	}
	
	public void showUI() {		
		System.out.println("\nWelcome to the TFTP client. [v1.0 - LOCALHOST ONLY]");
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
		System.out.println("  <filename> <absolute path of file location> <request type> <transfer mode>\n");
		System.out.println("Acceptable request types: 'r' (read) or 'w' (write)");
		System.out.println("Acceptable transfer modes: 'n' (netascii - text files) or 'o' (octet - binary files)");
		System.out.println("Example request (both forward slash or backslash path separator accepted):\n");
		System.out.println("  file.txt C:/Users/User/ r o\n");
		System.out.println("Press Q at any time to quit.\n");
	}
	
	private void parseInput(String input) {
		String[] args = input.split("\\s");
		if (args.length < 4) {
			System.out.println("Not enough arguments.");
			printHelp();
			return;
		}
		
		String path = args[0];
		String filename = args[1];
		String type = args[2].toLowerCase();
		String mode = args[3].toLowerCase();
		
				
		if (mode.equals("n")) {
			mode = "netascii";
		} else if (mode.equals("o")) {
			mode = "octet";
		} else {
			System.out.println("Invalid transfer mode.");
			printHelp();
			return;
		}
		
		String fullpath = path;
		// make sure the last character in the path is a separator
		if (path.charAt(path.length()-1) != File.separator.toCharArray()[0]) {			
			fullpath += File.separator;
		}
		fullpath += filename;
		
//		//----Client UI Updated by Syed----//
//		if (!new File(filename).exists()) {
//			System.out.println("File does not exist.");
//			printHelp();
//			return;
//		}
//		//Checking if user can read the file
//		if (!new File(filename).canRead()){System.out.println("Cannot Read file");
//		printHelp();
//		return;}
//		
//		//Checking if the file already exists?
//		if (new File(filename).exists()){
//			System.out.println("File Already Exist");
//			
//			String userinput;
//
//			//Allowing a user to input so that a user can Replace the file if the file already exists
//			//If can't replace the file then a transfer will not be completed, 
//			//And/or otherwise it will prompt an invalid request
//			while (true){
//				System.out.println("Do you want replace it: Y or N");
//				userinput = keyboard.nextLine();
//				if(userinput == "y"|| userinput=="Y"){
//					new File(filename).delete();
//					System.out.println("File has been deleted");
//					break;
//				}
//				if (userinput == "n" || userinput =="N"){
//					System.out.println("Choose not replace a file, transfer can not complete");
//					break;
//				}
//				System.out.println("invalide request, try again");
//			}
//
//			
//
//		} //ClientUI Updated by Syed upto here!

		if (type.equals("r")) {
			// read request
			// check that the local (destination) file can be written to
			try {
				client.checkValidReadOperation(fullpath);
			} catch (AccessDeniedException e) {				
				System.out.println("Error: Access denied to destination file");				
			} catch (FileAlreadyExistsException e) {
				// file exists on the local filesystem, so make sure the user really wants to overwrite
				while (true){					
					System.out.println("Destination file already exists. Do you want to replace it? (y/n)");
					String userinput = keyboard.nextLine().toLowerCase();
					if(userinput.equals("y")) {
						new File(filename).delete();
						break;
					}
					else if (userinput.equals("n")) {
						System.out.println("Operation terminated.");
						return;
					}
				}
			}
			try {
				client.sendReadRequest(fullpath, mode);
			} catch (IOException e) {
				System.out.println("Error: IOException caught, couldn't complete request");
				e.printStackTrace();
			}
			System.out.printf("Transfer of file \"%s\" finished.\n\n", filename);
			
		} else if (type.equals("w")) {
			// write request
			// check that local (source) file exists and can be read from
			try {
				client.checkValidWriteOperation(fullpath);
			} catch (AccessDeniedException e) {				
				System.out.println("Error: Access denied to source file");
				return;
			} catch (FileNotFoundException e) {
				System.out.println("Error: Source file not found");
				return;
			}
			
			client.sendWriteRequest(fullpath, mode);
			System.out.printf("Transfer of file \"%s\" finished.\n\n", filename);
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
		Logger.getInstance().setLabel("client");
		ClientUI ui = new ClientUI(); 
		try {
			ui.showUI();
		} finally {
			ui.cleanup();
			Logger.getInstance().flushMessages();
		}
	}

	
	

}
