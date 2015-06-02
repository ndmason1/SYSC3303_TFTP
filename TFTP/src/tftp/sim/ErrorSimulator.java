
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
import tftp.server.Server;

/**
 * An ErrorSimulator acts as a man-in-the-middle between a TFTP server and client.
 * It behaves like the server from the perspective of the client, and behaves like
 * a client from the perspective of the server. It may run in several different modes
 * to simulate network-related errors.
 * 
 *  Note that the Client must be run in the correct mode to communicate with the ErrorSimulator.
 */
public class ErrorSimulator {
	
	private static final int LISTEN_PORT = 78; // not using 68 due to its usage by windows DHCP service	

	// sockets used for communicating with the client and server, respectively	
	// new sockets
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

	/**
	 * Constructs an ErrorSimulator. 
	 *
	 */
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

	/**
	 * Starts the execution of an ErrorSimulator and displays the top level menu.
	 */
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

	/**
	 * Display a menu for the user to select which process receives a packet.
	 */
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

	/**
	 * Displays a menu for the user to select a type of packet that should be modified.
	 * Currently only used for illegal operation mode.
	 */
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
			System.out.println("(3) DATA (must start with WRQ)");
			System.out.println("(4) ACK (must start with RRQ)");
			System.out.println("(5) ERROR");
		} else {
			System.out.println("(1) DATA (must start with RRQ)");
			System.out.println("(2) ACK (must start with WRQ)");
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

	/**
	 * Displays a menu for the user to select a method of modifying a packet
	 * so that its recipient triggers an illegal operation error.
	 */
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

		System.out.println("\nChosen parameters for illegal operation simulation: ");
		System.out.println("Process to receive error: " + receiverProcessSelection.name());
		System.out.println("Packet type to modify: " + packetTypeSelection.name());
		System.out.println("Packet field/property to modify: " + illegalOpTypeSelection.name());
		System.out.println("\n");
		
		simulateIllegalOperation();
	}

	/**
	 * Utility function for getting a user's menu selection.
	 */
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
	
	/**
	 * Runs "No error" mode. Just lets a file transfer proceed as normal.
	 */
	private void relayRequestWithoutErrors() {	
		System.out.println("No error mode selected.");
		System.out.println("Packets received from client will be forwarded to server, and vice versa.\n");

		byte data[] = new byte[PacketUtil.BUF_SIZE];		
		receivePacket = new DatagramPacket(data, data.length);			
		receivePacket.getLength();

		// listen for a client packet
		receivePacketFromProcess(clientRecvSocket, ProcessType.CLIENT, "RRQ/WRQ");

		clientIP = receivePacket.getAddress();
		clientPort = receivePacket.getPort();
		
		// forward request to server
		sendPacket = new DatagramPacket(receivePacket.getData(), receivePacket.getLength(), serverIP, Server.SERVER_PORT);
		sendPacketToProcess(serverSendRecvSocket, ProcessType.SERVER, receivedPacketType.name());

		// receive server response
		System.out.println("waiting for server response...");
		receivePacketFromProcess(serverSendRecvSocket, ProcessType.SERVER, "DATA/ACK/ERROR");
		
		// save port of server thread handling this request 
		int serverTID = receivePacket.getPort();	

		// set up a new socket to use for sending DATA/ACK to the client (act as server thread)
		DatagramSocket clientSendRecvSocket = null;
		try {
			clientSendRecvSocket = new DatagramSocket();
		} catch (SocketException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		}
				
		sendPacket = new DatagramPacket(receivePacket.getData(), receivePacket.getLength(), clientIP, clientPort);

		// forward response to client		
		sendPacketToProcess(clientSendRecvSocket, ProcessType.CLIENT, receivedPacketType.name());
		
		// carry out rest of transfer - first process supplied is the receiving process, so client
		finishTransfer(ProcessType.CLIENT, clientSendRecvSocket, clientIP, clientPort, 
				ProcessType.SERVER, serverSendRecvSocket, serverIP, serverTID);

		printEndSimulation();
	}
	
	/**
	 * Runs "Unknown TID" mode. 
	 * A packet's contents are not modified, but it is sent from a different socket so as to trigger
	 * an "Unknown TID" (transfer ID) error.
	 * The packet selected to trigger the error depends on whether the client sends a RRQ or WRQ to
	 * start the simulation and the process chosen to receive the packet with unknown TID.
	 */
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
		
		System.out.println("\n==== EXECUTING SIMULATION ====\n");
		
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
				serverIP, Server.SERVER_PORT);
		
		// send new packet to server
		sendPacketToProcess(serverSendRecvSocket, ProcessType.SERVER, receivedPacketType.name());
		
		// receive server response
		receivePacketFromProcess(serverSendRecvSocket, ProcessType.SERVER, "DATA/ACK/ERROR");
		
		// maybe ERROR should be allowed here...
