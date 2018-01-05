package org.pesho.workermanager;

public class Configuration {
	
	private boolean registerInstances = true;
	
	private String workerRegistryEndpoint;
	
	private String newInstanceKeyName;
	
	private String imageId;
	
	private String securityGroup;

	public Configuration() {
	}
	
	public boolean isRegisterInstances() {
		return registerInstances;
	}

	public Configuration setRegisterInstances(boolean registerInstances) {
		this.registerInstances = registerInstances;
		return this;
	}

	public String getWorkerRegistryEndpoint() {
		return workerRegistryEndpoint;
	}

	public Configuration setWorkerRegistryEndpoint(String workerRegistryEndpoint) {
		this.workerRegistryEndpoint = workerRegistryEndpoint;
		return this;
	}

	public String getNewInstanceKeyName() {
		return newInstanceKeyName;
	}

	public Configuration setNewInstanceKeyName(String newInstanceKeyName) {
		this.newInstanceKeyName = newInstanceKeyName;
		return this;
	}

	public String getImageId() {
		return imageId;
	}

	public Configuration setImageId(String imageId) {
		this.imageId = imageId;
		return this;
	}

	public String getSecurityGroup() {
		return securityGroup;
	}

	public Configuration setSecurityGroup(String securityGroup) {
		this.securityGroup = securityGroup;
		return this;
	}
}
