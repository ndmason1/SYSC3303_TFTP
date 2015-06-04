
package tftp.sim;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.LinkedList;
import java.util.Scanner;

import tftp.net.PacketType;
import tftp.net.PacketUtil;
import tftp.net.ProcessType;
import tftp.server.Server;

/**
 * An ErrorSimulator acts as a man-in-the-middle between a TFTP server and client.
 * It behaves like the server from the perspective of the client, and behaves like
 * a client from the perspective of the server. It may run in several different modes
 * to simulate network congestion and network-related errors.
 * 
 *  Note that the Client must be run in the correct mode to communicate with the ErrorSimulator.
 */
public class ErrorSimulator {

	public static final int TIMEOUT_MS = 2000; // temporary constant to use to detect timeout

	public static final int LISTEN_PORT = 78; // not using 68 due to its usage by windows DHCP service	

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
	private SimulationType simTypeSelection;
	private IllegalOperationType illegalOpTypeSelection;
	private PacketType packetTypeSelection;
	private int blockNumSelection;

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
		System.out.println("Please select the type of simulation: \n");		
		System.out.println("(1) Illegal operation (TFTP error code 4)");
		System.out.println("(2) Unknown TID (TFTP error code 5)");
		System.out.println("(3) Lost Packet");
		System.out.println("(4) Delayed Packet");
		System.out.println("(5) Duplicate Packet");
		// maybe others
		System.out.println("(9) No error (packets will be relayed unmodified)");
		System.out.println("[ PRESS Q TO QUIT ]\n");

