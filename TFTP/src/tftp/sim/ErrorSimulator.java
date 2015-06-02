
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

	private static final int LISTEN_PORT = 78;
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
	private ProcessType receiverProcess;
	private ErrorType errorType;
	private IllegalOperationType illegalOpType;
	private PacketType packetType;	

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
			errorType = ErrorType.ILLEGAL_OP;
			showProcessMenu();
			break;

		case 2:
			errorType = ErrorType.UNKNOWN_TID;
			showProcessMenu();
			break;

		case 9:
			errorType = ErrorType.NONE;
			relayRequestWithoutErrors();
			break;
		}
	}

	private void showProcessMenu() {

		// build message based on previous selection
		String message = errorType == ErrorType.ILLEGAL_OP ?
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
			receiverProcess = ProcessType.CLIENT;
			break;
		case 2:
			receiverProcess = ProcessType.SERVER;
		}

		switch (errorType) {
		case ILLEGAL_OP:
			showPacketTypeMenu();
			break;

		case UNKNOWN_TID:			
			simulateUnknownTID();
			break;

		case NONE:
			break;
		}
	}

	private void showPacketTypeMenu() {

		// build message based on previous selection
		String message = receiverProcess == ProcessType.CLIENT ?
				"Client" :
				"Server"; 
		message += " selected.";

		System.out.println(message);
		System.out.println("Please choose which packet type should be modified:\n");
		System.out.println("(1) RRQ");
		System.out.println("(2) WRQ");
		System.out.println("(3) DATA");
		System.out.println("(4) ACK");
		System.out.println("(5) ERROR");
		System.out.println("[ PRESS Q TO QUIT ]\n");

		switch (getInput()) {
		case 1:
			packetType = PacketType.RRQ;
			break;

		case 2:
			packetType = PacketType.WRQ;
			break;

		case 3:
			packetType = PacketType.DATA;
			break;

		case 4:
			packetType = PacketType.ACK;
			break;

		case 5:
			packetType = PacketType.ERROR;
			break;
		}

		showIllegalOpTypeMenu();
	}

	private void showIllegalOpTypeMenu() {

		// build message based on previous selection
		String message = packetType.name() + " packet selected";
		System.out.println(message);

		LinkedList<String> options = new LinkedList<String>();
		options.add("(1) Invalid op-code");
		options.add("(2) Length (contents of packet not modified)");

		// generate more options based on packet selection
		// TODO: enable more specific options e.g. exact byte?		
		switch (packetType) {
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
			illegalOpType = IllegalOperationType.OPCODE;
			break;

		case 2:
			illegalOpType = IllegalOperationType.LENGTH_TOO_SHORT;
			break;

		case 3:
			// option 3 depends on chosen packet type
			switch (packetType) {
			case RRQ:
			case WRQ:
				illegalOpType = IllegalOperationType.FILENAME;
				break;

			case DATA:
			case ACK:
				illegalOpType = IllegalOperationType.BLOCKNUM;
				break;

			case ERROR:
				illegalOpType = IllegalOperationType.ERRCODE;
			}

			break;

		case 4:
			// option 4 depends on chosen packet type
			switch (packetType) {
			case RRQ:
			case WRQ:
				illegalOpType = IllegalOperationType.MODE;
				break;

			case DATA:
			case ACK:
				throw new RuntimeException("invalid option chosen from illegal op menu");

			case ERROR:
				illegalOpType = IllegalOperationType.ERRMSG;
			}			
		}

		System.out.println("\nChosen parameters: ");
		System.out.println("Process to receive error: " + receiverProcess.name());
		System.out.println("Packet type to modify: " + packetType.name());
		System.out.println("Packet field/property to modify: " + illegalOpType.name());
		System.out.println("\n");

		System.out.println("==== EXECUTING SIMULATION ====");

		// start simulation
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

		System.out.println("response sent - simulation complete.");
		System.out.println("press any key to continue");
		keyboard.nextLine();
	}
	
	private void simulateUnknownTID() {
		
		// build message based on previous selection
		String message = receiverProcess == ProcessType.CLIENT ?
				"Client" :
					"Server"; 
		message += " selected.";
		System.out.println(message);
		
		if (receiverProcess == ProcessType.CLIENT) {
			System.out.println("If RRQ is made, Client will receive DATA 2 from unknown TID");
			System.out.println("If WRQ is made, Client will receive ACK 1 from unknown TID");			
		} else { // receiverProcess == ProcessType.SERVER
			System.out.println("If RRQ is made, Server will receive ACK 1 from unknown TID");
			System.out.println("If WRQ is made, Server will receive DATA 1 from unknown TID");
		}
		
		byte data[] = new byte[PacketUtil.BUF_SIZE];
		receivePacket = new DatagramPacket(data, data.length);			
		receivePacket.getLength();

		// listen for a client packet
		System.out.printf("listening for client packet on port %d...", LISTEN_PORT);
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
		
		// check packet type
		if (receivePacket.getData()[1] == 0x1) {
			packetType = PacketType.RRQ;
		} else if (receivePacket.getData()[1] == 0x2) {
			packetType = PacketType.WRQ;
		} else {
			System.out.println("Wrong packet type received from client! (expected RRQ or WRQ) [FAIL]");
			System.out.println("terminating simulation");
			return;
		}
		
		// pass request to server (establish client TID to the server)
		sendPacket = new DatagramPacket(receivePacket.getData(), receivePacket.getLength(), 
				serverIP, SERVER_PORT);
		
		// send new packet to server
		try {
			serverSendRecvSocket.send(sendPacket);
		} catch (IOException e) {
			e.printStackTrace();
			System.exit(1);
		}

		// receive server response
		System.out.println("request passed on to server.");
		System.out.println("waiting for server response...");
		try {
			serverSendRecvSocket.receive(receivePacket);
		} catch (IOException e) {
			System.out.println("IOException caught receiving server packet: " + e.getMessage());			
			System.exit(1);
		}	
		System.out.println("received server packet");
		printOpcode(receivePacket);
		
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

		System.out.println("sending server response to client...");
		try {
			clientSendRecvSocket.send(sendPacket);
		} catch (IOException e) {
			e.printStackTrace();
			System.exit(1);
		}
		
		// get first client DATA / ACK
		System.out.println("waiting for client packet...");
		try {
			clientSendRecvSocket.receive(receivePacket);
		} catch (IOException e) {
			System.out.println("IOException caught receiving client packet: " + e.getMessage());			
			System.exit(1);
		}	
		System.out.println("received client DATA/ACK packet");
		printOpcode(receivePacket);
		
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
		
		
		if (receiverProcess == ProcessType.SERVER) {
			System.out.println("sending client packet to server from unknown TID...");
			// server is receiving the unknown TID packet, send this from the unknown TID socket
			try {
				unknownTIDSocket.send(sendPacket);
			} catch (IOException e) {
				e.printStackTrace();
				System.exit(1);			
			}
			
			// receive server response (should be an ERROR with code 5)
			System.out.println("waiting for ERROR packet from server...");
			try {
				unknownTIDSocket.receive(receivePacket);
			} catch (IOException e) {
				System.out.println("IOException caught receiving server packet: " + e.getMessage());			
				System.exit(1);
			}	
			System.out.println("received server packet");
			printOpcode(receivePacket);
			
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
			System.out.println("sending client packet to server...");
			try {
				serverSendRecvSocket.send(sendPacket);
			} catch (IOException e) {
				e.printStackTrace();
				System.exit(1);
			}
			
			// receive server response 
			System.out.println("waiting for packet from server...");
			try {
				serverSendRecvSocket.receive(receivePacket);
			} catch (IOException e) {
				e.printStackTrace();			
				System.exit(1);
			}	
			System.out.println("received server packet");
			printOpcode(receivePacket);
			
			// pass response to client from unknown TID socket
			sendPacket = new DatagramPacket(receivePacket.getData(), receivePacket.getLength());
			sendPacket.setAddress(clientIP);
			sendPacket.setPort(clientPort);

			System.out.println("sending server packet to client from unknown TID...");
			try {
				unknownTIDSocket.send(sendPacket);
			} catch (IOException e) {
				e.printStackTrace();
				System.exit(1);
			}

			// receive client response (should be an ERROR with code 5)
			System.out.println("waiting for ERROR packet from client...");
			try {
				unknownTIDSocket.receive(receivePacket);
			} catch (IOException e) {
				e.printStackTrace();			
				System.exit(1);
			}	
			System.out.println("received client packet");
			printOpcode(receivePacket);

			if (receivePacket.getData()[1] == PacketUtil.ERROR_FLAG) {

				if (receivePacket.getData()[3] == PacketUtil.ERR_UNKNOWN_TID) {
					String msg = getErrMessage(receivePacket.getData());
					System.out.println("Client responded with ERROR code 5 as expected! [PASS]");
					System.out.printf("error message from packet: \"%s\"", msg);
				} else {
					String msg = getErrMessage(receivePacket.getData());
					System.out.printf("Client responded with ERROR code %d (not 5 as expected)", receivePacket.getData()[3]);
					System.out.printf("error message from packet: \"%s\"", msg);
				}


			} else { // not an error packet
				System.out.println("Client response was not an ERROR packet as expected [FAIL]"); 
			}
			
			
		}
		
	 
		
		System.out.println("\nsimulation complete.");
		System.out.println("press any key to continue");
		keyboard.nextLine();
		
	}

	private void simulateIllegalOperation() {

		byte data[] = new byte[PacketUtil.BUF_SIZE];		
		receivePacket = new DatagramPacket(data, data.length);			
		receivePacket.getLength();

		// listen for a client packet
		System.out.printf("listening for client packet on port %d...", LISTEN_PORT);
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

		// if chosen packet type is request packet, need to make sure what was received matches
		if (packetType == PacketType.RRQ) {
			if (receivePacket.getData()[1] != 0x1) {
				System.out.println("Wrong packet type received from client! (expected RRQ)");
				System.out.println("terminating simulation");
				return;
			} else {
				// received RRQ, now it must be modified to trigger illegal operation
				sendPacket = getCorruptedPacket(receivePacket, illegalOpType);
				sendPacket.setAddress(serverIP);
				sendPacket.setPort(SERVER_PORT);
			}

		} else if (packetType == PacketType.WRQ) {
			if (receivePacket.getData()[1] != 0x2) {
				System.out.println("Wrong packet type received from client! (expected WRQ)");
				System.out.println("terminating simulation");
				return;
			} else {
				// received WRQ, now it must be modified to trigger illegal operation
				sendPacket = getCorruptedPacket(receivePacket, illegalOpType);
				sendPacket.setAddress(serverIP);
				sendPacket.setPort(SERVER_PORT);
			}
		} else {
			sendPacket = new DatagramPacket(receivePacket.getData(), receivePacket.getLength(), serverIP, SERVER_PORT);
		}

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

		System.out.println("response sent - simulation complete.");
		System.out.println("press any key to continue");
		keyboard.nextLine(); 
	}

	private void getClientPacket() {
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
		
		int length = 0;
		for (int i = 4; i < data.length; i++) {
			sb.append((char)data[i]);
			if (data[i] == 0)				
				break;
		}

		return sb.toString();
	}

	private void printOpcode(DatagramPacket packet) {
		byte[] data = packet.getData();
		System.out.printf("opcode: %02x %02x\n", data[0], data[1]);
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

