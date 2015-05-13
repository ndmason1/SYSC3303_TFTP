package tftp;

import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.*;
import java.util.*;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
//import java.net.SocketException;
import java.net.UnknownHostException;

import javax.swing.*;

import java.awt.*;

public class ClientJason extends JFrame {	 

	public static final byte PACKET_DATA_TERMINATOR = 00;
	public static final byte PACKET_RRQ = 01; // Read request TFTP Packet
	public static final byte PACKET_WRQ = 02; // Write request TFTP Packet
	public static final byte PACKET_DATA = 03; // Defines a data TFTP Packet
	public static final byte PACKET_ACK = 04; // Defines a ACK Packet
	public static final byte PACKET_ERROR = 05; // Defines an Error Packet
	public static final String DOWNLOAD_NETASCII = "netascii"; // Ascii mode 
	public static final String DOWNLOAD_OCTET = "octet"; // Octet mode
	public static final String DOWNLOAD_MAIL = "mail"; // Mail mode
	public static final int DEFAULT_TFTP_SERVER_PORT = 1069; //Default Server Port no intermediate
	public static final int DEFAULT_TFTP_TEST_PORT = 1068; //Default Test Port to intermediate
	public static final int DEFAULT_UDP_PORT = 6254; // Random port
	public static final String DEFAULT_MODE = "octet";
	public static final int BUF_SIZE = 512;

	// Integers that store communication ports, the length of the last piece of
	// data received, and the total data received
	private int localPort;
	private int remotePort;
	private int dataLength;
	private int totalDataLength;
	private int totalActualDataLength;
	private int reqID;

	// Strings storing fileName to retrieve, data transfer mode, error messages, and server IP address.
	private String fileName;
	private String mode;
	//private String errorMessage;
	private String serverIP;

	// Byte array stream objects.  Used to store all data sent/received.
	private ByteArrayOutputStream receivedData = new ByteArrayOutputStream();
	private ByteArrayOutputStream tftpSendPacket = new ByteArrayOutputStream();
	private ByteArrayInputStream tftpReceivedPacket;
	// Byte arrays
	private byte[] blockNumber = new byte[2];
	private byte[] opCode = new byte[2];
	private byte[] data = new byte[512];

	// UDP socket and packet objects.
	private DatagramSocket sendReceiveSocket;
	private DatagramPacket sendPacket, receivePacket;

	private JPanel contentPane, secondPane;
	private JTextArea textArea;
	private JButton readRequest, writeRequest, serverRequest;
	private JTextField filename, modename, serverIPaddress;
	private JLabel fileLabel, modeLabel, serverIPLabel;



