package tftp.exception;

public class ErrorReceivedException extends TFTPException {
	
	public ErrorReceivedException() {
		// TODO Auto-generated constructor stub
	}

	public ErrorReceivedException(String arg0, int errCode) {		
		super(arg0, errCode);
		// TODO Auto-generated constructor stub
	}

	public ErrorReceivedException(Throwable arg0) {
		super(arg0);
		// TODO Auto-generated constructor stub
	}

	public ErrorReceivedException(String arg0, Throwable arg1) {
		super(arg0, arg1);
		// TODO Auto-generated constructor stub
	}

	public ErrorReceivedException(String arg0, Throwable arg1, boolean arg2,
			boolean arg3) {
		super(arg0, arg1, arg2, arg3);
		// TODO Auto-generated constructor stub
	}

}
