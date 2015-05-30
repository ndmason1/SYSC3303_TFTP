
package tftp.sim;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.LinkedList;
import java.util.Scanner;

import tftp.net.PacketUtil;

public class ErrorSimulator {
	
	private static final int LISTEN_PORT = 78; // not using 68 due to its usage by windows DHCP service
	private static final int SERVER_PORT = 69;

	// sockets used for communicating with the client and server, respectively	
	private DatagramSocket clientRecvSocket, serverSendRecvSocket;

	// packet objects to use for sending and receiving messages
	private DatagramPacket sendPacket, receivePacket;

	// keep track of network address
	private InetAddress serverIP, clientIP;
	private int clientPort;	

	// for input
	private Scanner keyboard;
	
	// enum values which keep track of user selections
	private ProcessType receiverProcessSelection;
	private ErrorType errorTypeSelection;
	private IllegalOperationType illegalOpTypeSelection;
	private PacketType packetTypeSelection;	
	
	// keep track of type of last packet received
	private PacketType receivedPacketType;

	public ErrorSimulator() {
		try {
			clientRecvSocket = new DatagramSocket(LISTEN_PORT);
			serverSendRecvSocket = new DatagramSocket();
		} catch (SocketException e) {
			e.printStackTrace();
			System.exit(1);
		}

		try {
			serverIP = InetAddress.getLocalHost(); // THIS WILL CHANGE IN ITERATION 5
		} catch (UnknownHostException e) {
			e.printStackTrace();
			System.exit(1);
		}

		keyboard = new Scanner(System.in);

		System.out.println("==== TFTP ERROR SIMULATOR STARTED ====\n");

		// print IP (useful for iteration 5)
		try {
			System.out.println("Hostname / Host IP: " + InetAddress.getLocalHost());
		} catch (UnknownHostException e) {
			// ignore 
		}
	}

	public void showMainMenu() {
		System.out.println("Please select the type of error to simulate: \n");		
		System.out.println("(1) Illegal operation (TFTP error code 4)");
		System.out.println("(2) Unknown TID (TFTP error code 5)");
		// add others here, probably
		System.out.println("(9) No error (packets will be relayed unmodified)");
		System.out.println("[ PRESS Q TO QUIT ]\n");

		switch (getInput()) {
		case 1: 
			errorTypeSelection = ErrorType.ILLEGAL_OP;
			showProcessMenu();
			break;

		case 2:
			errorTypeSelection = ErrorType.UNKNOWN_TID;
			showProcessMenu();
			break;

		case 9:
			errorTypeSelection = ErrorType.NONE;
			relayRequestWithoutErrors();
			break;
		}
	}

	private void showProcessMenu() {

		// build message based on previous selection
		String message = errorTypeSelection == ErrorType.ILLEGAL_OP ?
				"Illegal operation" :
					"Uknown TID"; 
		message += " error selected.";

		System.out.println(message);
		System.out.println("Which process should receive the invalid packet? \n");		
		System.out.println("(1) Client");
		System.out.println("(2) Server");
		System.out.println("[ PRESS Q TO QUIT ]\n");

		switch (getInput()) {
		case 1: 
			receiverProcessSelection = ProcessType.CLIENT;
			break;
		case 2:
			receiverProcessSelection = ProcessType.SERVER;
		}

		switch (errorTypeSelection) {
		case ILLEGAL_OP:
			showPacketTypeMenu();
			break;

		case UNKNOWN_TID:			
			simulateUnknownTID();
			break;

		case NONE:
			// shouldn't get here
			break;
		}
	}

