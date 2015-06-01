package tftp.sim;

// the kinds of TFTP errors that can be simulated
public enum ErrorType {
	ILLEGAL_OP,		// TFTP error 4
	UNKNOWN_TID,		// TFTP error 5
	NONE			// relay packets
}
