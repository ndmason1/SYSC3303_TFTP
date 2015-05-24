package tftp.sim;


public class Error {
	private int errorType;
	private byte blockType;
	private Message blockNumber;
	private int errorDetail;
	
	public Error(int et, byte bt, Message bn, int ed) {
		this.errorType = et;
		this.blockType = bt;
		this.blockNumber = bn;
		this.errorDetail = ed;
	}
	
	public Error(int et, byte bt, Message bn) {
		this(et, bt, bn, 0);
	}
	
	public Error() {
		this(0,(byte)0,new Message());
	}
	
	public int getErrorType() {
		return this.errorType;
	}
	
	public byte getBlockType() {
		return this.blockType;
	}
	
	public Message getBlockNumber() {
		return this.blockNumber;
	}
	
	public int getErrorDetail() {
		return this.errorDetail;
	}
}