	private void showPacketTypeMenu() {

		// build message based on previous selection
		String message = receiverProcessSelection == ProcessType.CLIENT ?
				"Client" :
				"Server"; 
		message += " selected.";

		System.out.println(message);
		System.out.println("Please choose which packet type should be modified:\n");
		if (receiverProcessSelection == ProcessType.SERVER) {
			System.out.println("(1) RRQ");
			System.out.println("(2) WRQ");
			System.out.println("(3) DATA");
			System.out.println("(4) ACK");
			System.out.println("(5) ERROR");
		} else {
			System.out.println("(1) DATA");
			System.out.println("(2) ACK");
			System.out.println("(3) ERROR");
		}
			
		System.out.println("[ PRESS Q TO QUIT ]\n");

		switch (getInput()) {
		case 1:
			packetTypeSelection = receiverProcessSelection == ProcessType.SERVER ? PacketType.RRQ : PacketType.DATA;
			break;

		case 2:			
			packetTypeSelection = receiverProcessSelection == ProcessType.SERVER ? PacketType.WRQ : PacketType.ACK;
			break;

		case 3:
			packetTypeSelection = receiverProcessSelection == ProcessType.SERVER ? PacketType.DATA : PacketType.ERROR;
			break;

		case 4:
			if (receiverProcessSelection == ProcessType.CLIENT) {
				System.out.println("Not a valid option! Returning to main menu");
				return;
			}				
			packetTypeSelection = PacketType.ACK;
			break;

		case 5:
			if (receiverProcessSelection == ProcessType.CLIENT) {
				System.out.println("Not a valid option! Returning to main menu");
				return;
			}
			packetTypeSelection = PacketType.ERROR;
			break;
			
		default:			
			System.out.println("Not a valid option! Returning to main menu");
			return;
		}

		showIllegalOpTypeMenu();
	}

	private void showIllegalOpTypeMenu() {

		// build message based on previous selection
		String message = packetTypeSelection.name() + " packet selected";
		System.out.println(message);

		LinkedList<String> options = new LinkedList<String>();
		options.add("(1) Invalid op-code");
		options.add("(2) Invalid length (contents of packet not modified)");

		// generate more options based on packet selection
		// TODO: enable more specific options e.g. exact byte?		
		switch (packetTypeSelection) {
		case RRQ:
		case WRQ:			
			options.add("(3) Filename");
			options.add("(4) Transfer mode");
			break;

		case DATA:
		case ACK:
			options.add("(3) Block number");
			break;

		case ERROR:
			options.add("(3) Error code");
			options.add("(4) Error message");
			break;
		}

		System.out.println("Please choose what field should be modified to generate the error:\n");
		for (String option : options) {
			System.out.println(option);
		}
		System.out.println("[ PRESS Q TO QUIT ]\n");

		switch (getInput()) {
		case 1:
			illegalOpTypeSelection = IllegalOperationType.OPCODE;
			break;

		case 2:
			illegalOpTypeSelection = IllegalOperationType.LENGTH_TOO_SHORT;
			break;

		case 3:
			// option 3 depends on chosen packet type
			switch (packetTypeSelection) {
			case RRQ:
			case WRQ:
				illegalOpTypeSelection = IllegalOperationType.FILENAME;
				break;

			case DATA:
			case ACK:
				illegalOpTypeSelection = IllegalOperationType.BLOCKNUM;
				break;

			case ERROR:
				illegalOpTypeSelection = IllegalOperationType.ERRCODE;
			}

			break;

		case 4:
			// option 4 depends on chosen packet type
			switch (packetTypeSelection) {
			case RRQ:
			case WRQ:
				illegalOpTypeSelection = IllegalOperationType.MODE;
				break;

			case DATA:
			case ACK:
				throw new RuntimeException("invalid option chosen from illegal op menu");

			case ERROR:
				illegalOpTypeSelection = IllegalOperationType.ERRMSG;
			}			
		}

		System.out.println("\nChosen parameters: ");
		System.out.println("Process to receive error: " + receiverProcessSelection.name());
		System.out.println("Packet type to modify: " + packetTypeSelection.name());
		System.out.println("Packet field/property to modify: " + illegalOpTypeSelection.name());
		System.out.println("\n");

		System.out.println("==== EXECUTING SIMULATION ====");

		simulateIllegalOperation();
	}

	private int getInput() {

		String input = null;

		do {
			System.out.print("> ");
			input = keyboard.nextLine();
			if (input.toLowerCase().charAt(0) == 'q')
				quit();

		} while (!input.matches("[1-9]")); // keep looping until single digit is entered 

		return Integer.parseInt(input);
	}

