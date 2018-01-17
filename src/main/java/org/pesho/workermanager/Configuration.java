package org.pesho.workermanager;

import com.amazonaws.services.ec2.model.InstanceType;
import com.amazonaws.services.ec2.model.Tag;

public class Configuration {
	
	private String newInstanceKeyName;
	
	private String imageId;
	
	private String securityGroup;
	
	private InstanceType instanceType;
	
	private Tag workerTag;
	
	private RunTerminateListener listener;

	public Configuration() {
	}

	public String getNewInstanceKeyName() {
		return newInstanceKeyName;
	}

	public Configuration setSecurityKeyName(String newInstanceKeyName) {
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

	public InstanceType getInstanceType() {
		return instanceType;
	}

	public Configuration setInstanceType(InstanceType instanceType) {
		this.instanceType = instanceType;
		return this;
	}

	public Tag getWorkerTag() {
		return workerTag;
	}

	public Configuration setWorkerTag(Tag workerTag) {
		this.workerTag = workerTag;
		return this;
	}

	public RunTerminateListener getListener() {
		return listener;
	}

	/**
	 * The listener's run method instanceCreated() is called when instance 
	 * is created and started (its status is "running").
	 * The method instanceTerminated() is called just before terminating
	 * a instance.
	 */
	public Configuration setListener(RunTerminateListener listener) {
		this.listener = listener;
		return this;
	}
}
