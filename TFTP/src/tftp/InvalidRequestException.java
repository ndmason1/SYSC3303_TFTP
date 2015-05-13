/*
 * InvalidRequestException.java
 * 
 * Author: Nigel Mason
 * Last updated: 07/05/2015
 * 
 * This file was created specifically for the course SYSC 3303.
 * Copyright (C) Nigel Mason, 2015 - All rights reserved
 */

package tftp;

/**
 * 
 * This exception is thrown by Server when a request has been found to be invalid or corrupted.
 *
 */
public class InvalidRequestException extends RuntimeException {

	public InvalidRequestException() {
		// TODO Auto-generated constructor stub
	}

	public InvalidRequestException(String arg0) {
		super(arg0);
		// TODO Auto-generated constructor stub
	}

	public InvalidRequestException(Throwable arg0) {
		super(arg0);
		// TODO Auto-generated constructor stub
	}

	public InvalidRequestException(String arg0, Throwable arg1) {
		super(arg0, arg1);
		// TODO Auto-generated constructor stub
	}

	public InvalidRequestException(String arg0, Throwable arg1, boolean arg2,
			boolean arg3) {
		super(arg0, arg1, arg2, arg3);
		// TODO Auto-generated constructor stub
	}

}
