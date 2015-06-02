package tftp.sim;

//possible ways a packet can trigger an illegal op error
public enum IllegalOperationType {
	OPCODE, 
	FILENAME, 
	MODE, 
	BLOCKNUM, 
	ERRCODE, 
	ERRMSG, 
	LENGTH_TOO_LONG,	// if actual packet length > expected packet length
	LENGTH_TOO_SHORT	// if actual packet length < expected packet length (not sure if both of these are needed)
}
