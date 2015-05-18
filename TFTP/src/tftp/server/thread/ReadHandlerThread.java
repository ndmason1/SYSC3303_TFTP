package tftp.server.thread;

import java.io.File;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.SocketException;

import tftp.exception.InvalidRequestException;
import tftp.net.Sender;

public class ReadHandlerThread extends WorkerThread {

	public ReadHandlerThread(DatagramPacket reqPacket) {
		super(reqPacket);
	}	

	@Override
	public void run() {
		byte[] data = reqPacket.getData();
		
		// validate file name		
		int i = 2;
		StringBuilder sb = new StringBuilder();
		while (data[i] != 0x00) {
			sb.append((char)data[i]);
			// reject non-printable values
			if (data[i] < 0x20 || data[i] > 0x7F)
				throw new InvalidRequestException(
						String.format("non-printable data inside file name: byte %d",i));			
			i++;
		}
		String filename = sb.toString();
		//\\//\\//\\ File Not Found - Error Code 1 //\\//\\//\\

				//Opens an input stream
				File f = new File(filename);
				if(!f.exists()){    //file doesn't exist

					byte errorCode = 1;   //error code 1 : file not found
					DatagramPacket error= OPcodeError.OPerror("FILE NOT FOUND",errorCode);  //create error packet
					error.setAddress(reqPacket.getAddress());
					error.setPort(69);		

					try {			   
						sendReceiveSocket.send(error);			   
					} catch (IOException ex) {			   
						ex.printStackTrace();			   
						System.exit(1);			   
					}			   
					sendReceiveSocket.close();			   
					return;
				}
		// move index to start of mode string
		i++;		

		// validate mode string
		sb = new StringBuilder();
		while (data[i] != 0x00) {
			sb.append((char)data[i]);			
			i++;
		}

		String mode = sb.toString();
		if (! (mode.toLowerCase().equals("netascii") || mode.toLowerCase().equals("octet")) )
			throw new InvalidRequestException("invalid mode");		

		// should be at end of packet
		if (i+1 != reqPacket.getLength())
			throw new InvalidRequestException("incorrect packet length");

		// request is good if we made it here
		// read request, so start a file transfer
		Sender s = new Sender(sendReceiveSocket, clientPort);
		try {			
			s.sendFile(new File(filename));
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		cleanup();

	}
}