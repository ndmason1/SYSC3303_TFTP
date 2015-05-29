/*
 * Client.java
 * 
 * Authors: TEAM 1
 * 
 * This file was created specifically for the course SYSC 3303.
 */

package tftp.client;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.nio.file.AccessDeniedException;
import java.nio.file.FileAlreadyExistsException;

import tftp.exception.TFTPException;
import tftp.exception.TFTPFileIOException;
import tftp.exception.TFTPPacketException;
import tftp.net.PacketParser;
import tftp.net.PacketUtil;
import tftp.net.Receiver;
import tftp.net.Sender;
import tftp.exception.*;

/**
 * 
 * This class implements a client program that sends TFTP connection requests to a server.
 *
 */
public class Client {	 
	
	//Private variables
	private DatagramSocket sendReceiveSocket;
	private DatagramPacket sendPacket, receivePacket;
	
	private int targetPort;
	private String directory;
	private String filename;
	private String mode;
	private File theFile;
	
	//default constructor for testing purposes mainly
	public Client (){
		try {
			sendReceiveSocket = new DatagramSocket();
		} catch (SocketException se) {
			se.printStackTrace();
			System.exit(1);
		}

		try {
			setDirectory(new java.io.File(".").getCanonicalPath().concat(new String("\\src\\tftp\\client\\ClientFiles")));
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
	//New constructor passes on filename and mode so it can be set and used everywhere
	public Client(String file, String aMode) {
		try {
			sendReceiveSocket = new DatagramSocket();
		} catch (SocketException se) {
			se.printStackTrace();
			System.exit(1);
		}
		
		setFilename(file);
		setMode(aMode);
			
		try {
			setDirectory(new java.io.File(".").getCanonicalPath().concat(new String("\\src\\tftp\\client\\ClientFiles")));
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		setFile(new File(getDirectory().concat("\\" + getFilename())));
	}

	public void cleanup() {
		sendReceiveSocket.close();
	}
	
	public void retreiveFile(){setFile(new File(getDirectory().concat("\\" + getFilename())));}
	
	public void checkValidReadOperation() throws TFTPException {
			
		//Checking if the file already exists?
		if (getFile().exists()){
			
			//Checking if user can read the file
			if (!getFile().canWrite()){
				String msg = "cannot write to destination file";
				System.out.println(msg);
				throw new TFTPException(msg, PacketUtil.ERR_ACCESS_VIOLATION);
			}
			
			throw new TFTPException("destination file exists", PacketUtil.ERR_FILE_EXISTS);

		}
		
	}
	
	public void checkValidWriteOperation() throws TFTPException {
		
		//Checking if the file exists
		if (!getFile().exists()){
			String msg = "source file not found: " + getDirectory();
			System.out.println(msg);
			throw new TFTPException(msg, PacketUtil.ERR_FILE_NOT_FOUND);
			
		}
		if (!getFile().canRead()){			
			throw new TFTPException("cannot read from source file", PacketUtil.ERR_ACCESS_VIOLATION);
		}
		if (getFile().length() > 33553920L){
			throw new TFTPFileIOException("source file is too big! (files >  33MB not supported)", PacketUtil.ERR_UNDEFINED);
		}
	}

	private byte[] prepareReadRequestPayload() {		

		int msgLength = getFilename().length() + getMode().length() + 4; 
		byte msg[] = new byte[msgLength];

		// preamble
		msg[0] = 0x00;
		msg[1] = 0x01;

		// filename
		byte[] fbytes = getFilename().getBytes(); 
		System.arraycopy(fbytes, 0, msg, 2, fbytes.length);
		msg[fbytes.length + 2] = 0x00;

		// mode
		byte[] mbytes = getMode().getBytes(); 
		System.arraycopy(mbytes, 0, msg, 3+fbytes.length, mbytes.length);
		msg[fbytes.length + mbytes.length + 3] = 0x00;

		return msg;
	}

	private byte[] prepareWriteRequestPayload() {

		int msgLength = getFilename().length() + getMode().length() + 4; 
		byte msg[] = new byte[msgLength];

		// preamble
		msg[0] = 0x00;
		msg[1] = 0x02;

		// filename
		byte[] fbytes = getFilename().getBytes(); 
		System.arraycopy(fbytes, 0, msg, 2, fbytes.length);
		msg[fbytes.length + 2] = 0x00;

		// mode
		byte[] mbytes = getMode().getBytes(); 
		System.arraycopy(mbytes, 0, msg, 3+fbytes.length, mbytes.length);
		msg[fbytes.length + mbytes.length + 3] = 0x00;

		return msg;
	}	

	public void sendReadRequest() throws TFTPException{
		
		System.out.println("path name is " + getDirectory());

		System.out.println("Starting read of file " + getFilename() + " from server...");

		byte[] payload = prepareReadRequestPayload();		

		// create send packet
		try {
			sendPacket = new DatagramPacket(payload, payload.length, InetAddress.getLocalHost(), targetPort);
		} catch (UnknownHostException e) {
			throw new TFTPException(e.getMessage(), PacketUtil.ERR_UNDEFINED);
		}

		//logger.logPacketInfo(sendPacket, true);


		try {
			sendReceiveSocket.send(sendPacket);
		} catch (IOException e) {
			e.printStackTrace();
			System.exit(1);
		}

		byte data[] = new byte[PacketUtil.BUF_SIZE];
		receivePacket = new DatagramPacket(data, data.length);		

		try {			  
			sendReceiveSocket.receive(receivePacket);
			
		} catch(IOException e) {
			throw new TFTPException(e.getMessage(), PacketUtil.ERR_UNDEFINED);
		}
		
		// check if this is an error packet
        PacketParser parser = new PacketParser();
       
        try{
        	//receive first data packet with block #1
        	parser.parseDataPacket(receivePacket, 1);
        }catch(ErrorReceivedException e){
        	//logger.error(e.getMessage());
        	System.out.println(e.getMessage());
            throw e;
        }catch(TFTPPacketException ex){
        	//logger.error(ex.getMessage());
        	System.out.println(ex.getMessage());
        	PacketUtil packetUtil = new PacketUtil(receivePacket.getAddress(),receivePacket.getPort());
        	DatagramPacket errPkt = packetUtil.formErrorPacket(ex.getErrorCode(), ex.getMessage());
        	try {
				sendReceiveSocket.send(errPkt);
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
        	if (ex.getErrorCode() == PacketUtil.ERR_ILLEGAL_OP){
        		//logger.error("File transfer can not start, terminating");
        		System.out.println("File transfer can not start, terminating");
        	    return;
        	}
        }catch(TFTPFileIOException exs){
        	//logger.error(exs.getMessage());
        	System.out.println(exs.getMessage());
        	throw exs;
        }catch(TFTPException w){
        	//logger.error(w.getMessage());
        	System.out.println(w.getMessage());
        	return;
        }
        
		// assume our request is good, set up a receiver to proceed with the transfer
		Receiver r = new Receiver(sendReceiveSocket, receivePacket.getPort());
		r.receiveFile(receivePacket, getFile());
	}

	public void sendWriteRequest() throws TFTPException {
		
		//String[] pathSegments = fullpath.split("\\"+File.separator);
		//String filename = pathSegments[pathSegments.length-1];S
		
		//logger.info(String.format("Starting write of file %s from server...", filename));
		System.out.println("Starting write of file + " + getFilename() + " from server...");
		byte[] payload = prepareWriteRequestPayload();

		// create send packet
		try {
			sendPacket = new DatagramPacket(payload, payload.length, InetAddress.getLocalHost(), targetPort);
		} catch (UnknownHostException e) {
			e.printStackTrace();
			System.exit(1);
		}

		// send packet to server
		try {
			sendReceiveSocket.send(sendPacket);
		} catch (IOException e) {
			e.printStackTrace();
			System.exit(1);
		}

		// create recv packet
		byte data[] = new byte[PacketUtil.BUF_SIZE];
		receivePacket = new DatagramPacket(data, data.length);

		try {			  
			sendReceiveSocket.receive(receivePacket);
			
		} catch(IOException e) {
			e.printStackTrace();
			System.exit(1);
		}
		
		PacketParser parser = new PacketParser(receivePacket.getAddress(), receivePacket.getPort());
		
		try{
		    parser.parseAckPacket(receivePacket, 0);
		}catch(ErrorReceivedException e){
			//logger.error(e.getMessage());
			System.out.println(e.getMessage());
            if(e.getErrorCode() == PacketUtil.ERR_UNKNOWN_TID){
            	//request may sent to different port, so resend it
            	sendWriteRequest();
            }
            
            if (e.getErrorCode() == PacketUtil.ERR_ILLEGAL_OP ){
            	//request may damaged, resend it
            	sendWriteRequest();
            }
		}catch(TFTPPacketException ex){
			//logger.error(ex.getMessage());
			System.out.println(ex.getMessage());
			PacketUtil packetUtil = new PacketUtil(receivePacket.getAddress(),receivePacket.getPort());
        	DatagramPacket errPkt = packetUtil.formErrorPacket(ex.getErrorCode(), ex.getMessage());
        	try{
        	    sendReceiveSocket.send(errPkt);
        	}catch(IOException ew){
        		throw new TFTPException(ew.getMessage(), PacketUtil.ERR_UNDEFINED);
        	}
        	        	
        	throw ex;
		}catch(TFTPFileIOException exs){
        	//logger.error(exs.getMessage());
        	System.out.println(exs.getMessage());
        	throw exs;
		}catch(TFTPException w){
        	//logger.error(w.getMessage());
        	System.out.println(w.getMessage());
        	throw w;
		}

		// set up a sender to proceed with the transfer

		Sender s = new Sender(sendReceiveSocket, receivePacket.getPort());
		try {
			s.sendFile(getFile());
		} catch (TFTPException e) {
			System.out.println("ERROR CODE " + e.getErrorCode());
			System.out.println(e.getMessage());
		}
	}
	
	//Client get functions
	public String getDirectory(){return directory;}
	public String getFilename(){return filename;}
	public int getPortNum(){return targetPort;}
	public String getMode(){return mode;}
	public File getFile(){return theFile;}
	
	//Client set functions
	public void setFilename(String aFilename){filename = aFilename;}
	public void setDirectory(String aDirectory){directory = aDirectory;}
	public void setPortNum(int aPort){targetPort = aPort;}
	public void setMode(String aMode){mode = aMode;}
	public void setFile(File aFile){theFile = aFile;}

}