	private void relayRequestWithoutErrors() {	
		System.out.println("No error mode selected.\n");

		byte data[] = new byte[PacketUtil.BUF_SIZE];		
		receivePacket = new DatagramPacket(data, data.length);			
		receivePacket.getLength();

		// listen for a client packet
		System.out.println("listening for client packet on port 68...");
		try {
			clientRecvSocket.receive(receivePacket);
		} catch (IOException e) {
			System.out.println("IOException caught receiving client packet: " + e.getMessage());			
			System.exit(1);
		}	
		System.out.println("received client packet");
		printOpcode(receivePacket);


		clientIP = receivePacket.getAddress();
		clientPort = receivePacket.getPort();
		// copy the data to a new packet

		sendPacket = new DatagramPacket(receivePacket.getData(), receivePacket.getLength(), serverIP, SERVER_PORT);

		// send new packet to server

		try {
			serverSendRecvSocket.send(sendPacket);
		} catch (IOException e) {
			e.printStackTrace();
			System.exit(1);
		}

		// receive server response
		System.out.println("waiting for server response...");
		try {
			serverSendRecvSocket.receive(receivePacket);
		} catch (IOException e) {
			System.out.println("IOException caught receiving server packet: " + e.getMessage());			
			System.exit(1);
		}	
		System.out.println("received server packet");
		printOpcode(receivePacket);


		// copy the data to a new packet

		// set up a new socket to send to the client
		DatagramSocket clientSendSocket = null;
		try {
			clientSendSocket = new DatagramSocket();
		} catch (SocketException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		}

		sendPacket = new DatagramPacket(receivePacket.getData(), receivePacket.getLength(), clientIP, clientPort);

		// send new packet to client
		System.out.println("sending server response to client...");
		try {
			clientSendSocket.send(sendPacket);
		} catch (IOException e) {
			e.printStackTrace();
			System.exit(1);
		}

		printEndSimulation();
	}
	
