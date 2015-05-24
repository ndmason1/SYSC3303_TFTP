package tftp.sim;


public class Message {
	private byte[] msgNum;
	public final static byte MAX = (byte) 2048;
	
	public Message (byte[] start) {
		if (start.length > 2 || start.length <= 0) System.exit(1);
		msgNum = new byte[2];
		if (start.length == 1) msgNum[1] = 0;
		else msgNum[1] = start[1];
		
		msgNum[0] = start[0];
	}
	
	public Message () {
		msgNum = new byte[2];
		msgNum[0] = 0;
		msgNum[1] = 0;
	}
	
	public Message (byte x) {
		msgNum = new byte[2];
		msgNum[1] = x;
		msgNum[0] = 0;
	}
	
	public byte[] getNext() {
		if (msgNum[1] == MAX) {
			msgNum[1] = 0;
			if (msgNum[0] == MAX) {
				msgNum[0] = 0;
			} else {
				msgNum[0]++;
			}
		} else {
			msgNum[1]++;
		}
		return msgNum;
	}
	
	public byte[] getCurrent() {
		return this.msgNum;
	}
	
	public void increment() {
		this.msgNum = this.getNext();
	}
	
	
	
	public byte[] getFiveLess() {
		for(int i = 0; i < 5; i++) {
			if(this.msgNum[1] == 0) {
				this.msgNum[1] = MAX;
				if(this.msgNum[0]==0) {
					this.msgNum[0] = MAX;
				} else {
					this.msgNum[0]--;
				}
			} else {
				this.msgNum[1]--;
			}
		}
		return this.msgNum;
	}
	
	public boolean lessThanOrEqualTo(byte n[]) {
		if(n[1] < this.msgNum[1]) return true;
		if(n[1] == this.msgNum[1] && n[0] <= this.msgNum[0])return true;
		return false;
	}
}