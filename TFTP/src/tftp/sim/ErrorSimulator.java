
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
		keyboard = new Scanner(System.in);
		System.out.println("Please Enter a valid Server IP address:");
		String IPaddress = keyboard.nextLine();

		try {
			serverIP = InetAddress.getByName(IPaddress);
		} catch (UnknownHostException e) {
			// TODO Auto-generated catch block
			System.out.println("Can not set IP address, terminated");
			return;
		}





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
		if (simTypeSelection == SimulationType.ILLEGAL_OP || simTypeSelection == SimulationType.UNKNOWN_TID)
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
		
		String message = null;

		switch (getMenuInput()) {
		case 1: 
			receiverProcessSelection = ProcessType.CLIENT;
			message = "Client selected.";
			break;
		case 2:
			receiverProcessSelection = ProcessType.SERVER;
			message = "Server selected.";
		}

		switch (simTypeSelection) {
		case ILLEGAL_OP:
			showPacketTypeMenu(message);
			break;

		case UNKNOWN_TID:
			showPacketTypeMenu(message);
			break;

		case PACKET_DELAY:
			showPacketTypeMenu(message);
			break;

		case PACKET_DUPLICATE:
			showDuplicateMenu(message);
			break;

		case PACKET_LOST:
			showPacketTypeMenu(message);
			break;

		case NONE:
			// shouldn't get here
			break;

		default:
			break;
		}
	} 

	private void showDuplicateMenu(String message){
		System.out.println(message);
		
		if(receiverProcessSelection == ProcessType.SERVER){
			System.out.println("\n(1) WRQ");
			packetTypeSelection = PacketType.WRQ;
		}else{
			System.out.println("\n(1) RRQ");
			packetTypeSelection = PacketType.RRQ;
		}
		
		int selection = getMenuInput();
		while (selection != 1){
			selection = getMenuInput();
		}
		
		showBlockNumberPrompt(packetTypeSelection.name() + " selected as packet to duplicate.");
		simulateDuplicatePacket();
		
	}
	/**
	 * Displays a menu for the user to select a type of packet that should be modified.
	 * Currently only used for illegal operation mode.
	 * 
	 * @param message	string containing previous selection
	 */
	private void showPacketTypeMenu(String message) {
		
		String verb = null;
		switch (simTypeSelection) {
		case ILLEGAL_OP:
			verb = "modified";
			break;
			
		case NONE:
			return;
			
		case PACKET_DELAY:
			verb = "delayed";
			break;
			
		case PACKET_DUPLICATE:
			verb = "duplicated";
			break;
			
		case PACKET_LOST:
			verb = "lost";
			break;
			
		case UNKNOWN_TID:
			verb = "sent from a different TID";
			break;
			
		default:
			return;		
		}
		
		System.out.println(message);
		
		// only show request packets if it makes sense for the simulation mode and the process chosen
		boolean showRequestTypes = simTypeSelection != SimulationType.UNKNOWN_TID 
				&& receiverProcessSelection == ProcessType.SERVER;
		
		 
		
		System.out.printf("Please choose which packet type should be %s:\n\n", verb);
		if (showRequestTypes) {
			System.out.println("(1) RRQ");
			System.out.println("(2) WRQ");			
			System.out.println("(3) DATA (must start with WRQ)");
			System.out.println("(4) ACK (must start with RRQ)");
			System.out.println("(5) ERROR");
		} else {
			if (receiverProcessSelection == ProcessType.SERVER) {
				System.out.println("(1) DATA (must start with WRQ)");
				System.out.println("(2) ACK (must start with RRQ)");
			} else {
				System.out.println("(1) DATA (must start with RRQ)");
				System.out.println("(2) ACK (must start with WRQ)");
			}
			System.out.println("(3) ERROR");
		}

		System.out.println("[ PRESS Q TO QUIT ]\n");

		switch (getMenuInput()) {
		case 1:
			packetTypeSelection = showRequestTypes ? PacketType.RRQ : PacketType.DATA;
			break;

		case 2:			
			packetTypeSelection = showRequestTypes ? PacketType.WRQ : PacketType.ACK;
			break;

		case 3:
			packetTypeSelection = showRequestTypes ? PacketType.DATA : PacketType.ERROR;
			break;

		case 4:
			if (!showRequestTypes) {
				System.out.println("Not a valid option! Returning to main menu");
				return;
			}				
			packetTypeSelection = PacketType.ACK;
			break;

		case 5:
			if (!showRequestTypes) {
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
			if (getNumber)
				showBlockNumberPrompt(packetTypeSelection.name() + " selected as packet to modify.");
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
			simulateDuplicatePacket();
			break;

		case PACKET_LOST:
			if (getNumber)
				showBlockNumberPrompt(packetTypeSelection.name() + " selected as packet to drop.");
			simulateLostPacket();
			break;
			
		case UNKNOWN_TID:
			if (getNumber)
				showBlockNumberPrompt(packetTypeSelection.name() + " selected as packet to send from different TID.");
			simulateUnknownTID();
			break;
			
		default:
			break;

		} 		
	}

	private void showBlockNumberPrompt(String prevSelectionMessage) {
		System.out.println(prevSelectionMessage);		

		System.out.println("Please enter the block number of the packet.");
		if (simTypeSelection == SimulationType.UNKNOWN_TID) {
			System.out.println("To establish server TID to client, block number must be:");
			System.out.println(" - at least 2 if DATA chosen\n - at least 1 is ACK chosen");
		}
		
		System.out.println("(note that the size of the file must be at least [blockNum] * 512 bytes)\n");
		

		String input = null;

		do {
			System.out.print("> ");
			input = keyboard.nextLine();
			if (input.toLowerCase().charAt(0) == 'q')
				quit();

		} while (!input.matches("\\d{1,5}")); // keep looping until 5 or less digit number entered 

		blockNumSelection = Integer.parseInt(input);
		
		// enforce valid block number based on simulation and packet type
		int min = -1;
		if (packetTypeSelection == PacketType.DATA) {
			min = simTypeSelection == SimulationType.UNKNOWN_TID ? 2 : 1;			
		} else {
			min = simTypeSelection == SimulationType.UNKNOWN_TID ? 1 : 0;
		}
		
		if (blockNumSelection < min) blockNumSelection = min;
			
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
	 * A packet is sent from a different socket so as to trigger an "Unknown TID" (transfer ID) error.
	 */
	private void simulateUnknownTID() {
		
		System.out.println("\nChosen parameters for unknown TID simulation: ");
		System.out.println("Process to receive packet from unknown TID: " + receiverProcessSelection.name());
		System.out.println("Packet type to send from unknown TID: " + packetTypeSelection.name());
		if (packetTypeSelection == PacketType.DATA || packetTypeSelection == PacketType.ACK)
			System.out.println("block number of packet chosen packet: " + blockNumSelection);

		System.out.println("\n==== EXECUTING SIMULATION ====\n");

		byte data[] = new byte[PacketUtil.BUF_SIZE];
		receivePacket = new DatagramPacket(data, data.length);

		PacketType startingRequestType;

		// listen for a client packet
		receivePacketFromProcess(clientRecvSocket, ProcessType.CLIENT, "RRQ/WRQ");
		clientIP = receivePacket.getAddress();
		clientPort = receivePacket.getPort();
		
		// create socket to send DATA/ACK to client
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
			clientSendRecvSocket.close();
			return;
		}
		
		PacketType serverSendType = startingRequestType == PacketType.RRQ ? 
				PacketType.DATA : PacketType.ACK ;
		PacketType clientSendType = startingRequestType == PacketType.RRQ ? 
				PacketType.ACK : PacketType.DATA ;

		// pass request to server (establish client TID to the server)
		sendPacket = new DatagramPacket(receivePacket.getData(), receivePacket.getLength(), 
				serverIP, Server.SERVER_PORT);
		sendPacketToProcess(serverSendRecvSocket, ProcessType.SERVER, receivedPacketType.name());

		// receive server response
		receivePacketFromProcess(serverSendRecvSocket, ProcessType.SERVER, serverSendType.name());
		
		// save the server's TID for this simulation
		int serverTID = receivePacket.getPort();

		// pass server response to client, establish server TID to the client		
		sendPacket = new DatagramPacket(receivePacket.getData(), receivePacket.getLength(), clientIP, clientPort);
		sendPacketToProcess(clientSendRecvSocket, ProcessType.CLIENT, receivedPacketType.name());
		
		// continue with transfer		
		boolean lastData = false;
		boolean lastAck = false;
		int currentBlockNum = 1;		

		while (!lastAck) {

			// receive client DATA/ACK
			receivePacketFromProcess(clientSendRecvSocket, ProcessType.CLIENT, clientSendType.name());			
			currentBlockNum = PacketUtil.getBlockNumber(receivePacket);						

			// check if the packet should be dropped
			if (receiverProcessSelection == ProcessType.SERVER) {
				if (currentBlockNum == blockNumSelection ) {
					System.out.println("\n\tSending next packet from unknown TID");
					
					// send the packet from a new socket (new TID)
					DatagramSocket unknownTIDSocket = null;
					try {						
						unknownTIDSocket = new DatagramSocket();
					} catch (SocketException e1) {
						e1.printStackTrace();
					}

					// save packet details so it can be resent
					byte[] originalPacketData = new byte[PacketUtil.BUF_SIZE];
					int originalLength = receivePacket.getLength();
					System.arraycopy(receivePacket.getData(), 0, originalPacketData, 0, originalLength);
					
					// send to server from unknown TID		
					sendPacket = new DatagramPacket(receivePacket.getData(), receivePacket.getLength(), serverIP, serverTID);				
					sendPacketToProcess(unknownTIDSocket, ProcessType.SERVER, receivedPacketType.name());
					
					// receive server response (should be an ERROR with code 5)
					receivePacketFromProcess(unknownTIDSocket, ProcessType.SERVER, "ERROR");

					// check result and display
					printSimulationResult(ProcessType.SERVER, PacketUtil.ERR_UNKNOWN_TID);
					
					// send out the original server socket to finish transfer
					System.out.println("\n\tFinishing transfer");
					
					sendPacket = new DatagramPacket(originalPacketData, originalLength, serverIP, serverTID);				
					sendPacketToProcess(serverSendRecvSocket, ProcessType.SERVER, PacketUtil.getPacketType(sendPacket).name());
					
					finishTransfer(ProcessType.SERVER, serverSendRecvSocket, serverIP, serverTID,
							ProcessType.CLIENT, clientSendRecvSocket, clientIP, clientPort);
										
					// end simulation
					printEndSimulation();
					clientSendRecvSocket.close();
					return;
				}
			}

			// send to server 
			sendPacket = new DatagramPacket(receivePacket.getData(), receivePacket.getLength(), serverIP, serverTID);				
			sendPacketToProcess(serverSendRecvSocket, ProcessType.SERVER, receivedPacketType.name());
			
			// check if we are done (no more blocks to send)
			if (lastAck) {
				System.out.println("Final ACK packet was sent!");
				System.out.println("File was too small to send selected block number from unknown TID. [FAIL]");
				System.out.println("Terminating simulation.");
				printEndSimulation();
				clientSendRecvSocket.close();
				return;
			}

			// get server response
			receivePacketFromProcess(serverSendRecvSocket, ProcessType.SERVER, serverSendType.name());
			currentBlockNum = PacketUtil.getBlockNumber(receivePacket);
			
			if (isFinalDataPacket(receivePacket)) {
				// this packet is the final data packet
				lastData = true;
			} else if (lastData) {
				// this packet is the final ack packet
				lastAck = true;
			}

			// check if the packet should be dropped
			if (receiverProcessSelection == ProcessType.CLIENT) {
				if (currentBlockNum == blockNumSelection ) {
					System.out.println("\n\tSending next packet from unknown TID");

					// send the packet from a new socket (new TID)
					DatagramSocket unknownTIDSocket = null;
					try {						
						unknownTIDSocket = new DatagramSocket();
					} catch (SocketException e1) {
						e1.printStackTrace();
					}

					// save packet details so it can be resent
					byte[] originalPacketData = new byte[PacketUtil.BUF_SIZE];
					int originalLength = receivePacket.getLength();
					System.arraycopy(receivePacket.getData(), 0, originalPacketData, 0, originalLength);

					// send to client from unknown TID					
					sendPacket = new DatagramPacket(receivePacket.getData(), receivePacket.getLength(), clientIP, clientPort);				
					sendPacketToProcess(unknownTIDSocket, ProcessType.CLIENT, receivedPacketType.name());

					// receive server response (should be an ERROR with code 5)
					receivePacketFromProcess(unknownTIDSocket, ProcessType.CLIENT, "ERROR");

					// check result and display
					printSimulationResult(ProcessType.CLIENT, PacketUtil.ERR_UNKNOWN_TID);
					
					// send ERROR packet to server from unknown TID					
					sendPacket = new DatagramPacket(receivePacket.getData(), receivePacket.getLength(), clientIP, clientPort);				
					sendPacketToProcess(unknownTIDSocket, ProcessType.CLIENT, receivedPacketType.name());

					// send out the original server socket to finish transfer
					System.out.println("\n\tFinishing transfer");

					sendPacket = new DatagramPacket(originalPacketData, originalLength, clientIP, clientPort);				
					sendPacketToProcess(clientSendRecvSocket, ProcessType.CLIENT, PacketUtil.getPacketType(sendPacket).name());

					finishTransfer(ProcessType.CLIENT, clientSendRecvSocket, clientIP, clientPort,
							ProcessType.SERVER, serverSendRecvSocket, serverIP, serverTID);

					// end simulation
					printEndSimulation();
					clientSendRecvSocket.close();
					return;
				}
			}			

			// send to client and end this loop iteration
			sendPacket = new DatagramPacket(receivePacket.getData(), receivePacket.getLength(), clientIP, clientPort);				
			sendPacketToProcess(clientSendRecvSocket, ProcessType.CLIENT, receivedPacketType.name());
			
			// check if we are done (no more blocks to send)
			if (lastAck) {
				System.out.println("Final ACK packet was sent!");
				System.out.println("File was too small to send selected block number from unknown TID. [FAIL]");
				System.out.println("Terminating simulation.");
				printEndSimulation();
				clientSendRecvSocket.close();
				return;
			}
		}
	}

	/**
	 * Runs "Illegal Operation" mode. 
	 * A packet's contents are modified so as to trigger an Illegal Operation error.
	 * The packet selected to trigger the error is determined based on the user's
	 * selection of packet type to modify and the process who is reciveing the 
	 * modified packet. 
	 */
	private void simulateIllegalOperation() {
		
		System.out.println("\nChosen parameters for illegal operation simulation: ");
		System.out.println("Process to receive illegal packet: " + receiverProcessSelection.name());
		System.out.println("Packet type to modify: " + packetTypeSelection.name());
		if (packetTypeSelection == PacketType.DATA || packetTypeSelection == PacketType.ACK)
			System.out.println("block number of packet chosen packet: " + blockNumSelection);
		System.out.println("Packet field/property to modify: " + illegalOpTypeSelection.name());
		System.out.println("\n");

		System.out.println("==== EXECUTING SIMULATION ====");

		byte data[] = new byte[PacketUtil.BUF_SIZE];		
		receivePacket = new DatagramPacket(data, data.length);

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
	 * A received packet is dropped by not forwarding it to the other process.
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
		
		System.out.println("\nChosen parameters for lost packet simulation: ");
		System.out.println("Process expecting packet that will be dropped: " + receiverProcessSelection.name());
		System.out.println("Packet type to drop: " + packetTypeSelection.name());
		if (packetTypeSelection == PacketType.DATA || packetTypeSelection == PacketType.ACK)
			System.out.println("block number of chosen packet: " + blockNumSelection);
		System.out.println("Number of times packet will be dropped: " + numDrops);
		System.out.println("\n");
	
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
	
				// received RRQ/WRQ, now it must be dropped
				System.out.printf("%s packet will be dropped.\n\n", receivedPacketType.name());
	
				for (int i = 1; i < numDrops; i++) {					
					// "drop" the last packet received by doing nothing
					// receive retransmitted packet 
					receivePacketFromProcess(clientRecvSocket, ProcessType.CLIENT, packetTypeSelection.name());
					System.out.printf("%s packet will be dropped.\n\n", receivedPacketType.name());
				}
	
				if (numDrops == 3) {  
					// simulation over
	
					System.out.println("no further retransmissions expected from client.");
	
					// listen for extra retransmit anyways
					// set timeout on client receive socket  
					PacketUtil.setSocketTimeout(clientRecvSocket, 2*TIMEOUT_MS);
	
					// listen to detect extra retransmits
					System.out.println("\n\tListening for further retransmissions from client");
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
					clientSendRecvSocket.close();
					return;
				}
	
				System.out.println("\tadditional retransmission expected from client");
	
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
				clientSendRecvSocket.close();
				return;
			}
	
		}
	
		// packetTypeSelection is DATA, ACK, or ERROR
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
	
		// packetTypeSelection is DATA, ACK, or ERROR
		// continue with transfer
	
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
						clientSendRecvSocket.close();
						return;
					}
	
					// deal with retransmissions from both sides resulting from lost packet
	
					if (startingRequestType == PacketType.RRQ) {
						// client send ACK, server send DATA
						getAndSendRetransmits(ProcessType.CLIENT, clientSendRecvSocket, clientIP, clientPort, 
								ProcessType.SERVER, serverSendRecvSocket, serverIP, serverTID, numDrops);
					} else {
						// client send DATA, server send ACK
						getAndSendRetransmits(ProcessType.SERVER, serverSendRecvSocket, serverIP, serverTID, 
								ProcessType.CLIENT, clientSendRecvSocket, clientIP, clientPort, numDrops);
					}
					
					// finish transfer
					System.out.println("finishing transfer...");
					finishTransfer(ProcessType.CLIENT, clientSendRecvSocket, clientIP, clientPort,
							ProcessType.SERVER, serverSendRecvSocket, serverIP, serverTID);				
	
					// end simulation
					printEndSimulation();
					clientSendRecvSocket.close();
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
					
					// deal with retransmissions from both sides resulting from lost packet
	
					if (startingRequestType == PacketType.RRQ) {
						// client send ACK, server send DATA
						getAndSendRetransmits(ProcessType.CLIENT, clientSendRecvSocket, clientIP, clientPort, 
								ProcessType.SERVER, serverSendRecvSocket, serverIP, serverTID, numDrops);
					} else {
						// client send DATA, server send ACK
						getAndSendRetransmits(ProcessType.SERVER, serverSendRecvSocket, serverIP, serverTID, 
								ProcessType.CLIENT, clientSendRecvSocket, clientIP, clientPort, numDrops);
					}
					
					// finish transfer
					System.out.println("finishing transfer...");
					finishTransfer(ProcessType.SERVER, serverSendRecvSocket, serverIP, serverTID,
							ProcessType.CLIENT, clientSendRecvSocket, clientIP, clientPort);				
	
					// end simulation
					printEndSimulation();
					clientSendRecvSocket.close();
					return;	
				}
			}
	
			// send to server and end this loop iteration
			sendPacket = new DatagramPacket(receivePacket.getData(), receivePacket.getLength(), serverIP, serverTID);				
			sendPacketToProcess(serverSendRecvSocket, ProcessType.SERVER, receivedPacketType.name());
			
			
		}
	
	}

	/**
	 * Runs "Delayed Packet" mode. 
	 * A packet is received is "delayed" by waiting a specified amount of time 
	 * before sending it to the other process.
	 * Retransmissions are expected if the delay is sufficient.
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
		
		System.out.println("\nChosen parameters for delayed packet simulation: ");
		System.out.println("Process to receive delayed packet: " + receiverProcessSelection.name());
		System.out.println("Packet type to delay: " + packetTypeSelection.name());
		if (packetTypeSelection == PacketType.DATA || packetTypeSelection == PacketType.ACK)
			System.out.println("Block number of chosen packet: " + blockNumSelection);
		System.out.printf("Duration of delay: %d (%d retransmission(s) expected)\n\n", delayInMs, expectedRetransmits);
				
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
			System.out.println("could not create socket for simulating server thread's socket");
			e1.printStackTrace();
			return;
		}



		// if chosen packet type is request packet, need to make sure what was received matches
		if (packetTypeSelection == PacketType.RRQ || packetTypeSelection == PacketType.WRQ) {
			if (packetTypeSelection == PacketType.RRQ && receivedPacketType != PacketType.RRQ) {
				System.out.println("Wrong packet type received from client! (expected RRQ)");
				System.out.println("terminating simulation");
				clientSendRecvSocket.close();
				return;				
			} else if (packetTypeSelection == PacketType.WRQ && receivedPacketType != PacketType.WRQ) { 
				System.out.println("Wrong packet type received from client! (expected WRQ)");
				System.out.println("terminating simulation");
				clientSendRecvSocket.close();
				return;
			} else {				

				// received request packet, now it must be delayed

				System.out.printf("\n\tWaiting %dms before sending next packet...\n", delayInMs);
				try {
					Thread.sleep(delayInMs);
				} catch (InterruptedException e) {
					System.out.println("Interrupted while simulating delayed packet!");
					System.out.println("Terminating simulation");
					clientSendRecvSocket.close();
					return;
				}

				// send the packet after the delay
				sendPacket = new DatagramPacket(receivePacket.getData(), receivePacket.getLength());
				sendPacket.setAddress(serverIP);
				sendPacket.setPort(Server.SERVER_PORT);
				sendPacketToProcess(serverSendRecvSocket, ProcessType.SERVER, "delayed " + packetTypeSelection.name());

				PacketType serverResponseType = packetTypeSelection == PacketType.RRQ ? 
						PacketType.DATA : PacketType.ACK ;

				// receive response
				receivePacketFromProcess(serverSendRecvSocket, ProcessType.SERVER, serverResponseType.name());
				int serverTID = receivePacket.getPort();
				System.out.printf("server TID for request is %d\n", serverTID);

				// send response to client
				sendPacket = new DatagramPacket(receivePacket.getData(), receivePacket.getLength());
				sendPacket.setAddress(clientIP);
				sendPacket.setPort(clientPort);
				sendPacketToProcess(clientSendRecvSocket, ProcessType.CLIENT, receivedPacketType.name());

				// handle retransmissions (should result in client sending unknown TID error
				// to new server TIDs created from retransmitted request)
				simulateRetransmittedRequestPackets(clientSendRecvSocket, expectedRetransmits);

				System.out.println("\n\tFinishing file transfer...");

				// finish the transfer so client goes back to ready state
				finishTransfer(ProcessType.CLIENT, clientSendRecvSocket, clientIP, clientPort,
						ProcessType.SERVER, serverSendRecvSocket, serverIP, serverTID);

				// set timeout on client receive socket  
				try {
					clientRecvSocket.setSoTimeout(2*TIMEOUT_MS);
				} catch (SocketException e) {
					e.printStackTrace();
				}

				// listen to detect extra retransmits
				System.out.println("\n\tListening for further request retransmissions from client");
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
				clientSendRecvSocket.close();
				return;
			}

		} 

		// packetTypeSelection is DATA, ACK, or ERROR
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
			clientSendRecvSocket.close();
			return;
		}

		// continue with exchange
		// packetTypeSelection is DATA, ACK, or ERROR
		System.out.println("DATA/ACK/ERROR not yet implemented, stay tuned");

		clientSendRecvSocket.close();
	}



	/**
	 * Runs "Duplicate Packet" mode. 
	 * A received packet is duplicated by forwarding it to the other process twice in a row.
	 */
	private void simulateDuplicatePacket() {
	
		// prompt for number of times to send duplicate
		System.out.println("Please enter the number of times the chosen packet should get duplicated (1-3):\n");	
		String input = null;
	
		do {
			System.out.print("> ");
			input = keyboard.nextLine();
			if (input.toLowerCase().charAt(0) == 'q')
				quit();
	
		} while (!input.matches("[1-3]")); // keep looping until max 2 digit number entered 
	
		int numDups = Integer.parseInt(input);
		
		System.out.println("\nChosen parameters for duplicated packet simulation: ");
		System.out.println("Process to receive duplicated packet: " + receiverProcessSelection.name());
		System.out.println("Packet type to duplicated: " + packetTypeSelection.name());
	
		System.out.println("Block number of chosen packet: " + blockNumSelection);
		System.out.printf("Number of duplicates sent: %d\n\n", numDups);
	
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
	
				// received RRQ/WRQ, now it must be duplicated
				// save packet details so it can be resent
				byte[] originalReqData = new byte[PacketUtil.BUF_SIZE];
				int originalReqLength = receivePacket.getLength();
				PacketType originalReqType = receivedPacketType;
				System.arraycopy(receivePacket.getData(), 0, originalReqData, 0, originalReqLength);
				
				int originalServerTID = 0;
				
				if (receivedPacketType == PacketType.RRQ){
					
					sendPacket = new DatagramPacket(originalReqData, originalReqLength, serverIP, Server.SERVER_PORT);
					try {
						serverSendRecvSocket.send(sendPacket);
					} catch (IOException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
					
					
					while(true){

						// start by listening for a packet from the process whose "turn" it is
						receivePacketFromProcess(serverSendRecvSocket, ProcessType.SERVER, "DATA/ERROR");
						originalServerTID = receivePacket.getPort();
						// send received packet to other side		
						sendPacket = new DatagramPacket(receivePacket.getData(), receivePacket.getLength(), 
								clientIP, clientPort);
						sendPacketToProcess(clientSendRecvSocket, ProcessType.CLIENT, receivedPacketType.name());

						// check to see if the last packet sent will end the transfer
						if (isTerminatingErrorPacket(receivePacket)) {
							// the error packet was sent to the host and no response is expected
							return;
						} else if (isFinalDataPacket(receivePacket)) {

							// expect a final ACK, but could also be ERROR				
							receivePacketFromProcess(clientSendRecvSocket, ProcessType.CLIENT, "ACK/ERROR");

							// pass on the ACK or ERROR
							sendPacket = new DatagramPacket(receivePacket.getData(), receivePacket.getLength(), 
									serverIP, originalServerTID);
							sendPacketToProcess(serverSendRecvSocket, ProcessType.SERVER, receivedPacketType.name());

							// we're done
							return;		
						}

						// last packet sent was not terminating error or final data, so do the same for process B

						receivePacketFromProcess(clientSendRecvSocket, ProcessType.CLIENT, "ACK/ERROR");

						if(receivePacket.getData()[3] == blockNumSelection){
							for (int i = 0; i < numDups; i++){
								receivePacketFromProcess(serverSendRecvSocket, ProcessType.SERVER, "DATA/ERROR");
								if (receivePacket.getData()[3] == blockNumSelection){
									System.out.println("================================================================");
									System.out.printf("\nDuplicate Data Packet %d times received with Block number %d\n", i+1, receivePacket.getData()[3]);
									System.out.println("================================================================");
								}else{
									System.out.println("Duplicate Data Packet Test Failed");
									printEndSimulation();
									clientSendRecvSocket.close();
									return;
								}
								sendPacket = new DatagramPacket(receivePacket.getData(), receivePacket.getLength(), 
										clientIP, clientPort);
								sendPacketToProcess(clientSendRecvSocket, ProcessType.CLIENT, receivedPacketType.name());
								
								receivePacketFromProcess(clientSendRecvSocket, ProcessType.CLIENT, "ACK/ERROR");
							}
							sendPacket = new DatagramPacket(receivePacket.getData(), receivePacket.getLength(), 
									serverIP, originalServerTID);
							sendPacketToProcess(serverSendRecvSocket, ProcessType.SERVER, receivedPacketType.name());
							
							finishTransfer(ProcessType.SERVER, serverSendRecvSocket, serverIP, originalServerTID, 
									ProcessType.CLIENT, clientSendRecvSocket, clientIP, clientPort);
							System.out.println("Finished Test, Duplicate Data Packet Test Success");
							printEndSimulation();
							clientSendRecvSocket.close();
							return;
						}
						// send received packet to other side		
						sendPacket = new DatagramPacket(receivePacket.getData(), receivePacket.getLength(), 
								serverIP, originalServerTID);
						sendPacketToProcess(serverSendRecvSocket, ProcessType.SERVER, receivedPacketType.name());

						// check to see if the last packet sent will end the transfer
						if (isTerminatingErrorPacket(receivePacket)) {
							// the error packet was sent to the host and no response is expected
							return;
						}

						// still not the end of the transfer -> next loop iteration
					
					}
				}
				
				if(receivedPacketType == PacketType.WRQ){
					sendPacket = new DatagramPacket(originalReqData, originalReqLength, serverIP, Server.SERVER_PORT);
					try {
						serverSendRecvSocket.send(sendPacket);
					} catch (IOException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
					
					
					while(true){

						// start by listening for a packet from the process whose "turn" it is
						receivePacketFromProcess(serverSendRecvSocket, ProcessType.SERVER, "ACK/ERROR");
						originalServerTID = receivePacket.getPort();
						// send received packet to other side		
						if(receivePacket.getData()[3] == blockNumSelection){
							for (int i = 0; i < numDups; i++){
								receivePacketFromProcess(clientSendRecvSocket, ProcessType.CLIENT, "DATA/ERROR");
								if (receivePacket.getData()[3] == blockNumSelection){
									System.out.println("================================================================");
									System.out.printf("\nDuplicate Data Packet %d times received with Block number %d\n", i+1, receivePacket.getData()[3]);
									System.out.println("================================================================");
								}else{
									System.out.println("Duplicate Data Packet Test Failed");
									printEndSimulation();
									clientSendRecvSocket.close();
									return;
								}
								sendPacket = new DatagramPacket(receivePacket.getData(), receivePacket.getLength(), 
										serverIP, originalServerTID);
								sendPacketToProcess(serverSendRecvSocket, ProcessType.SERVER, receivedPacketType.name());
								
								receivePacketFromProcess(serverSendRecvSocket, ProcessType.SERVER, "ACK/ERROR");
							}
							sendPacket = new DatagramPacket(receivePacket.getData(), receivePacket.getLength(), 
									clientIP, clientPort);
							sendPacketToProcess(clientSendRecvSocket, ProcessType.CLIENT, receivedPacketType.name());
							
							finishTransfer(ProcessType.CLIENT, clientSendRecvSocket, clientIP, clientPort, 
									ProcessType.SERVER, serverSendRecvSocket, serverIP, originalServerTID);
							System.out.println("Finished Test, Duplicate Data Packet Test Success");
							printEndSimulation();
							clientSendRecvSocket.close();
							return;
						}
						
						sendPacket = new DatagramPacket(receivePacket.getData(), receivePacket.getLength(), 
								clientIP, clientPort);
						sendPacketToProcess(clientSendRecvSocket, ProcessType.CLIENT, receivedPacketType.name());

						// check to see if the last packet sent will end the transfer
						if (isTerminatingErrorPacket(receivePacket)) {
							// the error packet was sent to the host and no response is expected
							return;
						} else if (isFinalDataPacket(receivePacket)) {

							// expect a final ACK, but could also be ERROR				
							receivePacketFromProcess(clientSendRecvSocket, ProcessType.CLIENT, "DATA/ERROR");

							// pass on the ACK or ERROR
							sendPacket = new DatagramPacket(receivePacket.getData(), receivePacket.getLength(), 
									serverIP, originalServerTID);
							sendPacketToProcess(serverSendRecvSocket, ProcessType.SERVER, receivedPacketType.name());

							// we're done
							return;		
						}

						// last packet sent was not terminating error or final data, so do the same for process B

						receivePacketFromProcess(clientSendRecvSocket, ProcessType.CLIENT, "DATA/ERROR");

						sendPacket = new DatagramPacket(receivePacket.getData(), receivePacket.getLength(), 
								serverIP, originalServerTID);
						
						sendPacketToProcess(serverSendRecvSocket, ProcessType.SERVER, receivedPacketType.name());
						
					
					}
					
				}
				System.out.println("\n\tfinishing transfer");
				
				// finish transfer

				// end simulation
				printEndSimulation();
				clientSendRecvSocket.close();
				return;
			}
	
		}
	
		// packetTypeSelection is DATA, ACK, or ERROR
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
	
		// packetTypeSelection is DATA, ACK, or ERROR
		// continue with transfer
	
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
	
					for (int i = 1; i < numDups; i++) {					
						// "drop" the last packet received by doing nothing
						// receive retransmitted packet 
						receivePacketFromProcess(serverSendRecvSocket, ProcessType.SERVER, packetTypeSelection.name());
						System.out.printf("%s packet will be dropped.\n\n", packetTypeSelection.name());
					}
	
					if (numDups == 3) {  
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
						clientSendRecvSocket.close();
						return;
					}
	
					// deal with retransmissions from both sides resulting from lost packet
	
					if (startingRequestType == PacketType.RRQ) {
						// client send ACK, server send DATA
						getAndSendRetransmits(ProcessType.CLIENT, clientSendRecvSocket, clientIP, clientPort, 
								ProcessType.SERVER, serverSendRecvSocket, serverIP, serverTID, numDups);
					} else {
						// client send DATA, server send ACK
						getAndSendRetransmits(ProcessType.SERVER, serverSendRecvSocket, serverIP, serverTID, 
								ProcessType.CLIENT, clientSendRecvSocket, clientIP, clientPort, numDups);
					}
					
					// finish transfer
					System.out.println("finishing transfer...");
					finishTransfer(ProcessType.CLIENT, clientSendRecvSocket, clientIP, clientPort,
							ProcessType.SERVER, serverSendRecvSocket, serverIP, serverTID);				
	
					// end simulation
					printEndSimulation();
					clientSendRecvSocket.close();
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
	
					for (int i = 1; i < numDups; i++) {					
						// "drop" the last packet received by doing nothing
						// receive retransmitted packet 
						receivePacketFromProcess(clientSendRecvSocket, ProcessType.CLIENT, packetTypeSelection.name());
						System.out.printf("%s packet will be dropped.\n\n",  packetTypeSelection.name());
					}
	
					if (numDups == 3) {  
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
					
					// deal with retransmissions from both sides resulting from lost packet
	
					if (startingRequestType == PacketType.RRQ) {
						// client send ACK, server send DATA
						getAndSendRetransmits(ProcessType.CLIENT, clientSendRecvSocket, clientIP, clientPort, 
								ProcessType.SERVER, serverSendRecvSocket, serverIP, serverTID, numDups);
					} else {
						// client send DATA, server send ACK
						getAndSendRetransmits(ProcessType.SERVER, serverSendRecvSocket, serverIP, serverTID, 
								ProcessType.CLIENT, clientSendRecvSocket, clientIP, clientPort, numDups);
					}
					
					// finish transfer
					System.out.println("finishing transfer...");
					finishTransfer(ProcessType.SERVER, serverSendRecvSocket, serverIP, serverTID,
							ProcessType.CLIENT, clientSendRecvSocket, clientIP, clientPort);				
	
					// end simulation
					printEndSimulation();
					clientSendRecvSocket.close();
					return;	
				}
			}
	
			// send to server and end this loop iteration
			sendPacket = new DatagramPacket(receivePacket.getData(), receivePacket.getLength(), serverIP, serverTID);				
			sendPacketToProcess(serverSendRecvSocket, ProcessType.SERVER, receivedPacketType.name());
			
			
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
		System.out.printf("listening on port %s for %s packet from %s ... \n", recvSocket.getLocalPort(), 
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

		System.out.printf("  received %s packet ", label);
	//	System.out.println(receivePacket.getData()[2] + "," + receivePacket.getData()[3]);
		PacketUtil.printOpcodeAndLength(receivePacket);
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
		System.out.printf("listening on port %s for %s packet from %s ... \n", recvSocket.getLocalPort(), 
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

		System.out.printf("  received %s packet ", label);
		PacketUtil.printOpcodeAndLength(receivePacket);
	}

	/**
	 * Sends a packet to the given process and displays information.
	 * 	 * 
	 *  @param sendSocket		the DatagramSocket to use for sending
	 *  @param recvProcess		the process (client or server) who should be listening for the packet
	 *  @param sendPacketStr	a string describing the packet being sent, which is displayed
	 */
	private void sendPacketToProcess(DatagramSocket sendSocket, ProcessType recvProcess, String sendPacketStr) {		


		System.out.printf("sending %s packet to %s (IP: %s, port %d) ... \n", 
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

		System.out.printf("  sent %s packet ", label);
		PacketUtil.printOpcodeAndLength(sendPacket);
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
	 * Deal with retransmissions sent by the client and server as a result of 
	 *  
	 *  @param ackProcess			the ProcessType value for the process sending ACKs
	 *  @param ackSocket			the DatagramSocket that should be used to communicate with ackProcess
	 *  @param ackAddr				the InetAddress object belonging to ackProcess
	 *  @param ackPort				the port number used by ackProcess
	 *  @param dataProcess			the ProcessType value for the process sending DATAs
	 *  @param dataSocket			the DatagramSocket that should be used to communicate with dataProcess
	 *  @param dataAddr				the InetAddress object belonging to dataProcess
	 *  @param dataPort				the port number used by dataProcess
	 *  @param expectedRetransmits  the number of retransmissions expected to be sent by both sides
	 */
	private void getAndSendRetransmits(ProcessType ackProcess, DatagramSocket ackSocket, InetAddress ackAddr, int ackPort,
			ProcessType dataProcess, DatagramSocket dataSocket, InetAddress dataAddr, int dataPort, int expectedRetransmits) { 

		System.out.println("GET AND SEND RETRANSMITS");

		DatagramPacket ack = null, data = null;

		for (int i = 0; i < expectedRetransmits; i++) {
			// get retransmitted DATA
			receivePacketFromProcess(dataSocket, dataProcess, "retransmitted DATA");
			//data = new DatagramPacket(receivePacket.getData(), receivePacket.getLength());

			// send retransmitted DATA (should be acknowledged by ACK w/ same block num)
			sendPacket = new DatagramPacket(receivePacket.getData(), receivePacket.getLength(), ackAddr, ackPort);
			sendPacketToProcess(ackSocket, ackProcess, "retransmitted DATA");

			// get retransmitted ACK
			receivePacketFromProcess(ackSocket, ackProcess, "retransmitted ACK");

			// send retransmitted ACK (should be ignored)
			sendPacket = new DatagramPacket(receivePacket.getData(), receivePacket.getLength(), ackAddr, ackPort);
			sendPacketToProcess(dataSocket, dataProcess, "retransmitted ACK");

			// get ACK from retransmitted DATA
			receivePacketFromProcess(ackSocket, ackProcess, "ACK");

			// send ACK from retransmitted DATA (should be ignored)
			sendPacket = new DatagramPacket(receivePacket.getData(), receivePacket.getLength(), ackAddr, ackPort);
			sendPacketToProcess(dataSocket, dataProcess, "ACK");

		}

		// transfer should now be "caught up"
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
	 * Returns true if an ERROR packet will end a transfer once it is received.
	 *  
	 *  @param packet	the packet to inspect
	 *  @return			false if not an ERROR packet or error code is 5 (unknown TID)
	 */
	private void simulateRetransmittedRequestPackets(DatagramSocket clientSendRecvSocket, int expectedRetransmits) {

		// for each retransmit expected...
		for (int i = 0; i < expectedRetransmits; i++) {
			System.out.printf("\n\t %s retransmission %d\n", packetTypeSelection.name(), i+1);
			// listen for client retransmit
			receivePacketFromProcess(clientRecvSocket, ProcessType.CLIENT, 
					String.format("%s (retransmit %d)", packetTypeSelection.name(), i+1));

			// forward to server
			sendPacket = new DatagramPacket(receivePacket.getData(), receivePacket.getLength());
			sendPacket.setAddress(serverIP);
			sendPacket.setPort(Server.SERVER_PORT);
			sendPacketToProcess(serverSendRecvSocket, ProcessType.SERVER, 
					String.format("%s (retransmit %d)", packetTypeSelection.name(), i+1));

			PacketType serverResponseType = packetTypeSelection == PacketType.RRQ ? 
					PacketType.DATA : PacketType.ACK ;

			// receive response
			receivePacketFromProcess(serverSendRecvSocket, ProcessType.SERVER, serverResponseType.name());
			int otherServerTID = receivePacket.getPort();
			System.out.printf("server TID for retransmitted request %d is %d\n", i+1, otherServerTID);

			// create a new socket to simulate new server TID
			DatagramSocket otherClientSendRecvSocket = null;
			try {
				otherClientSendRecvSocket = new DatagramSocket();
			} catch (SocketException e1) {
				e1.printStackTrace();
			}		

			// send second response to client
			sendPacket = new DatagramPacket(receivePacket.getData(), receivePacket.getLength());
			sendPacket.setAddress(clientIP);
			sendPacket.setPort(clientPort);
			sendPacketToProcess(otherClientSendRecvSocket, ProcessType.CLIENT, serverResponseType.name());

			// listen for client ERROR
			receivePacketFromProcess(otherClientSendRecvSocket, ProcessType.CLIENT, "ERROR");

			// check if client sent unknown TID error
			printSimulationResult(ProcessType.CLIENT, PacketUtil.ERR_UNKNOWN_TID);

			// send error 5 to server (new packet in case the client didn't actually send ERROR 5)
			PacketUtil packetUtil = new PacketUtil(serverIP, otherServerTID);
			sendPacket = packetUtil.formErrorPacket(PacketUtil.ERR_UNKNOWN_TID, 
					"received packet from unrecognized source port");	

			sendPacketToProcess(serverSendRecvSocket, ProcessType.SERVER, "ERROR");

		}
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

