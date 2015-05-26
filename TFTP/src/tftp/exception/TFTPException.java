/*
 * TFTPPacketException.java
 * 
 * Authors: TEAM 1
 * 
 * This file was created specifically for the course SYSC 3303.
 */

package tftp.exception;

public class TFTPException extends Exception {
	
	protected int errCode;
	
	
	public TFTPException() {
		// TODO Auto-generated constructor stub
	}

	public TFTPException(String arg0, int errCode) {		
		super(arg0);
		this.errCode = errCode;
		// TODO Auto-generated constructor stub
	}

	public TFTPException(Throwable arg0) {
		super(arg0);
		// TODO Auto-generated constructor stub
	}

	public TFTPException(String arg0, Throwable arg1) {
		super(arg0, arg1);
		// TODO Auto-generated constructor stub
	}

	public TFTPException(String arg0, Throwable arg1, boolean arg2,
			boolean arg3) {
		super(arg0, arg1, arg2, arg3);
		// TODO Auto-generated constructor stub
	}
	
	public int getErrorCode() {
		return errCode;
	}

}
