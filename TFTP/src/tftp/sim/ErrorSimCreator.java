package tftp.sim;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.UnknownHostException;

//define a TFTPSimManager class;
public class ErrorSimCreator  implements Runnable
{
	public static final int MSG_Size = 512;
	public static final int Buffer = MSG_Size+4;

	public static final int NETWORK = 0;
	public static final int PACKET = 1;
	public static final int TIP = 2;
	public static final byte DATA = 3;
	public static final int REQ = 3;
	public static final byte ACK = 4;
	public static final int DELAY = 8;
	public static final int DELETE = 9;
	public static final int DUPLICATE = 10;


	
	// UDP datagram packets and sockets used to send / receive
	private DatagramPacket ReceivePacket, SendPacket;
	
	
	private DatagramSocket sendReceieveSocket, clientSocket;
	private boolean exitNext;
	private int clientPort,serverPort;
	private InetAddress clientIP, serverIP;
	
	//Data for error generation
	private int errorType;
	private byte[] AckBlock;

	private byte packetType;
	
	public static final byte LastBlock = 127;
	

	private int errorMsg;
	
	public ErrorSimCreator( DatagramSocket clientSocket, DatagramPacket dp, Error e ) throws UnknownHostException, java.rmi.UnknownHostException {

		this.clientSocket = clientSocket;
		
	    ReceivePacket = dp;
	    serverPort = 69;
	    serverIP = InetAddress.getLocalHost();
	    clientIP = dp.getAddress();
	    exitNext = false;

	    
	    
	    this.AckBlock = new byte[4];
	    this.AckBlock[0] = 0;
	    this.AckBlock[1] = e.getBlockType();
	    this.AckBlock[2] = e.getBlockNumber().getCurrent()[0];
	    this.AckBlock[3] = e.getBlockNumber().getCurrent()[1];
	   
	    

	    if (this.AckBlock[1] == 1) {
	    	this.AckBlock[1] = 3;
	    } else if (this.AckBlock[1] == 2) {
	    	this.AckBlock[1] = 4;
	    }
	    //Sets error values
	    this.errorType = e.getErrorType();
	    this.packetType = e.getBlockType();
	    this.errorMsg = e.getErrorDetail();	    
	}

	private boolean checkLast(byte data[]) {
		if(data[0]==0&&data[1]==DATA) {
			int i;
			for(i = 4; i < data.length; i++) {
				if(data[i] == 0) {
					exitNext = true;
					return false;
				}
			}
		} else if(data[0]==0 && data[1]==ACK && exitNext) {
			return true;
		} else if(data[0]==0 && data[1]==5) {
			return true;
		}
	  
		return false;
	}

	private boolean forwardPacket() {
		byte data[] = new byte[Buffer];
		int outgoingPort;
		InetAddress outgoingIP;
		try {
			//Receives a packet
			ReceivePacket = new DatagramPacket(data,Buffer,InetAddress.getLocalHost(),clientPort);
			sendReceieveSocket.receive(ReceivePacket);
			//If packet is from the client port it is sent to the server
			if(ReceivePacket.getPort()==this.clientPort) {
				System.out.println("Recieved packet from client");
				outgoingPort = this.serverPort;
				outgoingIP = this.serverIP;
			//If it is from the server or an unknown port before the server port is set it is sent to the client
			} else if (this.serverPort == 69 || ReceivePacket.getPort()==this.serverPort) {
				if (this.serverPort == 69) this.serverPort = ReceivePacket.getPort();
				System.out.println("Recieved packet from server");
				outgoingPort = this.clientPort;
				outgoingIP = this.clientIP;
			} else {//TIP error
				System.out.println("Error with port number");
				System.exit(1);
				outgoingPort = 0; // Won't be reached but is used to stop errors in eclipse
				outgoingIP = this.serverIP;
			}
			//Checks if an error needs to be made
			//returns appropriate byte array
			byte temp[] = ErrFinder(SendPacket = new DatagramPacket(ReceivePacket.getData(),ReceivePacket.getLength(),outgoingIP,outgoingPort));
			//if temp is null, pack is "deleted" and no message is sent
			if(temp == null) {
				System.out.println("Packet Deleted");
			} else {
				//Prints out array to be sent
				for(int i = 0; i < temp.length; i++) System.out.print(temp[i]);
				System.out.println();
				//Packs and forwards data
				SendPacket = new DatagramPacket(temp,temp.length,outgoingIP,outgoingPort);
				sendReceieveSocket.send(SendPacket);
				System.out.print("Forwarded packet to ");
				if(SendPacket.getPort()==this.clientPort) System.out.println("client port: "+SendPacket.getPort());
				else if(SendPacket.getPort()==this.serverPort) System.out.println("server port: "+SendPacket.getPort());
				else System.out.println("unknown");
			}
			//returns whether or not the end has been reached
			return checkLast(ReceivePacket.getData());
		} catch (UnknownHostException e) {
			e.printStackTrace();
			System.exit(1);
		} catch (IOException e) {
			e.printStackTrace();
			System.exit(1);
		}
			

		return false; 	
	}
	
