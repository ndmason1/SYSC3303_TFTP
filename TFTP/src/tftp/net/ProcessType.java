package tftp.net;

// the communicating processes

public enum ProcessType {
	CLIENT, 
	SERVER;
	
	public static ProcessType getOther(ProcessType proc) {
		if (proc == CLIENT)
			return SERVER;
		else
			return CLIENT;
	}
}
