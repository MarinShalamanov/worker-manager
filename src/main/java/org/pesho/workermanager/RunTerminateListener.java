package org.pesho.workermanager;

public interface RunTerminateListener {
	
	public void instanceCreated(String ip);
	
	public void instanceTerminated(String ip);
}
