# worker-manager
Simple worker management tool for pesho.org

## Prerequisite
Setup your AWS credentials and region as described [here](https://docs.aws.amazon.com/sdk-for-java/v1/developer-guide/setup-credentials.html)

## Usage
```
Configuration configuration = new Configuration()
		.setImageId("ami-a8e97fd1")
		.setSecurityGroup("default")
		.setNewInstanceKeyName("test")
		.setWorkerRegistryEndpoint("http://localhost:8889/workers")
		.setRegisterInstances(true);

WorkerManager manager = new WorkerManager(configuration);
manager.ensureNumberOfInstances(10);
```

*Note:* Your image virtualization type should be "hardware virtual machine (HVM)".
