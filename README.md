# worker-manager
This tool ensures a number of workers (Amazon EC2 instances with specific image) are started.

## Prerequisite
Setup your AWS credentials and region as described [here](https://docs.aws.amazon.com/sdk-for-java/v1/developer-guide/setup-credentials.html)

## Usage
```java
RunTerminateListener listener = new RunTerminateListener() {
	@Override
	public void instanceTerminated(String ip) {
		System.out.println("Listener: Worker " + ip + " is terminated.");

	}

	@Override
	public void instanceCreated(String ip) {
		System.out.println("Listener: Worker " + ip + " is created.");
	}
};
	
Configuration configuration = new Configuration()
	.setImageId("ami-a8e97fd1")
	.setSecurityGroup("default")
	.setSecurityKeyName("test")
	.setWorkerTag(new Tag("type", "worker"))
	.setInstanceType(InstanceType.T2Micro)
	.setListener(listener);

WorkerManager manager = new WorkerManager(configuration);
manager.ensureNumberOfInstances(5);
```

To start some instances:
```
manager.runInstances(5);
```

To kill some instances:
```
manager.killInstances(5);
```

To kill a specific instance:
```
manager.killInstance("34.242.89.11");
```


*Note:* Your image virtualization type should be "hardware virtual machine (HVM)".



