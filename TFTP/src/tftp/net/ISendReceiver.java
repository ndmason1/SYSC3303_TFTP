package tftp.net;

/**
 * Should be implemented by objects that make use of (i.e. have as a member) 
 * a Sender or Receiver.
 *
 */
public interface ISendReceiver {	

	/**
	 * Returns a path that should be used by a SendReceiver to find files.
	 * 
	 * @param path	the path that the SendReceiver intends to search for a file 
	 * @return		the actual path that should be used 
	 */
	public String getStoragePath(String path);
}
