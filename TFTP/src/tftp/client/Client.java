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

import tftp.Config;
import tftp.Logger;
import tftp.Util;
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

	private DatagramSocket sendReceiveSocket;
	private DatagramPacket sendPacket, receivePacket;
	private Logger logger;
	private int targetPort;
	
	public Client() {
		try {
			sendReceiveSocket = new DatagramSocket();
		} catch (SocketException se) {
			se.printStackTrace();
			System.exit(1);
		}
		
		targetPort = Config.getSimulateErrors() ? 68 : 69;

		logger = Logger.getInstance();				
	}

	public void cleanup() {
		sendReceiveSocket.close();
	}
	
	public void checkValidReadOperation(String path) throws AccessDeniedException, FileAlreadyExistsException {
		
		File theFile = new File(path);
		
		//Checking if the file already exists?
		if (theFile.exists()){
			
			//Checking if user can read the file
			if (!theFile.canWrite()){
				String msg = "cannot write to destination file";
				logger.error(msg);
				throw new AccessDeniedException(msg);
			}
			
			
			
			throw new FileAlreadyExistsException("destination file exists");			

		}
		
	}
	
	public void checkValidWriteOperation(String path) throws FileNotFoundException, AccessDeniedException, TFTPFileIOException {
		
		File theFile = new File(path);
		
		//Checking if the file exists
		if (!theFile.exists()){
			String msg = "source file not found: " + path;
			logger.error(msg);
			throw new FileNotFoundException("source file does not exist");
		}
		if (!theFile.canRead()){			
			throw new AccessDeniedException("cannot read from source file");
		}
		if (theFile.length() > 33553920L){
			throw new TFTPFileIOException("destination file exists", PacketUtil.ERR_UNDEFINED);
		}
	}

	private byte[] prepareReadRequestPayload(String filename, String mode) {		

		int msgLength = filename.length() + mode.length() + 4; 
		byte msg[] = new byte[msgLength];

		// preamble
		msg[0] = 0x00;
		msg[1] = 0x01;

		// filename
		byte[] fbytes = filename.getBytes(); 
		System.arraycopy(fbytes, 0, msg, 2, fbytes.length);
		msg[fbytes.length + 2] = 0x00;

		// mode
		byte[] mbytes = mode.getBytes(); 
		System.arraycopy(mbytes, 0, msg, 3+fbytes.length, mbytes.length);
		msg[fbytes.length + mbytes.length + 3] = 0x00;

		return msg;
	}

	private byte[] prepareWriteRequestPayload(String filename, String mode) {

		int msgLength = filename.length() + mode.length() + 4; 
		byte msg[] = new byte[msgLength];

		// preamble
		msg[0] = 0x00;
		msg[1] = 0x02;

		// filename
		byte[] fbytes = filename.getBytes(); 
		System.arraycopy(fbytes, 0, msg, 2, fbytes.length);
		msg[fbytes.length + 2] = 0x00;

		// mode
		byte[] mbytes = mode.getBytes(); 
		System.arraycopy(mbytes, 0, msg, 3+fbytes.length, mbytes.length);
		msg[fbytes.length + mbytes.length + 3] = 0x00;

		return msg;
	}	

	public void sendReadRequest(String fullpath, String mode) throws IOException, TFTPPacketException, TFTPFileIOException {
		
		String[] pathSegments = fullpath.split("\\"+File.separator);
		String filename = pathSegments[pathSegments.length-1];
		String dirpath = fullpath.substring(0, fullpath.length() - filename.length());
		
		logger.debug("path name is " + dirpath);

		logger.info(String.format("Starting read of file %s from server...", filename));

		byte[] payload = prepareReadRequestPayload(filename, mode);		

		// create send packet
		try {
			sendPacket = new DatagramPacket(payload, payload.length, InetAddress.getLocalHost(), targetPort);
		} catch (UnknownHostException e) {
			e.printStackTrace();
			System.exit(1);
		}

		logger.logPacketInfo(sendPacket, true);


		try {
			sendReceiveSocket.send(sendPacket);
		} catch (IOException e) {
			e.printStackTrace();
			System.exit(1);
		}

		byte data[] = new byte[Util.BUF_SIZE];
		receivePacket = new DatagramPacket(data, data.length);		

		try {			  
			sendReceiveSocket.receive(receivePacket);
			
		} catch(IOException e) {
			logger.error(e.getMessage());
			System.exit(1);
		}
		
		// check if this is an error packet
        PacketParser parser = new PacketParser();
       
        try{
        	//receive first data packet with block #1
        	parser.parseDataPacket(receivePacket, 1);
        }catch(ErrorReceivedException e){
        	logger.error(e.getMessage());
            if(e.getErrorCode() == PacketUtil.ERR_UNKNOWN_TID){
            	//request may sent to different port, so resend it
            	sendReadRequest(fullpath, mode);
            }
            
            if (e.getErrorCode() == PacketUtil.ERR_ILLEGAL_OP ){
            	//request may damaged, resend it
            	sendReadRequest(fullpath, mode);
            }
        }catch(TFTPPacketException ex){
        	logger.error(ex.getMessage());
        	PacketUtil packetUtil = new PacketUtil(receivePacket.getAddress(),receivePacket.getPort());
        	DatagramPacket errPkt = packetUtil.formErrorPacket(ex.getErrorCode(), ex.getMessage());
        	sendReceiveSocket.send(errPkt);
        	if (ex.getErrorCode() == PacketUtil.ERR_ILLEGAL_OP){
        		logger.error("File transfer can not start, terminating");
        	    return;
        	}
        }catch(TFTPFileIOException exs){
        	logger.error(exs.getMessage());
        	throw exs;
        }catch(TFTPException w){
        	logger.error(w.getMessage());
        	return;
        }
        
		// assume our request is good, set up a receiver to proceed with the transfer
		Receiver r = new Receiver(sendReceiveSocket, receivePacket.getPort());
		r.receiveFile(receivePacket, dirpath, filename);
	}

	public void sendWriteRequest(String fullpath, String mode) throws TFTPFileIOException{
		
		String[] pathSegments = fullpath.split("\\"+File.separator);
		String filename = pathSegments[pathSegments.length-1];
		
		logger.info(String.format("Starting write of file %s from server...", filename));

		byte[] payload = prepareWriteRequestPayload(filename, mode);

		// create send packet
		try {
			sendPacket = new DatagramPacket(payload, payload.length, InetAddress.getLocalHost(), targetPort);
		} catch (UnknownHostException e) {
			e.printStackTrace();
			System.exit(1);
		}

		logger.logPacketInfo(sendPacket, true);

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
		PacketParser parser = new PacketParser();
		try{
		    parser.parseAckPacket(receivePacket, 0);
		}catch(ErrorReceivedException e){
			logger.error(e.getMessage());
            if(e.getErrorCode() == PacketUtil.ERR_UNKNOWN_TID){
            	//request may sent to different port, so resend it
            	sendWriteRequest(fullpath, mode);
            }
            
            if (e.getErrorCode() == PacketUtil.ERR_ILLEGAL_OP ){
            	//request may damaged, resend it
            	sendWriteRequest(fullpath, mode);
            }
		}catch(TFTPPacketException ex){
			logger.error(ex.getMessage());
			PacketUtil packetUtil = new PacketUtil(receivePacket.getAddress(),receivePacket.getPort());
        	DatagramPacket errPkt = packetUtil.formErrorPacket(ex.getErrorCode(), ex.getMessage());
        	try{
        	    sendReceiveSocket.send(errPkt);
        	}catch(IOException ew){
        		logger.error(ew.getMessage());
        		System.exit(1);
        	}
        	if (ex.getErrorCode() == PacketUtil.ERR_ILLEGAL_OP){
        		logger.error("File transfer can not start, terminating");
        	    return;
        	}
		}catch(TFTPFileIOException exs){
        	logger.error(exs.getMessage());
        	throw exs;
		}catch(TFTPException w){
        	logger.error(w.getMessage());
        	return;
		}
		logger.logPacketInfo(receivePacket, false);

		// TODO: verify ack packet


		// assume our request is good, set up a sender to proceed with the transfer

		Sender s = new Sender(sendReceiveSocket,receivePacket.getPort());
		try {
			s.sendFile(new File(fullpath));
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}


}