	public void run() {
		try {
			byte temp[];


			clientPort = ReceivePacket.getPort();
			


			temp = ErrFinder(SendPacket = new DatagramPacket(ReceivePacket.getData(),ReceivePacket.getLength(),serverIP,serverPort));

			for(int i = 0; i < temp.length; i++) System.out.print(temp[i]+" ");
			System.out.println();

			//SendPacket = new DatagramPacket(temp,temp.length,serverIP,serverPort);
			SendPacket = new DatagramPacket(temp,ReceivePacket.getLength(),serverIP,serverPort);
			System.out.println("Recieved Packet from client");
			sendReceieveSocket = new DatagramSocket();

			sendReceieveSocket.send(SendPacket);
			System.out.println("Forwarded packet to server");
			
			// get server response and pass to client
//			
//			if(checkLast(SendPacket.getData()))
//			{
//				System.out.println("Which type of error do you wish to generate? (select by number):");
//				System.out.println("0) No Error");
//				System.out.println("4) Packet Error");
//				System.out.println("5) TID Error");
//				System.out.println("8) Delayed Packet");
//				System.out.println("9) Deleted Packet");
//				System.out.println("10) Duplicated Packet");
//				System.out.println("Choose: ");
//				return;
//			}
//			
//
//			for(;;) {
//
//
//				{
//					System.out.println("Which type of error do you wish to generate? (select by number):");
//					System.out.println("0) No Error");
//					System.out.println("4) Packet Error");
//					System.out.println("5) TID Error");
//					System.out.println("8) Delayed Packet");
//					System.out.println("9) Deleted Packet");
//					System.out.println("10) Duplicated Packet");
//					System.out.println("Choose: ");
//					return;
//				}
//			}
//			
//			
			
		} catch (SocketException e) {
			e.printStackTrace();
			System.exit(1);
		} catch (IOException e) {
			e.printStackTrace();
			System.exit(1);
		}
		  
	}



	

	private byte[] ErrFinder(DatagramPacket packet) {
		
		
		byte temp[] = new byte[2];
		System.arraycopy(packet.getData(), 2, temp, 0, 2);
		if(this.packetType == 1 || this.packetType == 2) {
			for (int i = 0; i < 4; i++) {
				if(packet.getData()[i] != this.AckBlock[i]) return packet.getData();
			}
			return makeError(packet);
		} else if (this.packetType == 3) {
			if(packet.getData()[0]==0 && (packet.getData()[1]==1 || packet.getData()[1]==2)) return makeError(packet);
		}
		return packet.getData();
	}
	