	private void simulateUnknownTID() {
		
		// TODO add expected packet enforcement so different packet types can be tested definitively
		
		// build message based on previous selection
		String message = receiverProcessSelection == ProcessType.CLIENT ?
				"Client" :
					"Server"; 
		message += " selected.";
		System.out.println(message);
		
		if (receiverProcessSelection == ProcessType.CLIENT) {
			System.out.println("If RRQ is made, Client will receive DATA 2 from unknown TID");
			System.out.println("If WRQ is made, Client will receive ACK 1 from unknown TID");			
		} else { // receiverProcess == ProcessType.SERVER
			System.out.println("If RRQ is made, Server will receive ACK 1 from unknown TID");
			System.out.println("If WRQ is made, Server will receive DATA 1 from unknown TID");
		}
		
		byte data[] = new byte[PacketUtil.BUF_SIZE];
		receivePacket = new DatagramPacket(data, data.length);

		// listen for a client packet
		receivePacketFromProcess(clientRecvSocket, ProcessType.CLIENT, "RRQ/WRQ");
						
		clientIP = receivePacket.getAddress();
		clientPort = receivePacket.getPort();
		
		// check packet type
		if ( ! (receivedPacketType == PacketType.RRQ || receivedPacketType == PacketType.WRQ) ) {
			System.out.println("Wrong packet type received from client! (expected RRQ or WRQ) [FAIL]");
			System.out.println("terminating simulation");
			return;
		}
		
		
		// pass request to server (establish client TID to the server)
		sendPacket = new DatagramPacket(receivePacket.getData(), receivePacket.getLength(), 
				serverIP, SERVER_PORT);
		
		// send new packet to server
		sendPacketToProcess(serverSendRecvSocket, ProcessType.SERVER, receivedPacketType.name());
		
		// receive server response
		receivePacketFromProcess(serverSendRecvSocket, ProcessType.SERVER, "DATA/ACK/ERROR");
		
		// maybe ERROR should be allowed here...
		if (isErrorPacket(receivePacket)) {
			System.out.println("unexpected ERROR packet received from client! [FAIL]");
			System.out.println("terminating simulation");
			return;
		}
		
		// save the server's TID for this simulation
		int serverTID = receivePacket.getPort();
		
		// pass server response to client, establish server TID to the client
		DatagramSocket clientSendRecvSocket = null;
		try {
			clientSendRecvSocket = new DatagramSocket();
		} catch (SocketException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		}

		sendPacket = new DatagramPacket(receivePacket.getData(), receivePacket.getLength(), clientIP, clientPort);
		sendPacketToProcess(clientSendRecvSocket, ProcessType.CLIENT, "DATA/ACK/ERROR");
		
		// get first client DATA / ACK		
		receivePacketFromProcess(clientSendRecvSocket, ProcessType.CLIENT, "DATA/ACK/ERROR");		
		
		// create a new socket to generate TID error
		DatagramSocket unknownTIDSocket = null;
		try {
			unknownTIDSocket = new DatagramSocket();
		} catch (SocketException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		}
		
		sendPacket = new DatagramPacket(receivePacket.getData(), receivePacket.getLength());
		sendPacket.setAddress(serverIP);
		sendPacket.setPort(serverTID);		
		
		if (receiverProcessSelection == ProcessType.SERVER) {
			System.out.println("sending client packet to server from unknown TID...");
			// server is receiving the unknown TID packet, send this from the unknown TID socket
			sendPacketToProcess(unknownTIDSocket, ProcessType.SERVER, "DATA/ACK/ERROR");
			
			// receive server response (should be an ERROR with code 5)
			receivePacketFromProcess(unknownTIDSocket, ProcessType.SERVER, "ERROR");
						
			if (receivePacket.getData()[1] == PacketUtil.ERROR_FLAG) {
				
				if (receivePacket.getData()[3] == PacketUtil.ERR_UNKNOWN_TID) {
					String msg = getErrMessage(receivePacket.getData());
					System.out.println("Server responded with ERROR code 5 as expected! [PASS]");
					System.out.printf("error message from packet: \"%s\"", msg);
				} else {
					String msg = getErrMessage(receivePacket.getData());
					System.out.printf("Server responded with ERROR code %d (not 5 as expected)", receivePacket.getData()[3]);
					System.out.printf("error message from packet: \"%s\"", msg);
				}
				
				
			} else { // not an error packet
				System.out.println("Server response was not an ERROR packet as expected [FAIL]"); 
			}
			
			
		} else { 
			// client is receiving unknown TID packet, send this out server socket
			sendPacketToProcess(serverSendRecvSocket, ProcessType.SERVER, "DATA/ACK/ERROR");
			
			// receive server response
			receivePacketFromProcess(serverSendRecvSocket, ProcessType.SERVER, "DATA/ACK/ERROR");
						
			// pass response to client from unknown TID socket
			sendPacket = new DatagramPacket(receivePacket.getData(), receivePacket.getLength());
			sendPacket.setAddress(clientIP);
			sendPacket.setPort(clientPort);

			System.out.println("sending server packet to client from unknown TID...");
			sendPacketToProcess(unknownTIDSocket, ProcessType.CLIENT, "DATA/ACK/ERROR from unknown TID");

			// receive client response (should be an ERROR with code 5)
			receivePacketFromProcess(unknownTIDSocket, ProcessType.CLIENT, "ERROR (5)");
			
			if (receivePacket.getData()[1] == PacketUtil.ERROR_FLAG) {

				if (receivePacket.getData()[3] == PacketUtil.ERR_UNKNOWN_TID) {
					String msg = getErrMessage(receivePacket.getData());
					System.out.println("Client responded with ERROR code 5 as expected! [PASS]");
					System.out.printf("error message from packet: \"%s\"\n", msg);
				} else {
					String msg = getErrMessage(receivePacket.getData());
					System.out.printf("Client responded with ERROR code %d (not 5 as expected) [FAIL]\n", receivePacket.getData()[3]);
					System.out.printf("error message from packet: \"%s\"\n", msg);
				}


			} else { // not an error packet
				System.out.println("Client response was not an ERROR packet as expected [FAIL]"); 
			}
		}
		
		printEndSimulation();
		
	}

