/*
 * Util.java
 * 
 * Author: Nigel Mason
 * Last updated: 07/05/2015
 * 
 * This file was created specifically for the course SYSC 3303.
 * Copyright (C) Nigel Mason, 2015 - All rights reserved
 */

package tftp;

import java.net.DatagramPacket;
import java.util.Random;

/**
 * 
 * This class provides utility functions for inspecting packet contents and generating values for 
 * request parameters.
 *
 */
public class Util {
	
	public static final int BUF_SIZE = 516;
	
	private static Random rand = new Random();
	
	public static void printPacketInfo(DatagramPacket packet, boolean sent) {
		
		String addrLabel = sent ? "Destination" : "Source";
		System.out.printf("\n==============================\n");
		System.out.printf("Packet length: %d\n", packet.getLength());
		System.out.printf("%s address: %s:%d\n", addrLabel, packet.getAddress().getHostAddress(),
				packet.getPort());
		System.out.printf("First four bytes: %02x %02x %02x %02x\n", packet.getData()[0], packet.getData()[1],
				packet.getData()[2], packet.getData()[3]);
		System.out.printf("==============================\n");
	}
	
	public static void printPacketContents(DatagramPacket packet, boolean sent) {
		
		String addrLabel = sent ? "Destination" : "Source";
		
		System.out.printf("Packet length: %d\n", packet.getLength());
		System.out.printf("%s address: %s:%d\n", addrLabel, packet.getAddress().getHostAddress(),
				packet.getPort());
		System.out.printf("Payload contents as string: \n  %s\n", new String(packet.getData()));
		System.out.printf("Payload contents as hex values: \n  ");		
		
		int i = 0;
		for (byte b: packet.getData()) {
			if (i == packet.getLength()) break;
			if (i!=0 && i % 10 == 0) System.out.printf("\n  ");
			System.out.printf("%02x ", b);
			i++;
		}
		System.out.println();
	}
	
	public static String getRandomFilename() {
		StringBuilder sb = new StringBuilder();
		
		// choose a random length (5 - 40)
		int length = rand.nextInt(35) + 5;
		
		// for the first n - 4 characters, choose random character
		for (int i = 0; i < length-4; i++)
			sb.append(getRandomLetter());
		
		sb.append(".txt");
		return sb.toString();
	}
	
	public static String getRandomTransferMode() {
		// just do a coin flip
		if (rand.nextBoolean())
			return "netascii";
		else
			return "octet";
	}
		
	private static char getRandomLetter() {
		// lowercase only
		int val = rand.nextInt((int)'z' - (int)'a' + 1);
		val += (int)'a';
		return (char)val;
	}
	
	
		
}