		switch (getMenuInput()) {
		case 1: 
			simTypeSelection = SimulationType.ILLEGAL_OP;
			showProcessMenu("selected simulation: Illegal Operation error.");
			break;

		case 2:
			simTypeSelection = SimulationType.UNKNOWN_TID;
			showProcessMenu("selected simulation: Unknown TID error.");
			break;

		case 3:
			simTypeSelection = SimulationType.PACKET_LOST;
			showProcessMenu("selected simulation: Lost Packet.");
			break;

		case 4:
			simTypeSelection = SimulationType.PACKET_DELAY;
			showProcessMenu("selected simulation: Delayed Packet.");
			break;

		case 5:
			simTypeSelection = SimulationType.PACKET_DUPLICATE;
			showProcessMenu("selected simulation: Duplicate Packet.");
			break;

		case 9:
			simTypeSelection = SimulationType.NONE;
			relayRequestWithoutErrors();
			break;
		}
	}

	/**
	 * Display a menu for the user to select which process receives a packet.
	 */
	private void showProcessMenu(String prevSelectionMessage) {

		System.out.println(prevSelectionMessage);
		if (simTypeSelection == SimulationType.ILLEGAL_OP || simTypeSelection == SimulationType.ILLEGAL_OP)
			System.out.println("Which process should receive the invalid packet? \n");
		else if (simTypeSelection == SimulationType.PACKET_LOST)
			System.out.println("Which process should expect the packet that gets lost? \n");
		else if (simTypeSelection == SimulationType.PACKET_DELAY)
			System.out.println("Which process should receive the delayed packet? \n");
		else if (simTypeSelection == SimulationType.PACKET_DUPLICATE)
			System.out.println("Which process should receive the duplicated packet? \n");

		System.out.println("(1) Client");
		System.out.println("(2) Server");
		System.out.println("[ PRESS Q TO QUIT ]\n");

		switch (getMenuInput()) {
		case 1: 
			receiverProcessSelection = ProcessType.CLIENT;
			break;
		case 2:
			receiverProcessSelection = ProcessType.SERVER;
		}

		switch (simTypeSelection) {
		case ILLEGAL_OP:
			showPacketTypeMenu();
			break;

		case UNKNOWN_TID:			
			simulateUnknownTID();
			break;

		case PACKET_DELAY:
			showPacketTypeMenu();
			break;

		case PACKET_DUPLICATE:
			showPacketTypeMenu();
			break;

		case PACKET_LOST:
			showPacketTypeMenu();
			break;

		case NONE:
			// shouldn't get here
			break;

		default:
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
		System.out.println("Please choose which packet type should be modified/delayed/lost:\n");
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

		switch (getMenuInput()) {
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

		// determine if block number must be entered
		boolean getNumber = (packetTypeSelection == PacketType.DATA || 
				packetTypeSelection == PacketType.ACK);

		switch (simTypeSelection) {
		case ILLEGAL_OP:
			showIllegalOpTypeMenu();
			break;

		case PACKET_DELAY:
			if (getNumber)
				showBlockNumberPrompt(packetTypeSelection.name() + " selected as packet to delay.");
			simulateDelayedPacket();
			break;

		case PACKET_DUPLICATE:
			if (getNumber)
				showBlockNumberPrompt(packetTypeSelection.name() + " selected as packet to duplicate.");
			//simulateDuplicateddPacket();
			break;

		case PACKET_LOST:
			if (getNumber)
				showBlockNumberPrompt(packetTypeSelection.name() + " selected as packet to drop.");
			simulateLostPacket();
			break;

		default:
			break;
		} 		
	}

	private void showBlockNumberPrompt(String prevSelectionMessage) {
		System.out.println(prevSelectionMessage);		

		System.out.println("Please enter the block number of the packet.");
		System.out.println("(note that the size of the file must be at least [blockNum] * 512 bytes)\n");

		String input = null;

		do {
			System.out.print("> ");
			input = keyboard.nextLine();
			if (input.toLowerCase().charAt(0) == 'q')
				quit();

		} while (!input.matches("\\d{1,5}")); // keep looping until 5 or less digit number entered 

		blockNumSelection = Integer.parseInt(input);		
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

		switch (getMenuInput()) {
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
	private int getMenuInput() {

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

		System.out.println("==== EXECUTING SIMULATION ====");

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
				sendPacket = ErrorSimUtil.getCorruptedPacket(receivePacket, illegalOpTypeSelection);
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
				sendPacket = ErrorSimUtil.getCorruptedPacket(receivePacket, illegalOpTypeSelection);
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

				sendPacket = ErrorSimUtil.getCorruptedPacket(receivePacket, illegalOpTypeSelection);
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

				sendPacket = ErrorSimUtil.getCorruptedPacket(errPacket, illegalOpTypeSelection);

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

			sendPacket = ErrorSimUtil.getCorruptedPacket(receivePacket, illegalOpTypeSelection);
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

			sendPacket = ErrorSimUtil.getCorruptedPacket(errPacket, illegalOpTypeSelection);

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
	 * Runs "Lost Packet" mode. 
	 * A received packet is "lost" by not forwarding it to the other process.
	 * Retransmissions are expected.
	 */
	private void simulateLostPacket() {

		// prompt for amount of packets to lose
		System.out.println("Please enter the number of times the chosen packet should get lost (1-3):\n");

		String input = null;

		do {
			System.out.print("> ");
			input = keyboard.nextLine();
			if (input.toLowerCase().charAt(0) == 'q')
				quit();

		} while (!input.matches("[1-3]")); // keep looping until number from 1 to 3 entered 

		int numDrops = Integer.parseInt(input);

		System.out.println("==== EXECUTING SIMULATION ====\n");

		byte data[] = new byte[PacketUtil.BUF_SIZE];		
		receivePacket = new DatagramPacket(data, data.length);			
		receivePacket.getLength();

		// this variable is used to keep track of whether the client sent a RRQ or WRQ to start the exchange
		// this is necessary because if packetType is DATA, ACK, or ERROR, we need to know which side is sending
		// DATA packets and which side is sending ACK packets so we know which packet to delay
		PacketType startingRequestType;

		// listen for a client packet
		receivePacketFromProcess(clientRecvSocket, ProcessType.CLIENT, "RRQ/WRQ");

		clientIP = receivePacket.getAddress();
		clientPort = receivePacket.getPort();

		// make sure starting request type actually IS a request
		startingRequestType = receivedPacketType;
		if (startingRequestType != PacketType.RRQ && startingRequestType != PacketType.WRQ) {
			System.out.println("Need to start with request packet");
			System.out.println("terminating simulation");
			return;
		}

		// set up a new socket to send server packets to the client
		DatagramSocket clientSendRecvSocket = null;
		try {
			clientSendRecvSocket = new DatagramSocket();
		} catch (SocketException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		}

		// if chosen packet type is request packet, need to make sure what was received matches
		if (packetTypeSelection == PacketType.RRQ || packetTypeSelection == PacketType.WRQ) {
			if (packetTypeSelection == PacketType.RRQ && receivedPacketType != PacketType.RRQ) {
				System.out.println("Wrong packet type received from client! (expected RRQ)");
				System.out.println("terminating simulation");
				return;				
			} else if (packetTypeSelection == PacketType.WRQ && receivedPacketType != PacketType.WRQ) { 
				System.out.println("Wrong packet type received from client! (expected WRQ)");
				System.out.println("terminating simulation");
				return;
			} else {				

				// received RRQ/WRQ, now it must be "lost"
				System.out.printf("%s packet will be dropped.\n\n", receivedPacketType.name());

				for (int i = 1; i < numDrops; i++) {					
					// "drop" the last packet received by doing nothing
					// receive retransmitted packet 
					receivePacketFromProcess(clientRecvSocket, ProcessType.CLIENT, "RRQ/WRQ");
					System.out.printf("%s packet will be dropped.\n\n", receivedPacketType.name());
				}

				if (numDrops == 3) {  
					// simulation over

					System.out.println("no further retransmissions expected from client.");

					// listen for extra retransmit anyways
					// set timeout on client receive socket  
					PacketUtil.setSocketTimeout(clientRecvSocket, 2*TIMEOUT_MS);

					// listen to detect extra retransmits
					System.out.println("\n\tListening for further retransmissions from client...");
					boolean timedOut = false;
					try {				
						receivePacketOrTimeout(clientRecvSocket, ProcessType.CLIENT, "retransmitted "+packetTypeSelection.name());
					} catch (SocketTimeoutException e) {
						timedOut = true;
					}

					if (!timedOut) {
						System.out.println("\nIncorrectly received an extra retransmission from client! [FAIL]");
					} else {
						System.out.println("\nSocket timed out, no further retransmission received [PASS]");
					}

					// reset timeout to 0  
					PacketUtil.setSocketTimeout(clientRecvSocket, 0);

					// end simulation
					printEndSimulation();
					return;
				}

				System.out.println("additional retransmission expected from client");
				System.out.println("finishing transfer...");

				// get the next request that won't be dropped
				receivePacketFromProcess(clientRecvSocket, ProcessType.CLIENT, "retransmitted "+packetTypeSelection.name());

				// send the the retransmission
				sendPacket = new DatagramPacket(receivePacket.getData(), receivePacket.getLength());
				sendPacket.setAddress(serverIP);
				sendPacket.setPort(Server.SERVER_PORT);
				sendPacketToProcess(serverSendRecvSocket, ProcessType.SERVER, 
						"retransmitted "+packetTypeSelection.name());

				// get response
				receivePacketFromProcess(serverSendRecvSocket, ProcessType.SERVER, "DATA/ACK");
				int serverTID = receivePacket.getPort();

				// pass to client
				sendPacket = new DatagramPacket(receivePacket.getData(), receivePacket.getLength());
				sendPacket.setAddress(clientIP);
				sendPacket.setPort(clientPort);					
				sendPacketToProcess(clientSendRecvSocket, ProcessType.CLIENT, "DATA/ACK");

				// finish transfer
				finishTransfer(ProcessType.CLIENT, clientSendRecvSocket, clientIP, clientPort, 
						ProcessType.SERVER, serverSendRecvSocket, serverIP, serverTID);				

				// end simulation
				printEndSimulation();
				return;
			}

		} else { // packetTypeSelection is DATA, ACK, or ERROR

			// check that the parameters make sense e.g. server cant recv delayed ACK if started with WRQ

			boolean exitSimulation = false;
			PacketType expectedRequestType = null;

			if (receiverProcessSelection == ProcessType.SERVER) {

				if (startingRequestType == PacketType.WRQ && packetTypeSelection == PacketType.ACK) {					
					expectedRequestType = PacketType.RRQ;
					exitSimulation = true;

				} else if (startingRequestType == PacketType.RRQ && packetTypeSelection == PacketType.DATA) {
					expectedRequestType = PacketType.WRQ;
					exitSimulation = true;
				}

			} else { // receiverProcessSelection == ProcessType.CLIENT

				if (startingRequestType == PacketType.WRQ && packetTypeSelection == PacketType.DATA) {
					expectedRequestType = PacketType.RRQ;
					exitSimulation = true;

				} else if (startingRequestType == PacketType.RRQ && packetTypeSelection == PacketType.ACK) {
					expectedRequestType = PacketType.WRQ;
					exitSimulation = true;
				}

			}

			if (exitSimulation) {
				System.out.printf("Wrong packet type received from client! (expected %s)\n", expectedRequestType.name());
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
			
			

			boolean done = false;
			int currentBlockNum = 1;
			int serverTID = 0;
			
			
			// send to server 
			sendPacket = new DatagramPacket(receivePacket.getData(), receivePacket.getLength(), serverIP, Server.SERVER_PORT);				
			sendPacketToProcess(serverSendRecvSocket, ProcessType.SERVER, receivedPacketType.name());
			
			while (!done) {
				
				// receive server DATA/ACK
				receivePacketFromProcess(serverSendRecvSocket, ProcessType.SERVER, "DATA/ACK");
				currentBlockNum = PacketUtil.getBlockNumber(receivePacket);
				serverTID = receivePacket.getPort();
				
				if (receiverProcessSelection == ProcessType.CLIENT) {

					if (currentBlockNum == blockNumSelection ) {

						// drop this ACK/DATA packet
						System.out.printf("%s packet will be dropped.\n\n", packetTypeSelection.name());

						for (int i = 1; i < numDrops; i++) {					
							// "drop" the last packet received by doing nothing
							// receive retransmitted packet 
							receivePacketFromProcess(serverSendRecvSocket, ProcessType.SERVER, packetTypeSelection.name());
							System.out.printf("%s packet will be dropped.\n\n", packetTypeSelection.name());
						}

						if (numDrops == 3) {  
							// simulation over

							System.out.println("no further retransmissions expected from server.");

							// listen for extra retransmit anyways
							// set timeout on client receive socket  
							PacketUtil.setSocketTimeout(serverSendRecvSocket, 2*TIMEOUT_MS);

							// listen to detect extra retransmits
							System.out.println("\n\tListening for further retransmissions from server...");
							boolean timedOut = false;
							try {				
								receivePacketOrTimeout(serverSendRecvSocket, ProcessType.SERVER, "retransmitted "+packetTypeSelection.name());
							} catch (SocketTimeoutException e) {
								timedOut = true;
							}

							if (!timedOut) {
								System.out.println("\nIncorrectly received an extra retransmission from server! [FAIL]");
							} else {
								System.out.println("\nSocket timed out, no further retransmission received [PASS]");
							}

							// reset timeout to 0  
							PacketUtil.setSocketTimeout(serverSendRecvSocket, 0);

							// end simulation
							printEndSimulation();
							return;
						}

						System.out.println("additional retransmission expected from server");
						System.out.println("finishing transfer...");

						// get the next request that won't be dropped
						receivePacketFromProcess(serverSendRecvSocket, ProcessType.SERVER, "retransmitted "+packetTypeSelection.name());

						// send the the retransmission
						sendPacket = new DatagramPacket(receivePacket.getData(), receivePacket.getLength());
						sendPacket.setAddress(clientIP);
						sendPacket.setPort(clientPort);
						sendPacketToProcess(clientSendRecvSocket, ProcessType.CLIENT, 
								"retransmitted "+packetTypeSelection.name());

						// get response
						receivePacketFromProcess(clientSendRecvSocket, ProcessType.CLIENT, "DATA/ACK");						

						// pass to server
						sendPacket = new DatagramPacket(receivePacket.getData(), receivePacket.getLength());
						sendPacket.setAddress(serverIP);
						sendPacket.setPort(serverTID);					
						sendPacketToProcess(serverSendRecvSocket, ProcessType.SERVER, "DATA/ACK");

						// finish transfer
						finishTransfer(ProcessType.SERVER, serverSendRecvSocket, serverIP, serverTID,
								ProcessType.CLIENT, clientSendRecvSocket, clientIP, clientPort);				

						// end simulation
						printEndSimulation();
						return;	

					}
				} 
				
				// send to client
				sendPacket = new DatagramPacket(receivePacket.getData(), receivePacket.getLength(), clientIP, clientPort);				
				sendPacketToProcess(clientSendRecvSocket, ProcessType.CLIENT, receivedPacketType.name());
				
				// get client response				
				receivePacketFromProcess(clientSendRecvSocket, ProcessType.CLIENT, "DATA/ACK");
				currentBlockNum = PacketUtil.getBlockNumber(receivePacket);
				
				// check if the packet should be dropped
				if (receiverProcessSelection == ProcessType.SERVER) {
					if (currentBlockNum == blockNumSelection ) {

						// drop this client packet
						System.out.printf("%s packet will be dropped.\n\n",  packetTypeSelection.name());

						for (int i = 1; i < numDrops; i++) {					
							// "drop" the last packet received by doing nothing
							// receive retransmitted packet 
							receivePacketFromProcess(clientSendRecvSocket, ProcessType.CLIENT, packetTypeSelection.name());
							System.out.printf("%s packet will be dropped.\n\n",  packetTypeSelection.name());
						}

						if (numDrops == 3) {  
							// simulation over

							System.out.println("no further retransmissions expected from client.");

							// listen for extra retransmit anyways
							// set timeout on client receive socket  
							PacketUtil.setSocketTimeout(clientSendRecvSocket, 2*TIMEOUT_MS);

							// listen to detect extra retransmits
							System.out.println("\n\tListening for further retransmissions from client...");
							boolean timedOut = false;
							try {				
								receivePacketOrTimeout(clientSendRecvSocket, ProcessType.CLIENT, "retransmitted "+packetTypeSelection.name());
							} catch (SocketTimeoutException e) {
								timedOut = true;
							}

							if (!timedOut) {
								System.out.println("\nIncorrectly received an extra retransmission from client! [FAIL]");
							} else {
								System.out.println("\nSocket timed out, no further retransmission received [PASS]");
							}

							// reset timeout to 0  
							PacketUtil.setSocketTimeout(clientSendRecvSocket, 0);

							// end simulation
							printEndSimulation();
							return;
						}

						System.out.println("additional retransmission expected from client");
						System.out.println("finishing transfer...");

						// get the next request that won't be dropped
						receivePacketFromProcess(clientSendRecvSocket, ProcessType.CLIENT, "retransmitted "+packetTypeSelection.name());

						// send the the retransmission
						sendPacket = new DatagramPacket(receivePacket.getData(), receivePacket.getLength());
						sendPacket.setAddress(serverIP);
						sendPacket.setPort(serverTID);
						sendPacketToProcess(serverSendRecvSocket, ProcessType.SERVER, 
								"retransmitted "+packetTypeSelection.name());

						// get response
						receivePacketFromProcess(serverSendRecvSocket, ProcessType.SERVER, "DATA/ACK");

						// pass to client
						sendPacket = new DatagramPacket(receivePacket.getData(), receivePacket.getLength());
						sendPacket.setAddress(clientIP);
						sendPacket.setPort(clientPort);					
						sendPacketToProcess(clientSendRecvSocket, ProcessType.CLIENT, "DATA/ACK");

						// finish transfer
						finishTransfer(ProcessType.CLIENT, clientSendRecvSocket, clientIP, clientPort, 
								ProcessType.SERVER, serverSendRecvSocket, serverIP, serverTID);				

						// end simulation
						printEndSimulation();
						return;

					}
				}
				
				// send to server and end this iteration
				sendPacket = new DatagramPacket(receivePacket.getData(), receivePacket.getLength(), serverIP, serverTID);				
				sendPacketToProcess(serverSendRecvSocket, ProcessType.SERVER, receivedPacketType.name());

			}
		}


	}

	/**
	 * Runs "Delayed Packet" mode. 
	 * A packet is received is "delayed" by waiting a specified amount of time 
	 * before sending it to the other process.
	 */
	private void simulateDelayedPacket() {

		// prompt for time in ms to delay
		System.out.println("Please enter the amount of time (in ms) to delay the packet: \n");

		String input = null;

		do {
			System.out.print("> ");
			input = keyboard.nextLine();
			if (input.toLowerCase().charAt(0) == 'q')
				quit();

		} while (!input.matches("\\d{1,6}")); // keep looping until 6 or less digit number entered 

		int delayInMs = Integer.parseInt(input);

		// get number of retransmits expected from the process who sends delayed packet
		int expectedRetransmits = (int) Math.min( Math.floor(delayInMs / TIMEOUT_MS), 2.0 );

		System.out.printf("Delay selected: %dms. (%d retransmission(s) expected)\n\n", delayInMs, expectedRetransmits);		
		System.out.println("==== EXECUTING SIMULATION ====\n");

		byte data[] = new byte[PacketUtil.BUF_SIZE];		
		receivePacket = new DatagramPacket(data, data.length);			
		receivePacket.getLength();

		// this variable is used to keep track of whether the client sent a RRQ or WRQ to start the exchange
		// this is necessary because if packetType is DATA, ACK, or ERROR, we need to know which side is sending
		// DATA packets and which side is sending ACK packets so we know which packet to delay
		PacketType startingRequestType;

		// listen for a client packet
		receivePacketFromProcess(clientRecvSocket, ProcessType.CLIENT, "RRQ/WRQ");

		clientIP = receivePacket.getAddress();
		clientPort = receivePacket.getPort();

		// make sure starting request type actually IS a request
		startingRequestType = receivedPacketType;
		if (startingRequestType != PacketType.RRQ && startingRequestType != PacketType.WRQ) {
			System.out.println("Need to start with request packet");
			System.out.println("terminating simulation");
			return;
		}

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

				// received RRQ, now it must be delayed

				System.out.printf("Waiting %dms before sending next packet...\n", delayInMs);
				try {
					Thread.sleep(delayInMs);
				} catch (InterruptedException e) {
					System.out.println("Interrupted while simulating delayed packet!");
					System.out.println("Terminating simulation");
					// TODO finish transfer
					return;
				}

				// send the packet after the delay
				sendPacket = new DatagramPacket(receivePacket.getData(), receivePacket.getLength());
				sendPacket.setAddress(serverIP);
				sendPacket.setPort(Server.SERVER_PORT);
				sendPacketToProcess(serverSendRecvSocket, ProcessType.SERVER, "delayed RRQ");

				// receive response
				receivePacketFromProcess(serverSendRecvSocket, ProcessType.SERVER, "DATA");
				int serverTID = receivePacket.getPort();
				System.out.printf("server TID for request is %d\n", serverTID);

				// send response to client
				sendPacket = new DatagramPacket(receivePacket.getData(), receivePacket.getLength());
				sendPacket.setAddress(clientIP);
				sendPacket.setPort(clientPort);
				sendPacketToProcess(clientSendRecvSocket, ProcessType.CLIENT, receivedPacketType.name());

				// for each retransmit expected...
				for (int i = 0; i < expectedRetransmits; i++) {
					System.out.printf("\n\t retransmission %d\n", i+1);
					// listen for client retransmit
					receivePacketFromProcess(clientRecvSocket, ProcessType.CLIENT, String.format("RRQ (retransmit %d)", i+1));

					// forward to server
					sendPacket = new DatagramPacket(receivePacket.getData(), receivePacket.getLength());
					sendPacket.setAddress(serverIP);
					sendPacket.setPort(Server.SERVER_PORT);
					sendPacketToProcess(serverSendRecvSocket, ProcessType.SERVER, String.format("RRQ (retransmit %d)", i+1));

					// receive response
					receivePacketFromProcess(serverSendRecvSocket, ProcessType.SERVER, "DATA");
					int otherServerTID = receivePacket.getPort();
					System.out.printf("server TID for retransmitted (%d) request is %d\n", i+1, otherServerTID);

					// create a new socket to simulate new server TID
					DatagramSocket secondClientSendRecvSocket = null;
					try {
						secondClientSendRecvSocket = new DatagramSocket();
					} catch (SocketException e1) {
						e1.printStackTrace();
					}		

					// send second response to client
					sendPacket = new DatagramPacket(receivePacket.getData(), receivePacket.getLength());
					sendPacket.setAddress(clientIP);
					sendPacket.setPort(clientPort);
					sendPacketToProcess(secondClientSendRecvSocket, ProcessType.CLIENT, "DATA");

					// listen for client ERROR
					receivePacketFromProcess(secondClientSendRecvSocket, ProcessType.CLIENT, "ERROR (5)");

					// check if client sent unknown TID error
					printSimulationResult(ProcessType.CLIENT, PacketUtil.ERR_UNKNOWN_TID);

					// send error 5 to server (new packet in case the client didn't actually send ERROR 5)
					PacketUtil packetUtil = new PacketUtil(serverIP, otherServerTID);
					sendPacket = packetUtil.formErrorPacket(PacketUtil.ERR_UNKNOWN_TID, 
							"received packet from unrecognized source port");	

					sendPacketToProcess(serverSendRecvSocket, ProcessType.SERVER, "ERROR");

				}


				System.out.println("\n\tFinishing file transfer...");
				// get response from original client sendRecv socket
				receivePacketFromProcess(clientSendRecvSocket, ProcessType.CLIENT, "ACK");				

				// forward to server
				sendPacket = new DatagramPacket(receivePacket.getData(), receivePacket.getLength());
				sendPacket.setAddress(serverIP);
				sendPacket.setPort(serverTID);
				sendPacketToProcess(serverSendRecvSocket, ProcessType.SERVER, receivedPacketType.name());

				// finish the transfer so client goes back to ready state
				finishTransfer(ProcessType.SERVER, serverSendRecvSocket, serverIP, serverTID,
						ProcessType.CLIENT, clientSendRecvSocket, clientIP, clientPort);

				// set timeout on client receive socket  
				try {
					clientRecvSocket.setSoTimeout(2*TIMEOUT_MS);
				} catch (SocketException e) {
					e.printStackTrace();
				}

				// listen to detect extra retransmits
				System.out.println("Listening for further retransmissions from client...");
				boolean timedOut = false;
				try {				
					receivePacketOrTimeout(clientRecvSocket, ProcessType.CLIENT, "retransmitted");
				} catch (SocketTimeoutException e) {
					timedOut = true;
				}

				if (!timedOut) {
					System.out.println("\nIncorrectly received an extra retransmission from client! [FAIL]");
				} else {
					System.out.println("\nSocket timed out, no further retransmission received [PASS]");
				}

				// reset timeout to 0  
				try {
					clientRecvSocket.setSoTimeout(0);
				} catch (SocketException e) {
					e.printStackTrace();
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

				// received WRQ, now it must be delayed

				try {
					Thread.sleep(delayInMs);
				} catch (InterruptedException e) {
					System.out.println("Interrupted while simulating delayed packet!");
					System.out.println("Terminating simulation");
					// TODO finish transfer
					return;
				}

				// send the packet after the delay
				sendPacket = new DatagramPacket(receivePacket.getData(), receivePacket.getLength());
				sendPacket.setAddress(serverIP);
				sendPacket.setPort(Server.SERVER_PORT);
				sendPacketToProcess(serverSendRecvSocket, ProcessType.SERVER, "delayed WRQ");

				// receive response
				receivePacketFromProcess(serverSendRecvSocket, ProcessType.SERVER, "ACK");
				int serverTIDA = receivePacket.getPort();

				// listen for client retransmit 
				// NOTE THAT THIS IS ONLY CORRECT TO DO IF THE GIVEN DELAY IS GREATER THAN THE TIMEOUT THRESHOLD
				// (could use a constant for timeout time used by the client here...)
				receivePacketFromProcess(clientRecvSocket, ProcessType.CLIENT, "retransmitted WRQ");

				// send the retransmitted request to server after the delay
				sendPacket = new DatagramPacket(receivePacket.getData(), receivePacket.getLength());
				sendPacket.setAddress(serverIP);
				sendPacket.setPort(Server.SERVER_PORT);
				sendPacketToProcess(serverSendRecvSocket, ProcessType.SERVER, "retransmitted WRQ");

				// receive retransmitted response from a different server port
				receivePacketFromProcess(serverSendRecvSocket, ProcessType.SERVER, "retransmitted ACK");
				int serverTIDB = receivePacket.getPort();

				// send first response to client
				sendPacket = new DatagramPacket(receivePacket.getData(), receivePacket.getLength());
				sendPacket.setAddress(clientIP);
				sendPacket.setPort(clientPort);
				sendPacketToProcess(clientSendRecvSocket, ProcessType.CLIENT, "original ACK");

				// create a new socket to simulate new server TID
				DatagramSocket secondClientSendRecvSocket = null;
				try {
					secondClientSendRecvSocket = new DatagramSocket();
				} catch (SocketException e1) {
					// TODO Auto-generated catch block
					e1.printStackTrace();
				}		

				// send second response to client
				sendPacket = new DatagramPacket(receivePacket.getData(), receivePacket.getLength());
				sendPacket.setAddress(clientIP);
				sendPacket.setPort(clientPort);
				sendPacketToProcess(secondClientSendRecvSocket, ProcessType.CLIENT, "retransmitted ACK");

				// listen for first client DATA
				receivePacketFromProcess(clientRecvSocket, ProcessType.CLIENT, "retransmitted WRQ");

				// listen for unknown TID error
				receivePacketFromProcess(secondClientSendRecvSocket, ProcessType.CLIENT, "ERROR (5)");

				// check if client sent unknown TID error
				printSimulationResult(ProcessType.CLIENT, PacketUtil.ERR_UNKNOWN_TID);

				// send DATA to server and finish the transfer so client goes back to ready state
				sendPacketToProcess(serverSendRecvSocket, ProcessType.SERVER, "DATA");
				finishTransfer(ProcessType.CLIENT, clientSendRecvSocket, clientIP, clientPort, 
						ProcessType.SERVER, serverSendRecvSocket, serverIP, serverTIDA);


				// end simulation
				printEndSimulation();
				return;

			}
		} else { // packetTypeSelection is DATA, ACK, or ERROR

			// check that the parameters make sense e.g. server cant recv delayed ACK if started with WRQ

			boolean exitSimulation = false;
			PacketType expectedRequestType = null;

			if (receiverProcessSelection == ProcessType.SERVER) {

				if (startingRequestType == PacketType.WRQ && packetTypeSelection == PacketType.ACK) {


					expectedRequestType = PacketType.RRQ;
					exitSimulation = true;

				} else if (startingRequestType == PacketType.RRQ && packetTypeSelection == PacketType.DATA) {

					expectedRequestType = PacketType.WRQ;
					exitSimulation = true;
				}

			} else { // receiverProcessSelection == ProcessType.CLIENT

				if (startingRequestType == PacketType.WRQ && packetTypeSelection == PacketType.DATA) {

					expectedRequestType = PacketType.RRQ;
					exitSimulation = true;

				} else if (startingRequestType == PacketType.RRQ && packetTypeSelection == PacketType.ACK) {

					expectedRequestType = PacketType.WRQ;
					exitSimulation = true;
				}

			}

			if (exitSimulation) {
				System.out.printf("Wrong packet type received from client! (expected %s)\n", expectedRequestType.name());
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


			System.out.println("DATA/ACK/ERROR not yet implemented, stay tuned");
		}


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
	 *  @throws SocketTimeoutException 	if a timeout was set and has expired
	 */
	private void receivePacketFromProcess(DatagramSocket recvSocket, ProcessType sendProcess, String expectedPacketStr) {

		// listen for a packet from given source process
		// note this function doesn't actually enforce the packet type received, since it might not always matter
		System.out.printf("listening on port %s for %s packet from %s ... ", recvSocket.getLocalPort(), 
				expectedPacketStr, sendProcess);
		try {
			recvSocket.receive(receivePacket);
		} catch (IOException e) {
			System.out.printf("IOException caught receiving %s packet: %s", sendProcess, e.getMessage());
			System.out.println("cannot proceed, terminating simulation");
			System.exit(1);
		}	

		receivedPacketType = PacketUtil.getPacketType(receivePacket);		
		String label = receivedPacketType.name();

		// if DATA or ACK packet, display block number		
		if (receivedPacketType == PacketType.DATA || receivedPacketType == PacketType.ACK)
			label += " " + PacketUtil.getBlockNumber(receivePacket);
		else if (receivedPacketType == PacketType.ERROR)
			label += " " + PacketUtil.getErrorCode(receivePacket);

		System.out.printf("received %s packet ", label);
		PacketUtil.printOpcode(receivePacket);
	}

	/**
	 * Listens for a packet from the given process and displays information.
	 * The socket may timeout, in which case a SocketTimeoutException is thrown.
	 * Sets receivedPacket and receivedPacketType to the packet that was 
	 * received and its type, respectively.
	 * 
	 *  @param recvSocket			the DatagramSocket to listen on
	 *  @param sendProcess			the process (client or server) expected to send a packet
	 *  @param expectedPacketStr	a string describing the expected type of packet to receive, which is displayed
	 *  @throws SocketTimeoutException 	if a timeout was set and has expired
	 */
	private void receivePacketOrTimeout(DatagramSocket recvSocket, ProcessType sendProcess, String expectedPacketStr) 
			throws SocketTimeoutException {

		// listen for a packet from given source process
		// note this function doesn't actually enforce the packet type received, since it might not always matter
		System.out.printf("listening on port %s for %s packet from %s ... ", recvSocket.getLocalPort(), 
				expectedPacketStr, sendProcess);
		try {
			recvSocket.receive(receivePacket);
		} catch (SocketTimeoutException e) {
			throw e;
		} catch (IOException e) {
			System.out.printf("IOException caught receiving %s packet: %s", sendProcess, e.getMessage());
			System.out.println("cannot proceed, terminating simulation");
			System.exit(1);
		}	

		receivedPacketType = PacketUtil.getPacketType(receivePacket);		
		String label = receivedPacketType.name();

		// if DATA or ACK packet, display block number		
		if (receivedPacketType == PacketType.DATA || receivedPacketType == PacketType.ACK)
			label += " " + PacketUtil.getBlockNumber(receivePacket);
		else if (receivedPacketType == PacketType.ERROR)
			label += " " + PacketUtil.getErrorCode(receivePacket);

		System.out.printf("received %s packet ", label);
		PacketUtil.printOpcode(receivePacket);
	}

	/**
	 * Sends a packet to the given process and displays information.
	 * 	 * 
	 *  @param sendSocket		the DatagramSocket to use for sending
	 *  @param recvProcess		the process (client or server) who should be listening for the packet
	 *  @param sendPacketStr	a string describing the packet being sent, which is displayed
	 */
	private void sendPacketToProcess(DatagramSocket sendSocket, ProcessType recvProcess, String sendPacketStr) {		


		System.out.printf("sending %s packet to %s (IP: %s, port %d) ... ", 
				sendPacketStr, recvProcess, sendPacket.getAddress(), sendPacket.getPort());		

		try {
			sendSocket.send(sendPacket);
		} catch (IOException e) {
			System.out.printf("IOException caught sending %s packet: %s", recvProcess, e.getMessage());
			System.out.println("cannot proceed, terminating simulation");
			return;
		}	

		PacketType sendType = PacketUtil.getPacketType(sendPacket);		
		String label = sendType.name();
		// if DATA or ACK packet, display block number		
		if (sendType == PacketType.DATA || sendType == PacketType.ACK)
			label += " " + PacketUtil.getBlockNumber(sendPacket);
		else if (sendType == PacketType.ERROR)
			label += " " + PacketUtil.getErrorCode(receivePacket);

		System.out.printf("sent %s packet ", label);
		PacketUtil.printOpcode(sendPacket);
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
				String msg = PacketUtil.getErrMessage(receivePacket.getData());
				System.out.printf("%s responded with ERROR code %d as expected! [PASS]\n", srcProcName, expectedErrCode);
				System.out.printf("error message from packet: \"%s\"\n", msg);
			} else {
				String msg = PacketUtil.getErrMessage(receivePacket.getData());
				System.out.printf("%s responded with ERROR code %d (not %d as expected) [FAIL]\n", 
						srcProcName, receivePacket.getData()[3], expectedErrCode);
				System.out.printf("error message from packet: \"%s\"\n", msg);
			}

		} else { // not an error packet
			System.out.printf("%s response was not an ERROR packet as expected [FAIL]\n", srcProcName); 
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
		if (PacketUtil.getPacketType(packet) != PacketType.DATA)
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
		if (PacketUtil.getPacketType(packet) != PacketType.ERROR)
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