	private void simulateIllegalOperation() {

		byte data[] = new byte[PacketUtil.BUF_SIZE];		
		receivePacket = new DatagramPacket(data, data.length);			
		receivePacket.getLength();
		
		// this variable is used to keep track of whether the client sent a RRQ or WRQ to start the exchange
		// this is necessary because if packetType is DATA, ACK, or ERROR, we need to know which side is sending
		// DATA packets and which side is sending ACK packets so we know which packet to modify
		boolean startedWithRead = false; 

		// listen for a client packet
		receivePacketFromProcess(clientRecvSocket, ProcessType.CLIENT, "RRQ/WRQ");
		
		clientIP = receivePacket.getAddress();
		clientPort = receivePacket.getPort();
		
		// set up a new socket to send server packets to the client
		DatagramSocket clientSendRecvSocket = null;
		try {
			clientSendRecvSocket = new DatagramSocket();
		} catch (SocketException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		}		

		// if chosen packet type is request packet, need to make sure what was received matches
		if (packetTypeSelection == PacketType.RRQ) {
			if (receivedPacketType != PacketType.RRQ) {
				System.out.println("Wrong packet type received from client! (expected RRQ)");
				System.out.println("terminating simulation");
				return;
			} else {
				// received RRQ, now it must be modified to trigger illegal operation
				sendPacket = getCorruptedPacket(receivePacket, illegalOpTypeSelection);
				sendPacket.setAddress(serverIP);
				sendPacket.setPort(SERVER_PORT);
				
				// send the modified packet
				sendPacketToProcess(serverSendRecvSocket, ProcessType.SERVER, "modified RRQ");
				
				// receive response (should be ERROR)
				receivePacketFromProcess(serverSendRecvSocket, ProcessType.SERVER, "ERROR (4)");
				
				// check to see if server response is correct				
				if (receivedPacketType == PacketType.ERROR) {

					if (receivePacket.getData()[3] == PacketUtil.ERR_ILLEGAL_OP) {
						String msg = getErrMessage(receivePacket.getData());
						System.out.println("Server responded with ERROR code 4 as expected! [PASS]");
						System.out.printf("error message from packet: \"%s\"\n", msg);
					} else {
						String msg = getErrMessage(receivePacket.getData());
						System.out.printf("Server responded with ERROR code %d (not 4 as expected) [FAIL]\n", receivePacket.getData()[3]);
						System.out.printf("error message from packet: \"%s\"\n", msg);
					}

				} else { // not an error packet
					System.out.println("Server response was not an ERROR packet as expected [FAIL]"); 
				}
				
				System.out.println("passing server response to client...");
				// pass server response to client
				sendPacket = new DatagramPacket(receivePacket.getData(), receivePacket.getLength());
				sendPacket.setAddress(clientIP);
				sendPacket.setPort(clientPort);
				
				sendPacketToProcess(clientSendRecvSocket, ProcessType.CLIENT, "DATA/ACK/ERROR");
				
				// end simulation
				printEndSimulation();
				return;
			}

		} else if (packetTypeSelection == PacketType.WRQ) {
			
			if (receivedPacketType != PacketType.WRQ) {				
				System.out.println("Wrong packet type received from client! (expected WRQ)");
				System.out.println("terminating simulation");
				return;
			} else {
				// received WRQ, now it must be modified to trigger illegal operation
				sendPacket = getCorruptedPacket(receivePacket, illegalOpTypeSelection);
				sendPacket.setAddress(serverIP);
				sendPacket.setPort(SERVER_PORT);
				
				// send the modified packet
				sendPacketToProcess(serverSendRecvSocket, ProcessType.SERVER, "modified WRQ");
								
				// receive response (should be ERROR)
				receivePacketFromProcess(serverSendRecvSocket, ProcessType.SERVER, "ERROR (4)");
								
				// check to see if server response is correct				
				if (receivedPacketType != PacketType.ERROR) {

					if (receivePacket.getData()[3] == PacketUtil.ERR_ILLEGAL_OP) {
						String msg = getErrMessage(receivePacket.getData());
						System.out.println("Server responded with ERROR code 4 as expected! [PASS]");
						System.out.printf("error message from packet: \"%s\"", msg);
					} else {
						String msg = getErrMessage(receivePacket.getData());
						System.out.printf("Server responded with ERROR code %d (not 4 as expected) [FAIL]", receivePacket.getData()[3]);
						System.out.printf("error message from packet: \"%s\"", msg);
					}

				} else { // not an error packet
					System.out.println("Server response was not an ERROR packet as expected [FAIL]"); 
				}
				
								
				// pass server response to client
				sendPacket = new DatagramPacket(receivePacket.getData(), receivePacket.getLength());
				sendPacket.setAddress(clientIP);
				sendPacket.setPort(clientPort);				
				sendPacketToProcess(clientSendRecvSocket, ProcessType.CLIENT, receivedPacketType.name());
				
				// end simulation
				printEndSimulation(); 
				return;
				
			}
		} else { // packetType is DATA, ACK, or ERROR
			
			// set startedWithRead so we know which side is sending DATA packets
			startedWithRead = receivePacket.getData()[1] == PacketUtil.READ_FLAG;
			
			// send request to server as normal
			sendPacket = new DatagramPacket(receivePacket.getData(), receivePacket.getLength(), serverIP, SERVER_PORT);
		}
		
		try {
			serverSendRecvSocket.send(sendPacket);
		} catch (IOException e) {
			e.printStackTrace();
			System.exit(1);
		}

		// receive first server response (DATA 1 if RRQ, ACK 0 if WRQ)
		System.out.println("waiting for server response...");
		try {
			serverSendRecvSocket.receive(receivePacket);
		} catch (IOException e) {
			System.out.println("IOException caught receiving server packet: " + e.getMessage());			
			System.exit(1);
		}	
		System.out.println("received server packet");
		printOpcode(receivePacket);
		
		// keep track of port being used by server thread
		int serverTID = receivePacket.getPort();

		// if receiver process is CLIENT...
		// if started with RRQ and packet to modify is DATA, this one can be modified
		// if started with WRQ and packet to modify is ACK, this one can be modified
		// otherwise, pass response to client as normal
		
		if (receiverProcessSelection == ProcessType.CLIENT && (
			(startedWithRead && packetTypeSelection == PacketType.DATA) || 
			(!startedWithRead && packetTypeSelection == PacketType.ACK) ) ) {
			
			sendPacket = getCorruptedPacket(receivePacket, illegalOpTypeSelection);
			sendPacket.setAddress(clientIP);
			sendPacket.setPort(clientPort);
			
			System.out.printf("sending modified %s packet to client...\n", packetTypeSelection.name());
			try {
				clientSendRecvSocket.send(sendPacket);
			} catch (IOException e) {
				e.printStackTrace();
				System.exit(1);
			}
			
			System.out.println("waiting for response from client (expecting ERROR code 4)...");
			try {
				clientSendRecvSocket.receive(receivePacket);
			} catch (IOException e) {
				e.printStackTrace();
				System.exit(1);
			}
			System.out.println("received client packet");
			printOpcode(receivePacket);
			
			// check to see if server response is correct				
			if (receivePacket.getData()[1] == PacketUtil.ERROR_FLAG) {

				if (receivePacket.getData()[3] == PacketUtil.ERR_ILLEGAL_OP) {
					String msg = getErrMessage(receivePacket.getData());
					System.out.println("Client responded with ERROR code 4 as expected! [PASS]");
					System.out.printf("error message from packet: \"%s\"", msg);
				} else {
					String msg = getErrMessage(receivePacket.getData());
					System.out.printf("Client responded with ERROR code %d (not 4 as expected) [FAIL]", receivePacket.getData()[3]);
					System.out.printf("error message from packet: \"%s\"", msg);
				}

			} else { // not an error packet
				System.out.println("Client response was not an ERROR packet as expected [FAIL]"); 
			}
			
			// pass client response to server
			sendPacket = new DatagramPacket(receivePacket.getData(), receivePacket.getLength());
			sendPacket.setAddress(serverIP);
			sendPacket.setPort(serverTID);
			try {
				serverSendRecvSocket.send(sendPacket);
			} catch (IOException e) {
				e.printStackTrace();
				System.exit(1);
			}
			
			// end simulation
			printEndSimulation(); 
			return;
			
		} 
		
		System.out.println("SHOULDN'T BE HERE YET");
		
		
		// send new packet to client
		System.out.println("sending server response to client...");
		try {
			clientSendRecvSocket.send(sendPacket);
		} catch (IOException e) {
			e.printStackTrace();
			System.exit(1);
		}

		printEndSimulation();
	}

