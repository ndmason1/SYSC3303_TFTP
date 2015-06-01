package tftp.sim;

// the kinds of TFTP errors that can be simulated
public enum SimulationType {
	ILLEGAL_OP,		
	UNKNOWN_TID,	
	PACKET_LOST,
	PACKET_DELAY,
	PACKET_DUPLICATE,
	SORC_APPRENTICE,
	NONE			
}
