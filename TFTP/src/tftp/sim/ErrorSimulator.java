/*
 * Intermediary.java
 * TEAM 1
 * 
 * Last updated: 07/05/2015
 * 
 * This file was created specifically for the course SYSC 3303.
 * 
 */

package tftp.sim;


import java.io.*;
import java.net.*;
import java.util.*;

public class ErrorSimulator {
	
	public static final int ErrorSimPort = 68;
	
	
	//OPcode errors
	public static final int NETWORK = 0;
	public static final int PACKET = 1;
	public static final int TID = 2;

	
	public static final int DATA = 1;
	public static final int ACK = 2;
	public static final int REQ = 3;

	private byte packetType;
	private byte blockNumber;
	private int errorDetail;
	protected Error error;
	
	private DatagramSocket sendReceieveSocket;
	
	
	public ErrorSimulator()
	{
		try {sendReceieveSocket = new DatagramSocket(ErrorSimPort);} catch (SocketException se) {se.printStackTrace();System.exit(1); }
	}
	
	

	public DatagramPacket FormPacket() throws UnknownHostException
	{
		byte data[] = new byte[516];
		DatagramPacket packet = new DatagramPacket(data, data.length);
		try {
			System.out.println("Waiting for packet from client on port "+ ErrorSimPort);
			sendReceieveSocket.receive(packet);
		} catch (IOException e) {
			e.printStackTrace();
			System.exit(1);
		}
		System.out.println("Recieved Packet");
		return packet;
	
	}
	
	

	public void setupErrorMode(Scanner scanner) {
		
		int input;
		System.out.println("Which type of error do you wish to generate? (select by number):");
		System.out.println("0) No Error");
		System.out.println("4) Packet Error");
		System.out.println("5) TID Error");
		System.out.println("8) Delayed Packet");
		System.out.println("9) Deleted Packet");
		System.out.println("10) Duplicated Packet");
		System.out.println("Choose: ");
		
		for(;;) {
				input = scanner.nextInt();
			if(input==0) {
				this.error = new Error();
				return;
			}
			if(input==4) {
				System.out.println("Packet Error!");
				packetError(scanner);
				break;
			} else if(input==5) {
				System.out.println("TIP Error!");
				TIDError(scanner);
				break;
			} else if(input>=8 && input<=10) {
				errorDetail = input;
				networkError(scanner);
				break;
			}
			System.out.println("Invalid option.  Please try again:");
			
		}
	}
	
	private void TIDError(Scanner scanner) {
		System.out.println("TIP Error:");
		System.out.println();
		System.out.println("Packet type to initiate error:");
		System.out.println("1) DATA");
		System.out.println("2) ACK");
		for(;;) {
			this.packetType = scanner.nextByte();
			if (this.packetType!=DATA || this.packetType!=ACK) break;
			System.out.println("Invalid block selection.  Please try again:");
		} 
		
		System.out.println();
		System.out.println();
		
		System.out.println("Please select a block number to trigger the error (Must be under 127): ");
		for(;;) {
			this.blockNumber = scanner.nextByte();
			if (this.blockNumber>0 || (this.blockNumber==0 && this.packetType == (byte) 1)) break;
			System.out.println("Invalid block number selection.  Please try again:");
		} 
		error = new Error(TID, this.packetType, new Message(this.blockNumber));
	}
	
	private void networkError(Scanner scanner) {

				System.out.println("Network Error:");
				System.out.println();
				System.out.println("Select packet type:");
				System.out.println("1) DATA");
				System.out.println("2) ACK");
				System.out.println("3) REQ");
				for(;;) {
					this.packetType = scanner.nextByte();
					if (this.packetType!=DATA || this.packetType!=ACK || this.packetType!=REQ) break;
					System.out.println("Invalid block selection.  Please try again:");
				} 
				
				System.out.println();
				System.out.println();
				
				if (this.packetType == REQ) {
					error = new Error(0, this.packetType, new Message(this.blockNumber),this.errorDetail);
					return;
				}
				
				//Select Block Number
				System.out.println("Please select a block number to trigger the error (Must be under 127): ");
				for(;;) {
					this.blockNumber = scanner.nextByte();
					if (this.blockNumber>0) break;
					System.out.println("Invalid block number selection.  Please try again:");
				}
				error = new Error(0, this.packetType, new Message(this.blockNumber),this.errorDetail);
	}
	
	

	private void packetError(Scanner scanner) {
		//Select Packet Type
		System.out.println("Packet Error:");
		System.out.println();
		System.out.println("Packet type to corrupt:");
		System.out.println("1) DATA");
		System.out.println("2) ACK");
		System.out.println("3) REQ");
		for(;;) {
			this.packetType = scanner.nextByte();
			if (this.packetType!=DATA || this.packetType!=ACK || this.packetType!=REQ) break;
			System.out.println("Invalid block selection.  Please try again:");
		} 
		
		System.out.println();
		System.out.println();
		
		if (this.packetType == REQ) {
			System.out.println();
			System.out.println("Select type of error you wish to generate in the request packet:");
			System.out.println("1) No Starting Zero");
			System.out.println("2) Invalid Op Code");
			System.out.println("3) No File Name");
			System.out.println("4) No Zero After Filename");
			System.out.println("5) No Mode");
			System.out.println("6) Invalid Mode");
			System.out.println("7) No Zero After Mode");
			System.out.println("8) Data After Zero");
			
			for(;;) {
				this.errorDetail = scanner.nextInt();
				if (this.errorDetail>0 || this.errorDetail<=7) break;
				System.out.println("Invalid option.  Please try again:");
			}
			error = new Error(PACKET, this.packetType, new Message(this.blockNumber),this.errorDetail);
			
			return;
		}
		
		System.out.println("Please select a block number to trigger the error (Must be under 127): ");
		for(;;) {
			this.blockNumber = scanner.nextByte();
			if (this.blockNumber>0) break;
			System.out.println("Invalid block number selection.  Please try again:");
		}
		
		System.out.println();
		System.out.println("Select type of error you wish to generate in the data packet:");
		System.out.println("1) No Starting Zero");
		System.out.println("2) Invalid Op Code");
		System.out.println("3) Block Number Too High");
		System.out.println("4) Block Number Too Low");
		
		for(;;) {
			this.errorDetail = scanner.nextInt();
			if (this.errorDetail>0 || this.errorDetail<=4) break;
			System.out.println("Invalid option.  Please try again:");
		}
		error = new Error(PACKET, this.packetType, new Message(this.blockNumber),this.errorDetail);
	}
	

	
	
	
	
	public static void main( String args[] ) throws UnknownHostException
	{
		ErrorSimulator s = new ErrorSimulator();
		Scanner scanner = new Scanner (System.in);
		
		for(;;){
			System.out.println("Error mode setup:");
			s.setupErrorMode(scanner);
			ErrorSimCreator thread = null;
			try {
				thread = new ErrorSimCreator(s.FormPacket(),s.error);
			} catch (java.rmi.UnknownHostException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			Thread connect = new Thread (thread);
	        connect.start();
		}
	}
}