	public ClientJason()
	{
		setVariables("68", "test.txt", DEFAULT_TFTP_TEST_PORT, PACKET_RRQ,
				DEFAULT_MODE);

		setBounds(50, 300, 500, 550);
		contentPane = new JPanel();
		secondPane = new JPanel();
		//contentPane.setBorder(new EmptyBorder(5, 5, 5, 5));
		add(contentPane);
		add(secondPane);
		contentPane.setLayout(new FlowLayout());
		secondPane.setLayout(new GridLayout(3,3));
		setLayout(new FlowLayout());
		setTitle("Client Side UI - SYSC3303 Team#1 Project ");

		//add TextArea
		textArea = new JTextArea();		  
		textArea.setWrapStyleWord(true);
		textArea.setLineWrap(true);

		//add Scrollpane
		JScrollPane Scroller = new JScrollPane(textArea,JScrollPane.VERTICAL_SCROLLBAR_ALWAYS,
				JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
		Scroller.setPreferredSize(new Dimension(480,380));

		contentPane.add(Scroller);

		//add Button
		serverIPLabel = new JLabel("Enter Server IP Address:");
		secondPane.add(serverIPLabel);
		serverIPaddress = new JTextField(10);
		secondPane.add(serverIPaddress);
		serverRequest = new JButton("Check IP Address");
		secondPane.add(serverRequest);

		fileLabel = new JLabel("Enter File Name:");
		secondPane.add(fileLabel);
		filename = new JTextField(10);
		secondPane.add(filename);
		readRequest = new JButton("Read Request");
		secondPane.add(readRequest);

		modeLabel = new JLabel("Enter Mode Name:");
		secondPane.add(modeLabel);
		modename = new JTextField(10);
		secondPane.add(modename);
		writeRequest = new JButton("Write Request");
		secondPane.add(writeRequest);


		// button actionlistener
		readRequest.addActionListener(new ActionListener(){
			public void actionPerformed(ActionEvent e)
			{
				//Execute when button is pressed
				//To Do
				String readfilename = filename.getText();
				String readmodename = modename.getText();
				setFileName(readfilename);
				setDataTransferMode(readmodename);
			
				setOpCode(PACKET_RRQ);
				int success = getFile();
			

				System.out.println("You clicked the read Request button");
			}

		});
		writeRequest.addActionListener(new ActionListener(){
			public void actionPerformed(ActionEvent e)
			{
				//Execute when button is pressed
				//To Do
				String writefilename = filename.getText();
				String writemodename = modename.getText();

				setFileName(writefilename);
				setDataTransferMode(writemodename);
			
				setOpCode(PACKET_WRQ);
				int success = getFile();
				
				System.out.println("You clicked the write Request button");
			}

		});

		serverRequest.addActionListener(new ActionListener(){
			public void actionPerformed(ActionEvent e)
			{
				//Execute when button is pressed
				//To Do
				System.out.println("You clicked the Server Request button");
			}

		});

		PrintStream printStream = new PrintStream(new CustomOutputStream(textArea));
		System.setOut(printStream);
		System.setErr(printStream);
		setDefaultCloseOperation(EXIT_ON_CLOSE); 


		System.out.println("===== CLIENT STARTED =====\n");

	}

	/*public Client(String serverIP, String filename)
    {
    	setVariables(serverIP, filename, DEFAULT_TFTP_TEST_PORT, PACKET_RRQ,
                DEFAULT_MODE);
    }*/

	private void setVariables(String serverIP, String filename, int remotePort,
			byte opCode, String mode) {

		byte[] blockNumber = {0,0};
		setServerIP(serverIP);
		setRemotePort(remotePort);
		setFileName(filename);
		setOpCode(opCode);
		setDataTransferMode(mode);
		setBlockNumber(blockNumber);
		setDataLength(0);
		setTotalDataLength(0);
		setLocalPort(DEFAULT_UDP_PORT);
	}


	private int initialiseSocket()
	{
		// If there are any problems throw exception
		try
		{
			sendReceiveSocket = new DatagramSocket();
		}
		catch (IOException e) {
			e.printStackTrace();
			System.exit(1);
		}
		return sendReceiveSocket.getLocalPort();
	}

	private void initialisePackets() {
		byte[] buffer = new byte[BUF_SIZE];

		try
		{
			sendPacket = new DatagramPacket(buffer, buffer.length,
					InetAddress.getLocalHost(), getRemotePort());
			receivePacket = new DatagramPacket(buffer , buffer.length);
		}
		catch(UnknownHostException e)
		{
			e.printStackTrace();
			System.exit(1);
		}
	}

	public void cleanup() {
		sendReceiveSocket.close();
	}

	private boolean buildTftpPacket()
	{
		//Byte errorCode;

		tftpSendPacket.reset();

		switch (getOpCode())
		{
		case PACKET_RRQ:
		case PACKET_WRQ:
			tftpSendPacket.write(opCode, 0, opCode.length);
			tftpSendPacket.write(getFileName().getBytes(), 0,
					getFileName().getBytes().length);
			tftpSendPacket.write(PACKET_DATA_TERMINATOR);
			tftpSendPacket.write(getDataTransferMode().getBytes(), 0,
					getDataTransferMode().getBytes().length);
			tftpSendPacket.write(PACKET_DATA_TERMINATOR);
			return true;
		case PACKET_ACK:
			tftpSendPacket.write(opCode, 0, opCode.length);
			tftpSendPacket.write(blockNumber, 0, blockNumber.length);
			return true;
		case PACKET_DATA:
			tftpSendPacket.write(opCode, 0, opCode.length);
			tftpSendPacket.write(blockNumber, 0, blockNumber.length);
			tftpSendPacket.write(data, 0, data.length);
			return true;
		case PACKET_ERROR:
			return true;
		default:
			return false;
		}
	}

	private int parseTftpPacket()
	{
		//String errorMessage;
		byte[] blockNumber = new byte[2];
		byte[] oldBlockNumber;

		tftpReceivedPacket = new ByteArrayInputStream(receivePacket.getData(),
				0, receivePacket.getLength());
		tftpReceivedPacket.read(opCode, 0, 2);

		if (opCode[1] == 3){
			opCode[1] = 03;
		}
		if (opCode[1] == 4){
			opCode[1] = 04;
		}

		switch (opCode[1])
		{
		case PACKET_ACK:
			tftpReceivedPacket.read(blockNumber, 0, 2);
			setBlockNumber(blockNumber);
			return 4;
		case PACKET_DATA:
			oldBlockNumber = getBlockNumber();
			tftpReceivedPacket.read(blockNumber, 0, 2);
			setBlockNumber(blockNumber);

			/*              int i = 0;
	        		for (byte b: getBlockNumber()) {
	        			if (i == getBlockNumber().length) break;
	        			if (i!=0 && i % 10 == 0) System.out.printf("\n  ");
	        			System.out.printf("%02x ", b);
	        			i++;
	        		}*/
			// set current packet length

			return 3;
		case PACKET_ERROR:
			return 5;
		case PACKET_RRQ:
			return 1;
		case PACKET_WRQ:
			return 2;
		default:
			return 6;
		}
	}

	private void sendTftpPacket()
	{
		byte[] data = tftpSendPacket.toByteArray();

		try
		{
			sendPacket.setData(data, 0, data.length);
			//	            if (sendPacket.getPort() != getRemotePort())
			//                sendPacket.setPort(getRemotePort());
			sendReceiveSocket.send(sendPacket);
		}
		catch(IOException e)
		{
			e.printStackTrace();
			System.exit(1);   
		}
	}

	private int readTftpPacket()
	{
		int returnValue = 0;

		try
		{
			sendReceiveSocket.receive(receivePacket);
			if (getRemotePort() != receivePacket.getPort())
				setRemotePort(receivePacket.getPort());
		}
		catch(IOException e)
		{
			e.printStackTrace();
			returnValue = 1;
		}
		return returnValue;
	}

	public int getFile() {
		int returnCode;
		
		returnCode = getFileInternal();
	
		return returnCode;
	}

	private int getFileInternal() 
	{
		boolean finished = false;
		int returnCode = 0;
		int readTftpPacketValue = 0;

		// Initialise socket and packets
		setLocalPort(initialiseSocket());
		initialisePackets();

		System.out.println("Trying to fetch file " + getFileName() + " from server "
				+ getServerIP());
		// Clear any previous received data
		receivedData.reset();
		// Set op code to read request and set remote port to 69
		//setOpCode(PACKET_RRQ);
		setRemotePort(DEFAULT_TFTP_TEST_PORT);

		// Build TFTP Read request packet
		if (buildTftpPacket())
		{
			// If packet has been built successfully, try to send it
			// System.out.println(tftpSendPacket);	    	   	
			sendTftpPacket();
			// Loop until we are finished
			while (! finished)
			{
				// Try to read packet from the network
				readTftpPacketValue = readTftpPacket();
				switch (readTftpPacketValue)
				{
				case 0:
					switch (parseTftpPacket())
					{

					case 3:
						System.out.println(new String (receivePacket.getData()));

						if (receivePacket.getLength()  == 512)
						{
							// Build an acknowledgement packet and send it back
							setOpCode(PACKET_ACK);
							if (buildTftpPacket())
								// Send packet
								sendTftpPacket();
						}
						else
						{
							// If data part is less then 512 bytes long then this is
							// the last data packet
							setOpCode(PACKET_ACK);
							// Build the last acknowledgement packet
							if (buildTftpPacket())
							{
								// Send packet
								sendTftpPacket();
								finished = true;
							}
						}
						break;
					case 4:
						
						//System.out.println(new String (getBlockNumber(), "UTF-8"));
						int intblock = (int)(getBlockNumber()[0]*10 + getBlockNumber()[1]) + 1;
						int length;
						byte[] array = new byte[2];
						array[0] = (byte)(intblock/10%10);
						array[1] = (byte)(intblock%10);
						setBlockNumber(array);
						
						File file = new File(".");
						try{
							BufferedInputStream in = 
									new BufferedInputStream(new FileInputStream(file.getAbsolutePath().replace(".", "src/") + getFileName()));
							in.skip((intblock-1)*512);

							length = in.read(data);

							if (length == -1){
								length = 0;
								data = new byte[0];
								finished = true;
							}
							
							if (length < 512){
								finished = true;
							}
							
							setOpCode(PACKET_DATA);
							if (buildTftpPacket()){
								// Send packet
								sendTftpPacket();
							}
							in.close();
						}catch(IOException e){
							e.printStackTrace();
						    System.exit(1);
						}
						break;
					case 5:
						// Error packet has been received
						finished = true;
						returnCode = 1;
						break;
					}
					break;
				}
			}
		}
		try
		{
			// Close socket.  Ignore any thrown exceptions
			cleanup();
		}
		catch (Exception e)
		{}

		return returnCode;
	}


	public void setFileName(String fileName){this.fileName = fileName;}
	public void setServerIP(String serverIP){this.serverIP = serverIP;}

	//Private set methods.
	private void setRemotePort(int remotePort) {
		if (remotePort > 0)
			this.remotePort = remotePort;
		else
			this.remotePort = DEFAULT_TFTP_SERVER_PORT;
	}

	private void setDataLength(int dataLength) {
		if (dataLength > 0)
			this.dataLength = dataLength;
		else
			this.dataLength = 0;
	}

	private void setTotalDataLength(int totalDataLength) {
		if (totalDataLength > 0)
			this.totalDataLength = totalDataLength;
		else
			this.totalDataLength = 0;
	}

	private void setTotalActualDataLength(int actualLength) {
		if (actualLength >= 0)
			this.totalActualDataLength = actualLength;
		else
			this.totalActualDataLength = 0;
	}

	private void setOpCode(byte secondOpCodeByte) {

		this.opCode[0] = 0;

		switch (secondOpCodeByte)
		{
		case PACKET_RRQ:
		case PACKET_WRQ:
		case PACKET_DATA:
		case PACKET_ACK:
		case PACKET_ERROR:
			this.opCode[1] = secondOpCodeByte;
			break;
		default:
			this.opCode[1] = PACKET_RRQ;
		}
	}
	private void setLocalPort(int localPort)
	{
		if (localPort > 0)
			this.localPort = localPort;
		else
			this.localPort = DEFAULT_UDP_PORT;
	}

	public void setDataTransferMode(String mode)
	{
		boolean defaultValue = false;

		// Checks to detect correct values
		if (! mode.equalsIgnoreCase(DOWNLOAD_NETASCII))
			if (! mode.equalsIgnoreCase(DOWNLOAD_OCTET))
				if (! mode.equalsIgnoreCase(DOWNLOAD_MAIL))
					defaultValue = true;
		// Accept the passed value according to the boolean variable defaultValue
		if (defaultValue)
			this.mode = DEFAULT_MODE;
		else
			this.mode = mode;
	}

	private void setBlockNumber(byte[] blockNumber) { this.blockNumber = blockNumber; }

	// Class get Methods
	public String getFileName() { return fileName; }
	public String getDataTransferMode() { return mode; }
	public String getServerIP() { return serverIP; }
	public int getRemotePort() { return remotePort; }
	public int getFetchedDataLength() { return totalDataLength; }
	private int getDataLength() { return dataLength; }
	private byte getOpCode() { return opCode[1]; }
	private byte[] getBlockNumber() { return blockNumber; }
	private int getActualDownloadedBytes() { return  totalActualDataLength; }

	public static void main(String[] args) {

		ClientJason c = new ClientJason();
		//c.getFile();
		c.setVisible(true);

	}



}