	private byte[] makeError(DatagramPacket packet) {
		System.out.println();
		System.out.println("Error being generated.");
		System.out.println();
		byte[] block = new byte[Buffer]; //Change this
		
			
		if (this.errorType == PACKET) { // Generates a packet error
			System.arraycopy(packet.getData(), 0, block, 0, packet.getLength());
			byte temp[];
			Message bn;
			if(this.packetType == REQ) {
				int i;
				boolean set;
				switch (this.errorMsg) {
					case 1:// No starting zero
						block[0]++;
						break;
						
					case 2:// invalid op code
						block[1]=-2;
						break;
						
					case 3://No file name
						temp = new byte[4];
						System.arraycopy(block, 0, temp, 0, temp.length);
						block = temp;
						break;
						
					case 4: //No zero after filename
						for(i = 4; i < block.length; i++) {
							if (block[i]==0) break;
						}
						temp = new byte[i];
						System.arraycopy(block, 0, temp, 0, temp.length);
						block = temp;
						break;
						
					case 5: //No mode
						for(i = 4; i < block.length; i++) {
							if (block[i]==0) break;
						}
						temp = new byte[i+1];
						System.arraycopy(block, 0, temp, 0, temp.length);
						temp[temp.length-1] = 0;
						block = temp;
						break;
						
					case 6: // change mode
						for(i = 1; i < block.length; i++) {
							if (block[i] == 0) break;
						}
						String fake = new String("fake");
						byte fMode[] = new byte[i + fake.getBytes().length + 2];
						System.arraycopy(fake.getBytes(), 0, fMode, i+1, fake.getBytes().length);
						System.arraycopy(block, 0, fMode, 0, i);
						fMode[fMode.length-1] = 0;
						block = fMode;
						break;
						
					case 7: //no closing zero
						set = false;
						for(i = 4; i < block.length; i++) {
							if (block[i]==0) {
								if (set) break;
								else set = true;
							}
						}
						temp = new byte[i];
						System.arraycopy(block, 0, temp, 0, temp.length);
						block = temp;
						break;
						
					case 8: //data after closing zero
						set = false;
						for(i = 4; i < block.length; i++) {
							if (block[i]==0) {
								if (set) break;
								else set = true;
							}
						}
						temp = new byte[i+3];
						System.arraycopy(block, 0, temp, 0, temp.length);
						temp[temp.length-2] = 0;
						temp[temp.length-1] = 7;
						block = temp;
						break;
						
					default:
						System.out.println("Error: invalid error details.");
						break;
				}
				
				return block;
				
			} else if (this.packetType == 1) {//DATA
				switch (this.errorMsg) {
					case 1:// no starting 0
						block[0]++;
						break;
						
					case 2:// invalid opcode
						block[1]++;
						break;
						
					case 3://block number too large
						temp = new byte[2];
						System.arraycopy(block, 2, temp, 0, 2);
						bn = new Message(temp);
						System.arraycopy(bn.getNext(), 0, block, 2, 2);
						break;
						
					case 4://block bumber too small
						temp = new byte[2];
						System.arraycopy(block, 2, temp, 0, 2);
						bn = new Message();
						System.arraycopy(bn.getNext(), 0, block, 2, 2);
						break;

					default:
						System.out.println("Error: invalid error details.");
						break;
				}
				
				return block;
			} else if (this.packetType == 2) {//ACK
				switch (this.errorMsg) {
					case 1:// no starting zero
						block[0]++;
						break;
						
					case 2:// invalid opcode
						block[1]=-3;
						break;
						
					case 3:// block number too large
						temp = new byte[2];
						System.arraycopy(block, 2, temp, 0, 2);
						bn = new Message(temp);
						System.arraycopy(bn.getNext(), 0, block, 2, 2);
						break;
						
					case 4://block bumber too small
						temp = new byte[2];
						System.arraycopy(block, 2, temp, 0, 2);
						bn = new Message();
						System.arraycopy(bn.getNext(), 0, block, 2, 2);
						break;
						
						
					default:
						System.out.println("Error: invalid error details.");
						break;
				}
				
				return block;
			} else {
				System.out.println("Invalid packet type chosen.");
				System.exit(1);
			}
			return packet.getData();
		} else if (this.errorType == TIP) {//TIP Error generator
			DatagramPacket temp = new DatagramPacket(packet.getData(),packet.getLength(),packet.getAddress(),packet.getPort());
			try {
				//Sends data on a fake port
				DatagramSocket fakePort = new DatagramSocket();
				fakePort.send(temp);
				//recieves error and closes port
				fakePort.receive(temp = new DatagramPacket(new byte[Buffer],Buffer));
				fakePort.close();
				
				
				clientSocket.send(new DatagramPacket(temp.getData(), temp.getLength(), clientIP, clientPort));
				
				// makes sure TID error is formatted properly
				if(temp.getData()[0]==0 && temp.getData()[1]==5 && temp.getData()[2]==0 && temp.getData()[3]==5) {
					int i;
					for(i = 4; i < Buffer; i++) {
						if(temp.getData()[i]==0) break;
					}
					if (i+1 >= Buffer - 4) {
						System.out.println("Error: TID Error Message has no closing zero");
						return packet.getData();
					}
					for(int j = i; j < Buffer; j++) {
						if (temp.getData()[j]!=0) {
							System.out.println("Error: TID Error Message has data after closing zero");
							return packet.getData();
						}
					}
					System.out.println("TID Error recieved");
					return packet.getData();
				} else {
					//responds that TID error is encoded properly and returns data to be sent from real port
					System.out.println("Error: TID Error encoded improperly");
					return packet.getData();
				}
			} catch (SocketException e) {
				e.printStackTrace();
				System.out.println("Socket Exception Error");
				System.exit(1);
			} catch (IOException e) {
				e.printStackTrace();
				System.out.println("Socket Exception Error");
				System.exit(1);
			}
		} else if (this.errorType == NETWORK) {
			if (this.errorMsg == DELAY) {
				//Delay the packet
				System.out.println("Delaying data");
				try {
					Thread.sleep(1000);
				} catch (InterruptedException e) {
					e.printStackTrace();
					System.exit(1);
				}
				this.AckBlock = new byte[4];
				System.out.println("Delaying data complete");
				return packet.getData();
			} else if (this.errorMsg == DELETE) {
				//deletes packet
				this.AckBlock = new byte[4];
				return null;
			} else if (this.errorMsg == DUPLICATE) {
				//sends a duplicate packet
				try {
					this.sendReceieveSocket.send(packet);
					Thread.sleep(1000);
				} catch (InterruptedException e) {
					e.printStackTrace();
					System.exit(1);
				} catch (IOException e) {
					e.printStackTrace();
					System.exit(1);
				}
				this.AckBlock = new byte[4];
				return packet.getData();
			}
		} else {
			System.out.println("Incorrect error type.  Shutting down.");
			System.exit(1);
		}
		return packet.getData();
	}
	
	
}
