package tftp.server.thread;

import java.util.HashMap;
import java.util.HashSet;


public class LockCollection {

	HashMap<String, Integer> readLock ;	
	HashSet<String> writeLock ;	
	
	private static LockCollection instance = null;
	
	public static LockCollection getInstance() {
		if (instance == null)
			instance = new LockCollection();
		return instance;
	}
	
	private LockCollection(){
		this.readLock= new HashMap<String, Integer>();
		this.writeLock	= new HashSet<String>();
	}
	
	//add reader into readLock
	public synchronized void addReader(String filename){
		
		if (readLock.get(filename) == null)
			readLock.put(filename, 1);
		else {
			readLock.put(filename, readLock.get(filename)+1);
		}
	}
	
	//add writer into readLock
	public synchronized void addWriter(String filename){
		writeLock.add(filename);
	}
	
	public synchronized boolean isReadLock(String filename){
		return readLock.containsKey(filename);
	}
	
	public synchronized boolean isWriteLock(String filename){
		return writeLock.contains(filename);
	}
	
	
	public synchronized boolean deleteReader(String filename){
		
		if (readLock.get(filename) == null)
			return false;
		
		if (readLock.get(filename) == 1)
			readLock.remove(filename);
		else {
			readLock.put(filename, readLock.get(filename)-1);
		}
		
		return true;
	}
	
	public synchronized boolean deleteWriter(String filename){
		if (writeLock.contains(filename)) {
			writeLock.remove(filename);
			return true;
		} else
			return false;
	}
}