	private static DatagramPacket getCorruptedPacket(DatagramPacket originalPacket, IllegalOperationType illegalOpType) {

		byte[] newData = originalPacket.getData();
		int newLength = originalPacket.getLength();

		switch (illegalOpType) {
		case OPCODE: // invalidate the opcode field
			newData[0] = 0xf; // set the very first byte to non-zero
			break;

		case FILENAME: // invalidate the filename field (only for RRQ/WRQ)
			// TODO: find a better way to invalidate text fields?
			newData[2] = 0x1; // set the first filename byte to non-printable character
			break;

		case MODE: // invalidate the mode field (only for RRQ/WRQ)
			// TODO: find a better way to invalidate text fields?
			int index = getFilenameLength(newData) + 3; // get the index of the first mode character
			newData[index] = 0x1; // set the first mode byte to non-printable character
			break;

		case BLOCKNUM: // invalidate the block number field (only for DATA/ACK)
			// "invalid" block number depends on what the current block number is
			// for now, just set it to zero - this is valid only for the first ACK in a WRQ
			newData[2] = 0x0;
			newData[3] = 0x0;
			break;

		case ERRCODE: // invalidate the error code field (only for ERROR)
			newData[2] = 0xf; // set the first error code byte to non-zero 
			break;

		case ERRMSG: // invalidate the error message field (only for ERROR)
			// TODO: find a better way to invalidate text fields?
			newData[4] = 0x1; // set the first message byte to non-printable character
			break;
		
		case LENGTH_TOO_SHORT:
			newLength -= 2; // shorten the length by 2 bytes
			break;
		default:
			break;			
		}

		DatagramPacket newPacket = new DatagramPacket(newData, newLength);
		return newPacket;

	}

