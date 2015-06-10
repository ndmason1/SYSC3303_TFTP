
SYSC 3303 Project - Team 1
Nigel Mason | 100837493
Syed Taqi 	| 100887193

Setup Instructions:
	Please import the following java source files in the new project in eclipse
	
	package tftp.client
		Client.java
		ClientUI.java
		
	package tftp.client.ClientFiles
		23k.txt
		success.png
		test_certs.PNG
		test_d_984B.txt
		Thumbs.db
		
	package tftp.exception
		ErrorReceivedException.java
		TFTP Exception.java
		
	package tftp.net
		OPcodeError.java
		PacketParser.java
		PacketType.java
		PacketUtil.java
		ProcessType.java
		Receiver.java
		Sender.java
		
	package tftp.server
		Server.java
		ServerUI.java
	
	package tftp.server.ServerFiles
		23K.txt
		img.jpg
		success.png
		test_b_40B.txt
		test_c_512B.txt
		test_cets.PNG
		test_d_984B.txt
		Thumbs.db
	package tftp.server.thread
		ReadHandlerThread.java
		WorkerThread.java
		WorkerThreadFactory.java
		WriteHandlerThread.java
	
	package tftp.sim
		ErrorSimulator.java
		ErrorSimUtil.java
		IllegalOperationType.java
		SimulationType.java
		
-First Simulate without Error simulator in a normal mode. 

	ServerUI:
	-Run the serverUI first
	-Continue with default server directory 
	-You can also shutdown server by pressing Q in the menu
	- IF server receives read/write request from client for unknown file, the file NOT FOund will be generated
	- If server received read/write request from client for a file that already exists in the server directory will
			generate error code 6 : File already exists
	
	ClientUI:
	-Run the ClientUI after serverUI is turned on. 
	-Without ErrorSimulator, Client will ask you for server IP address
	-Use Default client directory
	Commands: r is used for read, and w is used for write
	-Proceed with read or write of the file 
	-Finish the transfer
	-You can also shutdown client by pressing Q in the menu
	-If Client read/write a request for a file which has restricted access, the server will return
	ACCESS VIOLATION error code 3
	-Transfer won't be completed if the request packet came from unknown TID
	
-Simulate With Error simulator to test Error scenarios 

	ClientUI:
	-Run the ClientUI first
	-command : r is used for read request, and w is used for write request
	-if bad opcode for Data, transfer could not be completed
	-if bad length for Data, transfer will not be completed
	--if server packet types are chosen at error for illegal operation, transfer will not be completed
	
	ServerUI:
	-Second Run the ServerUI
	-if invalid opcode for Data/ACK packet, server will return bad-opcode error code 4
		-Transfer wont be completed
	-if invalid length for DATA/ACK
		-Transfer wont be completed
	-if server packet types are chosen for illegal operation, transfer will not be completed
	
	ErrorSimulator:
	-Third Run the ErrorSimulator
	-Enter a valid server IP address to proceed further
	-Menu Options shows: Press 1,2,3,4,5, or 9 to proceed further
	(1) Illegal operation (TFTP error code 4)
		-It will ask you either client shall receive invalid packet or server
		-Choose one client or server
		
			-If chose client, Packet type must be chosen 
			-Data (RRQ), ACK(WRQ), or ERROR
			-Enter the block number as required for Data(RRQ)/ACK(WRQ)
			-Choose which field you want to be modified to generate error:
					Invalid op-code, Invalid Length or block number
					-transfer wont be completed if any of these choices are chosen
			-if sever is chosen: chose packet type from 
				-RRQ, WRQ, DATA, ACK, and ERROR
				- Chose from invalid op-code, invalid length, Filename, Transfer mode
				-Accordingly it will return error packet 4, transfer wont be completed
	(2) Unknown TID (TFTP error code 5)
			-Transfer won't be completed if the file is received from unknown tid
	
	(3) Lost Packet
		-If the packet is lost once, it will retransmit the packet once
	(4) Delayed Packet
		-wait for the ACK/Data packet, retransmit 2 times, if not received timeout the machine
	(5) Duplicate Packet
		-Generate duplicate packets, accepts them and continue with the transfer
	(9) No error (packets will be relayed unmodified)
	 
	Submitted Diagrams and test cases:
	test-cases-IT2 to 4.xlsx ( basically from Iteration 1 to 5)
		-Excel spreadsheet is used to write test cases
	
	Diagrams:
		