//		if (isErrorPacket(receivePacket)) {
//			System.out.println("unexpected ERROR packet received from client! [FAIL]");
//			System.out.println("terminating simulation");
//			return;
//		}
		
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
		sendPacketToProcess(clientSendRecvSocket, ProcessType.CLIENT, receivedPacketType.name());
		
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
			System.out.println("[ next packet will be sent from unknown TID ]");
			// server is receiving the unknown TID packet, send this from the unknown TID socket
			sendPacketToProcess(unknownTIDSocket, ProcessType.SERVER, receivedPacketType.name());
			
			// receive server response (should be an ERROR with code 5)
			receivePacketFromProcess(unknownTIDSocket, ProcessType.SERVER, "ERROR");
			
			// check result and display
			printSimulationResult(ProcessType.SERVER, PacketUtil.ERR_UNKNOWN_TID);
			
			// TODO finish transfer by sending packet from original client TID
			// and finish remaining exchange
			
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
			
			// check result and display
			printSimulationResult(ProcessType.CLIENT, PacketUtil.ERR_UNKNOWN_TID);
			
		}
		
		printEndSimulation();
		
	}

	/**
	 * Runs "Illegal Operation" mode. 
	 * A packet's contents are modified so as to trigger an Illegal Operation error.
	 * The packet selected to trigger the error is determined based on the user's
	 * selection of packet type to modify and the process who is reciveing the 
	 * modified packet. 
	 */
	private void simulateIllegalOperation() {
<<<<<<< HEAD
=======
		
		System.out.println("==== EXECUTING SIMULATION ====");
>>>>>>> 62f467077f30821ff999d698fecb5850e09fe45f

		byte data[] = new byte[PacketUtil.BUF_SIZE];		
		receivePacket = new DatagramPacket(data, data.length);			
		receivePacket.getLength();
		
		// this variable is used to keep track of whether the client sent a RRQ or WRQ to start the exchange
		// this is necessary because if packetType is DATA, ACK, or ERROR, we need to know which side is sending
		// DATA packets and which side is sending ACK packets so we know which packet to modify		 
		PacketType startingRequestType;

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
		
		// make sure starting request type actually IS a request
		startingRequestType = receivedPacketType;
		if (startingRequestType != PacketType.RRQ && startingRequestType != PacketType.WRQ) {
			System.out.println("Need to start with request packet");
			System.out.println("terminating simulation");
			return;
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
				sendPacket.setPort(Server.SERVER_PORT);
				
				// send the modified packet
				sendPacketToProcess(serverSendRecvSocket, ProcessType.SERVER, "modified RRQ");
				
				// receive response (should be ERROR)
				receivePacketFromProcess(serverSendRecvSocket, ProcessType.SERVER, "ERROR (4)");
				
				// check to see if server response is correct		
				printSimulationResult(ProcessType.SERVER, PacketUtil.ERR_ILLEGAL_OP);
				
				// pass server response to client
				sendPacket = new DatagramPacket(receivePacket.getData(), receivePacket.getLength());
				sendPacket.setAddress(clientIP);
				sendPacket.setPort(clientPort);
				
				sendPacketToProcess(clientSendRecvSocket, ProcessType.CLIENT, "DATA/ACK/ERROR");
				
				// if the last packet received wasn't an error, finish the transfer so client goes back to ready state
				if (receivedPacketType != PacketType.ERROR) {
					int serverTID = receivePacket.getPort();
					System.out.println("finishing transfer...");
					finishTransfer(ProcessType.CLIENT, clientSendRecvSocket, clientIP, clientPort, 
							ProcessType.SERVER, serverSendRecvSocket, serverIP, serverTID);
				}
				
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
				sendPacket.setPort(Server.SERVER_PORT);
				
				// send the modified packet
				sendPacketToProcess(serverSendRecvSocket, ProcessType.SERVER, "modified WRQ");
								
				// receive response (should be ERROR)
				receivePacketFromProcess(serverSendRecvSocket, ProcessType.SERVER, "ERROR (4)");
								
				// check to see if server response is correct		
				printSimulationResult(ProcessType.SERVER, PacketUtil.ERR_ILLEGAL_OP);				
								
				// pass server response to client
				sendPacket = new DatagramPacket(receivePacket.getData(), receivePacket.getLength());
				sendPacket.setAddress(clientIP);
				sendPacket.setPort(clientPort);				
				sendPacketToProcess(clientSendRecvSocket, ProcessType.CLIENT, receivedPacketType.name());
				
				// if the last packet received wasn't an error, finish the transfer so client goes back to ready state
				if (receivedPacketType != PacketType.ERROR) {
					int serverTID = receivePacket.getPort();
					System.out.println("finishing transfer...");
					finishTransfer(ProcessType.CLIENT, clientSendRecvSocket, clientIP, clientPort, 
							ProcessType.SERVER, serverSendRecvSocket, serverIP, serverTID);
				}
				
				// end simulation
				printEndSimulation(); 
				return;
				
			}
		} else { // packetTypeSelection is DATA, ACK, or ERROR
			
			// check that the parameters make sense e.g. server cant recv modified ACK if started with WRQ
			
			boolean exitSimulation = false;
			
			if (receiverProcessSelection == ProcessType.SERVER) {
				
				if (startingRequestType == PacketType.WRQ && packetTypeSelection == PacketType.ACK) {
					
					System.out.println("Wrong packet type received from client! (expected RRQ)");
					exitSimulation = true;
					
				} else if (startingRequestType == PacketType.RRQ && packetTypeSelection == PacketType.DATA) {
					
					System.out.println("Wrong packet type received from client! (expected WRQ)");
					exitSimulation = true;
				}
				
			} else { // receiverProcessSelection == ProcessType.CLIENT
				
				if (startingRequestType == PacketType.WRQ && packetTypeSelection == PacketType.DATA) {
					
					System.out.println("Wrong packet type received from client! (expected RRQ)");
					exitSimulation = true;
					
				} else if (startingRequestType == PacketType.RRQ && packetTypeSelection == PacketType.ACK) {
					
					System.out.println("Wrong packet type received from client! (expected WRQ)");
					exitSimulation = true;
				}
				
			}
			
			if (exitSimulation) {
				System.out.println("cannot proceed, finishing simulation...");

				// send request to server
				sendPacket = new DatagramPacket(receivePacket.getData(), receivePacket.getLength(), serverIP, Server.SERVER_PORT);				
				sendPacketToProcess(serverSendRecvSocket, ProcessType.SERVER, receivedPacketType.name());

				// get server response
				receivePacketFromProcess(serverSendRecvSocket, ProcessType.SERVER, receivedPacketType.name());
				int serverTID = receivePacket.getPort();

				// send to client
				sendPacket = new DatagramPacket(receivePacket.getData(), receivePacket.getLength(), clientIP, clientPort);				
				sendPacketToProcess(clientSendRecvSocket, ProcessType.CLIENT, receivedPacketType.name());

				// finish transfer so client goes back to a ready state
				finishTransfer(ProcessType.CLIENT, clientSendRecvSocket, clientIP, clientPort, 
						ProcessType.SERVER, serverSendRecvSocket, serverIP, serverTID);

				printEndSimulation();
				return;
			}
			
			// good to proceed, send request to server as normal
			sendPacket = new DatagramPacket(receivePacket.getData(), receivePacket.getLength(), serverIP, Server.SERVER_PORT);
		}
		
		
		
		sendPacketToProcess(serverSendRecvSocket, ProcessType.SERVER, receivedPacketType.name());

		// receive first server response (DATA 1 if RRQ, ACK 0 if WRQ)
		String expectPacket = startingRequestType == PacketType.RRQ ? "DATA 1" : "ACK 0";
		receivePacketFromProcess(serverSendRecvSocket, ProcessType.SERVER, expectPacket);		
		
		// keep track of port being used by server thread
		int serverTID = receivePacket.getPort();

		// if receiver process is CLIENT...
		// if started with RRQ, packet to modify is DATA, this one can be modified
		// if started with WRQ, packet to modify is ACK, this one can be modified		
		
		if (receiverProcessSelection == ProcessType.CLIENT) {
			if (packetTypeSelection == PacketType.DATA || packetTypeSelection == PacketType.ACK ) {
				
				sendPacket = getCorruptedPacket(receivePacket, illegalOpTypeSelection);
				sendPacket.setAddress(clientIP);
				sendPacket.setPort(clientPort);

				// send modified packet
				sendPacketToProcess(clientSendRecvSocket, ProcessType.CLIENT, "modified " + receivedPacketType.name());			

				// receive response, expecting ERROR 4
				receivePacketFromProcess(clientSendRecvSocket, ProcessType.CLIENT, "ERROR (4)");

				// check to see if client response is correct		
				printSimulationResult(ProcessType.CLIENT, PacketUtil.ERR_ILLEGAL_OP);

				// pass client response to server				
				sendPacket = new DatagramPacket(receivePacket.getData(), receivePacket.getLength());
				sendPacket.setAddress(serverIP);
				sendPacket.setPort(serverTID);
				sendPacketToProcess(serverSendRecvSocket, ProcessType.SERVER, receivedPacketType.name());

				// if the last packet received wasn't an error, finish the transfer so client goes back to ready state
				if (receivedPacketType != PacketType.ERROR) {
					System.out.println("finishing transfer...");
					finishTransfer(ProcessType.SERVER, serverSendRecvSocket, serverIP, serverTID,
							ProcessType.CLIENT, clientSendRecvSocket, clientIP, clientPort);
				}

				// end simulation
				printEndSimulation(); 
				return;
				
			} else { // selected packet type must be ERROR
				
				// fake an error packet sent from server
				
				PacketUtil packetUtil = new PacketUtil(clientIP, clientPort);
				DatagramPacket errPacket = packetUtil.formErrorPacket(PacketUtil.ERR_ACCESS_VIOLATION, 
						"user does not have permission to access that file");
				
				sendPacket = getCorruptedPacket(errPacket, illegalOpTypeSelection);

				// send modified ERROR packet
				sendPacketToProcess(clientSendRecvSocket, ProcessType.CLIENT, "modified ERROR (2)");			

				// receive response, expecting ERROR 4
				receivePacketFromProcess(clientSendRecvSocket, ProcessType.CLIENT, "ERROR (4)");

				// check to see if client response is correct		
				printSimulationResult(ProcessType.CLIENT, PacketUtil.ERR_ILLEGAL_OP);

				// pass client response to server				
				sendPacket = new DatagramPacket(receivePacket.getData(), receivePacket.getLength());
				sendPacket.setAddress(serverIP);
				sendPacket.setPort(serverTID);
				sendPacketToProcess(serverSendRecvSocket, ProcessType.SERVER, receivedPacketType.name());

				// if the last packet received wasn't an error, finish the transfer so client goes back to ready state
				if (receivedPacketType != PacketType.ERROR) {
					System.out.println("finishing transfer...");
					finishTransfer(ProcessType.SERVER, serverSendRecvSocket, serverIP, serverTID,
							ProcessType.CLIENT, clientSendRecvSocket, clientIP, clientPort);
				}

				// end simulation
				printEndSimulation(); 
				return;				
			}
		} 		
		
		// send server response to client as normal
		sendPacket = new DatagramPacket(receivePacket.getData(), receivePacket.getLength());
		sendPacket.setAddress(clientIP);
		sendPacket.setPort(clientPort);
		sendPacketToProcess(clientSendRecvSocket, ProcessType.CLIENT, receivedPacketType.name());
		
		// get client response
		receivePacketFromProcess(clientSendRecvSocket, ProcessType.CLIENT, "DATA/ACK/ERROR");
		
		// now we know that receiver process is server
		// simulate based on selected packet type
		if (packetTypeSelection == PacketType.DATA || packetTypeSelection == PacketType.ACK ) {
			
			sendPacket = getCorruptedPacket(receivePacket, illegalOpTypeSelection);
			sendPacket.setAddress(serverIP);
			sendPacket.setPort(serverTID);

			// send modified packet to server
			sendPacketToProcess(serverSendRecvSocket, ProcessType.SERVER, "modified " + receivedPacketType.name());			

			// receive response, expecting ERROR 4
			receivePacketFromProcess(serverSendRecvSocket, ProcessType.SERVER, "ERROR (4)");

			// check to see if server response is correct		
			printSimulationResult(ProcessType.SERVER, PacketUtil.ERR_ILLEGAL_OP);

			// pass server response to client
			
			sendPacket = new DatagramPacket(receivePacket.getData(), receivePacket.getLength());
			sendPacket.setAddress(clientIP);
			sendPacket.setPort(clientPort);
			
			sendPacketToProcess(clientSendRecvSocket, ProcessType.CLIENT, receivedPacketType.name());

			// if the last packet received wasn't an error, finish the transfer so client goes back to ready state
			if (receivedPacketType != PacketType.ERROR) {
				System.out.println("finishing transfer...");
				finishTransfer(ProcessType.CLIENT, clientSendRecvSocket, clientIP, clientPort,
						ProcessType.SERVER, serverSendRecvSocket, serverIP, serverTID);
			}

			// end simulation
			printEndSimulation(); 
			return;
			
		} else { // selected packet type must be ERROR
			
			// fake an error packet sent from client
			
			PacketUtil packetUtil = new PacketUtil(serverIP, serverTID);
			DatagramPacket errPacket = packetUtil.formErrorPacket(PacketUtil.ERR_ACCESS_VIOLATION, 
					"user does not have permission to access that file");
			
			sendPacket = getCorruptedPacket(errPacket, illegalOpTypeSelection);

			// send modified ERROR packet
			sendPacketToProcess(serverSendRecvSocket, ProcessType.SERVER, "modified ERROR (2)");			

			// receive response, expecting ERROR 4
			receivePacketFromProcess(serverSendRecvSocket, ProcessType.SERVER, "ERROR (4)");

			// check to see if server response is correct		
			printSimulationResult(ProcessType.SERVER, PacketUtil.ERR_ILLEGAL_OP);

			// pass server response to client
			
			sendPacket = new DatagramPacket(receivePacket.getData(), receivePacket.getLength());
			sendPacket.setAddress(clientIP);
			sendPacket.setPort(clientPort);

			sendPacketToProcess(clientSendRecvSocket, ProcessType.CLIENT, receivedPacketType.name());

			// if the last packet received wasn't an error, finish the transfer so client goes back to ready state
			if (receivedPacketType != PacketType.ERROR) {
				System.out.println("finishing transfer...");
				finishTransfer(ProcessType.CLIENT, clientSendRecvSocket, clientIP, clientPort,
						ProcessType.SERVER, serverSendRecvSocket, serverIP, serverTID);
			}

			// end simulation
			printEndSimulation(); 
			return;				
		}
		
	}

	/**
	 * Utility function to return a packet that has been modified to trigger an
	 * illegal operation error.
	 * 
	 *  @param originalPacket	the packet to be modified
	 *  @param illegalOpType	the method of modification
	 *  @return 				a modified DatagramPacket
	 */
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

	/**
	 * Utility function to return the length of a filename contained in a RRQ or WRQ packet.
	 * 
	 *  @param data		the contents of a request packet
	 *  @return 		the length of the string
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
	
	/**
	 * Utility function to return the error message contained in an ERROR packet.
	 * 
	 *  @param data		the contents of an ERROR packet
	 *  @return 		the error message
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

	/**
	 * Utility function to display the opcode of a TFTP packet.
	 * 
	 *  @param packet	the packet
	 */
	private void printOpcode(DatagramPacket packet) {
		byte[] data = packet.getData();
		System.out.printf("[opcode: %02x]\n", data[1]);
	}
	
	/**
	 * Utility function to check if a packet is an ERROR packet.
	 * 
	 *  @param packet	the packet
	 *  @return			true if ERROR packet
	 */
	private boolean isErrorPacket(DatagramPacket packet) {
		return packet.getData()[1] == PacketUtil.ERROR_FLAG;
	}

	/**
	 * Terminate the ErrorSimulator's execution. 
	 */
	private void quit() {
		System.out.println("\n*** Exiting! ***");
		closeResources();		
		System.exit(0);
	}
	
	/**
	 * Close instance sockets. 
	 */
	private void closeResources() {
		clientRecvSocket.close();
		serverSendRecvSocket.close();
	}
	
	/**
	 * Listens for a packet from the given process and displays information.
	 * Sets receivedPacket and receivedPacketType to the packet that was 
	 * received and its type, respectively.
	 * 
	 *  @param recvSocket			the DatagramSocket to listen on
	 *  @param sendProcess			the process (client or server) expected to send a packet
	 *  @param expectedPacketStr	a string describing the expected type of packet to receive, which is displayed
	 */
	private void receivePacketFromProcess(DatagramSocket recvSocket, ProcessType sendProcess, String expectedPacketStr) {
		
		// listen for a packet from given source process
		// note this function doesn't actually enforce the packet type received, since it might not always matter
		System.out.printf("listening on UDP port %s for %s packet from %s ... ", recvSocket.getLocalPort(), 
				expectedPacketStr, sendProcess);
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
	
	/**
	 * Sends a packet to the given process and displays information.
	 * 
	 * 
	 *  @param sendSocket		the DatagramSocket to listen on
	 *  @param recvProcess		the process (client or server) who should be listening for the packet
	 *  @param sendPacketStr	a string describing the packet being sent, which is displayed
	 */
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
	
	/**
	 * Displays results from an error simulation by inspecting the packet that should have been an ERROR
	 * packet.  
	 * 
	 *  @param errorSource		the process (client or server) who sent the ERROR packet
	 *  @param expectedErrCode	the TFTP error code that should be set
	 */
	private void printSimulationResult(ProcessType errorSource, byte expectedErrCode) {
		
		String srcProcName = errorSource.name().toLowerCase();
		
		if (receivedPacketType == PacketType.ERROR) {

			if (receivePacket.getData()[3] == expectedErrCode) {
				String msg = getErrMessage(receivePacket.getData());
				System.out.printf("%s responded with ERROR code %d as expected! [PASS]\n", srcProcName, expectedErrCode);
				System.out.printf("error message from packet: \"%s\"\n", msg);
			} else {
				String msg = getErrMessage(receivePacket.getData());
				System.out.printf("%s responded with ERROR code %d (not %d as expected) [FAIL]\n", 
						srcProcName, receivePacket.getData()[3], expectedErrCode);
				System.out.printf("error message from packet: \"%s\"\n", msg);
			}

		} else { // not an error packet
			System.out.printf("%s response was not an ERROR packet as expected [FAIL]\n", srcProcName); 
		}
	}
	
	/**
	 * Get the type of a TFTP packet.   
	 * 
	 *  @param packet	the packet to inspect
	 *  @return 		the type of the packet
	 */
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
	
	/**
	 * Display message to indicate end of simulation and receives user input to reset the menu.
	 */
	private void printEndSimulation() {
		System.out.println("\nsimulation complete.");
		System.out.println("press enter to continue");
		keyboard.nextLine(); 
	}
	
	/**
	 * Complete a file transfer by forwarding the remaining packets between the client & server.
	 * 
	 * In this method, parameters whose names end in "A" correspond to process A, which is expected
	 * to be in a state of "next to send a packet." 
	 * Parameters whose names end in "B" correspond to process B, which is expected to have just sent
	 * a packet and is now expecting a response.
	 * Process A may be the client or the server, as with process B.
	 *  
	 *  @param processA		the ProcessType value for process A
	 *  @param socketA		the DatagramSocket that should be used to communicate with process A
	 *  @param addrA		the InetAddress object belonging to process A
	 *  @param portA		the port number used by process A
	 *  @param processB		the ProcessType value for process B
	 *  @param socketB		the DatagramSocket that should be used to communicate with process B
	 *  @param addrB		the InetAddress object belonging to process B
	 *  @param portB		the port number used by process B
	 */
	private void finishTransfer(ProcessType processA, DatagramSocket socketA, InetAddress addrA, int portA,
					ProcessType processB, DatagramSocket socketB, InetAddress addrB, int portB) { 
		
		String expectPacketStr = "DATA/ACK/ERROR";		
		
		while (true) {
			
			// start by listening for a packet from the process whose "turn" it is
			receivePacketFromProcess(socketA, processA, expectPacketStr);
						
			// send received packet to other side		
			sendPacket = new DatagramPacket(receivePacket.getData(), receivePacket.getLength(), 
							addrB, portB);
			sendPacketToProcess(socketB, processB, receivedPacketType.name());
			
			// check to see if the last packet sent will end the transfer
			if (isTerminatingErrorPacket(receivePacket)) {
				// the error packet was sent to the host and no response is expected
				return;
			} else if (isFinalDataPacket(receivePacket)) {
				
				// expect a final ACK, but could also be ERROR				
				receivePacketFromProcess(socketB, processB, "ACK/ERROR");
								
				// pass on the ACK or ERROR
				sendPacket = new DatagramPacket(receivePacket.getData(), receivePacket.getLength(), 
						addrA, portA);
				sendPacketToProcess(socketA, processA, receivedPacketType.name());

				// we're done
				return;		
			} 
			
			// last packet sent was not terminating error or final data, so do the same for process B
						
			receivePacketFromProcess(socketB, processB, expectPacketStr);
			
			// send received packet to other side		
			sendPacket = new DatagramPacket(receivePacket.getData(), receivePacket.getLength(), 
							addrA, portA);
			sendPacketToProcess(socketA, processA, receivedPacketType.name());
			
			// check to see if the last packet sent will end the transfer
			if (isTerminatingErrorPacket(receivePacket)) {
				// the error packet was sent to the host and no response is expected
				return;
			} else if (isFinalDataPacket(receivePacket)) {
				
				// expect a final ACK, but could also be ERROR				
				receivePacketFromProcess(socketA, processA, "ACK/ERROR");
								
				// pass on the ACK or ERROR
				sendPacket = new DatagramPacket(receivePacket.getData(), receivePacket.getLength(), 
						addrB, portB);
				sendPacketToProcess(socketB, processB, receivedPacketType.name());

				// we're done
				return;		
			}
			
			// still not the end of the transfer -> next loop iteration
		}
		
	}
	
	/**
	 * Returns true if a DATA packet is the last one in a transfer, i.e. less than 512 bytes
	 *  
	 *  @param packet	the packet to inspect
	 *  @return			false if not a DATA packet or length is 516 (512 + opcode length + block number) bytes
	 */
	private boolean isFinalDataPacket(DatagramPacket packet) {
		// return false if not a DATA packet 
		if (getPacketType(packet) != PacketType.DATA)
			return false;
		
		// check length including opcode and block number
		return packet.getLength() < 516;
	}
		
	/**
	 * Returns true if an ERROR packet will end a transfer once it is received.
	 *  
	 *  @param packet	the packet to inspect
	 *  @return			false if not an ERROR packet or error code is 5 (unknown TID)
	 */
	private boolean isTerminatingErrorPacket(DatagramPacket packet) {
		// return false if not an ERROR packet 
		if (getPacketType(packet) != PacketType.ERROR)
			return false;
		
		byte errCode = packet.getData()[3];
		
		// return true for any error code except unknown TID
		return errCode != PacketUtil.ERR_UNKNOWN_TID;
	}

	/**
	 * Initialize an ErrorSimulator and run it.
	 *  
	 *  @param packet	the packet to inspect
	 *  @return			false if not an ERROR packet or error code is 5 (unknown TID)
	 */
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