	/*
	 * returns the length of a filename stored in a RRQ / WRQ packet's data
	 */
	private static int getFilenameLength (byte[] data) {

		int length = 0;
		for (int i = 2; i < data.length; i++) {
			if (data[i] == 0) {
				length = i - 2;
				break;
			}
		}

		return length;
	}
	
	/*
	 * returns the message from an ERROR packet
	 */
	private static String getErrMessage(byte[] data) {

		StringBuilder sb = new StringBuilder();
		
		for (int i = 4; i < data.length; i++) {
			sb.append((char)data[i]);
			if (data[i] == 0)				
				break;
		}

		return sb.toString();
	}

	private void printOpcode(DatagramPacket packet) {
		byte[] data = packet.getData();
		System.out.printf("[opcode: %02x]\n", data[1]);
	}
	
	private boolean isErrorPacket(DatagramPacket packet) {
		return packet.getData()[1] == PacketUtil.ERROR_FLAG;
	}

	private void quit() {
		System.out.println("\n*** Exiting! ***");
		closeResources();		
		System.exit(0);
	}
	
	private void closeResources() {
		clientRecvSocket.close();
		serverSendRecvSocket.close();
	}
	
	private void receivePacketFromProcess(DatagramSocket recvSocket, ProcessType sendProcess, String expectedPacketStr) {
		
		// listen for a packet from given source process
		// note this function doesn't actually enforce the packet type received, since it might not always matter
		System.out.printf("listening for %s packet from %s (IP: %s, UDP port %d)... ", expectedPacketStr, 
				sendProcess, recvSocket.getLocalAddress(), recvSocket.getLocalPort());
		try {
			recvSocket.receive(receivePacket);
		} catch (IOException e) {
			System.out.printf("IOException caught receiving %s packet: %s", sendProcess, e.getMessage());
			System.out.println("cannot proceed, terminating simulation");
			System.exit(1);
		}	
		receivedPacketType = getPacketType(receivePacket);
		System.out.printf("received %s packet ", receivedPacketType.name());
		printOpcode(receivePacket);
	}
	
	private void sendPacketToProcess(DatagramSocket sendSocket, ProcessType recvProcess, String sendPacketStr) {		
		
		// sends a packet to a given destination process
		System.out.printf("sending %s packet to %s (IP: %s, UDP port %d) ... ", 
				sendPacketStr, recvProcess, sendPacket.getAddress(), sendPacket.getPort());
		
		PacketType sendPacketType = getPacketType(sendPacket);
		
		try {
			sendSocket.send(sendPacket);
		} catch (IOException e) {
			System.out.printf("IOException caught sending %s packet: %s", recvProcess, e.getMessage());
			System.out.println("cannot proceed, terminating simulation");
			System.exit(1);
		}	
		
		System.out.printf("sent %s packet ", sendPacketType.name());
		printOpcode(sendPacket);
	}
	
	private PacketType getPacketType(DatagramPacket packet) {
		
		switch (packet.getData()[1]) {
		case PacketUtil.READ_FLAG:
			return PacketType.RRQ;
			
		case PacketUtil.WRITE_FLAG:
			return PacketType.WRQ;
			
		case PacketUtil.DATA_FLAG:
			return PacketType.DATA;
			
		case PacketUtil.ACK_FLAG:
			return PacketType.ACK;
			
		case PacketUtil.ERROR_FLAG:
			return PacketType.ERROR;
			
		default:
			// we should only be parsing server or client packets so 
			// only genuine network errors will cause this
			return null; 
		}
	} 
	
	private void printEndSimulation() {
		System.out.println("\nsimulation complete.");
		System.out.println("press enter to continue");
		keyboard.nextLine(); 
	}

	public static void main(String args[]) {
		ErrorSimulator sim = new ErrorSimulator();
		try {
			while (true) {
				sim.showMainMenu();
			}
		} finally {
			sim.closeResources();
		}

	}

}

