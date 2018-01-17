package org.pesho.workermanager;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.stream.Collectors;

import com.amazonaws.services.ec2.AmazonEC2;
import com.amazonaws.services.ec2.AmazonEC2ClientBuilder;
import com.amazonaws.services.ec2.model.CreateTagsRequest;
import com.amazonaws.services.ec2.model.CreateTagsResult;
import com.amazonaws.services.ec2.model.DescribeInstancesRequest;
import com.amazonaws.services.ec2.model.DescribeInstancesResult;
import com.amazonaws.services.ec2.model.Instance;
import com.amazonaws.services.ec2.model.InstanceType;
import com.amazonaws.services.ec2.model.RunInstancesRequest;
import com.amazonaws.services.ec2.model.RunInstancesResult;
import com.amazonaws.services.ec2.model.Tag;
import com.amazonaws.services.ec2.model.TerminateInstancesRequest;

public class WorkerManager 
{
	private static final String STATUS_RUNNING = "running";
	private static final int TRY_COUNT = 3;
	
	private Configuration configuration;
	private AmazonEC2 amazonEC2Client;
	
	public WorkerManager(Configuration configuration) {
		setConfiguration(configuration);
		amazonEC2Client = AmazonEC2ClientBuilder.defaultClient();
	}
	
	public void setConfiguration(Configuration configuration) {
		this.configuration = configuration;
	}
	
	public void ensureNumberOfInstances(int numInstances) {
		List<Instance> runningInstances = getRunningInstances();
		
		List<String> ips = runningInstances
				.stream()
				.map(instance -> instance.getPublicIpAddress())
				.collect(Collectors.toList());
		System.out.println("The following instances are running " + ips);
		
		int difference = numInstances - runningInstances.size();
		if (difference > 0) {
			runInstances(difference);
		} else if (difference < 0) {
			killInstances(-difference);
		} else {
			System.out.println("There are exactly " + numInstances + 
					" runing instances. Nothing to do.");
		}
	}
	
	/**
	 * Kills the instance with the specified public IP. If a listener
	 * is provided in the configuration this will call its instanceTerminated() 
	 * method. 
	 * 
	 * @param publicIp IP of the instance to kill. This instance should have 
	 * the tag specified in the configuration.
	 * @return True iff the kill request is send successfully.
	 */
	public boolean killInstance(String publicIp) {
		List<Instance> runningInstances = getRunningInstances();
		
		Optional<Instance> instanceToKill = runningInstances.stream()
			.filter(instance -> instance.getPublicIpAddress().equals(publicIp))
			.findFirst();
		
		if (!instanceToKill.isPresent()) {
			return false;
		}
		
		TerminateInstancesRequest terminateInstancesRequest 
			= new TerminateInstancesRequest();
		terminateInstancesRequest.withInstanceIds(instanceToKill.get().getInstanceId());
		
		if (configuration.getListener() != null) {
			configuration.getListener().instanceTerminated(publicIp);
		}
		
		executeTryCount(() -> amazonEC2Client.terminateInstances(terminateInstancesRequest));
		return true;
	}
	
	public void killInstances(int numInstances) {
		List<Instance> runningInstances = getRunningInstances();
		
		List<Instance> instancesToKill = runningInstances.stream()
			.limit(numInstances)
			.collect(Collectors.toList());
		
		List<String> instanceIdsToKill = 
					instancesToKill
					.stream()
					.map(instance -> instance.getInstanceId())
					.collect(Collectors.toList());
		
		List<String> instanceIpsToKill = 
					instancesToKill
					.stream()
					.map(instance -> instance.getPublicIpAddress())
					.collect(Collectors.toList());
		
		TerminateInstancesRequest terminateInstancesRequest 
			= new TerminateInstancesRequest();
		terminateInstancesRequest.withInstanceIds(instanceIdsToKill);
		
		System.out.println("The following instances will be "
				+ "terminated " + instanceIpsToKill);
		
		
		if (configuration.getListener() != null) {
			instanceIpsToKill
				.stream()
				.forEach(ip -> configuration.getListener().instanceTerminated(ip));
		}
		
		executeTryCount(() -> amazonEC2Client.terminateInstances(terminateInstancesRequest));
	}
	
	public void runInstances(int numNewInstances) {
		
		RunInstancesRequest runInstancesRequest =
				   new RunInstancesRequest();

		runInstancesRequest
			.withImageId(configuration.getImageId())
			.withInstanceType(configuration.getInstanceType())
			.withMinCount(1)
			.withMaxCount(numNewInstances)
			.withKeyName(configuration.getNewInstanceKeyName())
			.withSecurityGroups(configuration.getSecurityGroup());
		
		
		RunInstancesResult result
			= executeTryCount(() -> amazonEC2Client.runInstances(runInstancesRequest));
				
		if (result == null) {
			System.err.println("Can't launch instances.");
			return;
		}
		
		List<String> ips = new ArrayList<>();
		
		List<Instance> instances = result.getReservation().getInstances();
		
		tagInstances(instances);
		
		for (Instance instance : instances) {
			String instanceId = instance.getInstanceId();
			String status = instance.getState().getName();
			
			while (!status.equals(STATUS_RUNNING)) {
				try {
					System.out.println("Instance " + instanceId + " status is " + status);
					System.out.println("Waiting...");
					
					Thread.sleep(3*1000);
					
					instance = getInstanceInfo(instanceId);
					status = instance.getState().getName();
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
			}
			
			String ip = instance.getPublicIpAddress();
			System.out.println("Intance is running: " + ip);
			ips.add(ip);
		}

		if (configuration.getListener() != null) {
			ips
				.stream()
				.forEach(ip -> configuration.getListener().instanceCreated(ip));
		}
		
		System.out.println("The following instances were created");
		System.out.println(ips);
	}
	
	private void tagInstances(List<Instance> instances) {
		CreateTagsRequest createTagsRequest = new CreateTagsRequest();
		List<String> ids = instances
				.stream()
				.map(instance -> instance.getInstanceId())
				.collect(Collectors.toList());
		
		createTagsRequest
			.withResources(ids)
			.withTags(configuration.getWorkerTag());
		
		CreateTagsResult result = executeTryCount(() -> amazonEC2Client.createTags(createTagsRequest));
		
		if (result == null) {
			System.err.println("Can't tag the new instances.");
		}
	}
	
	private List<Instance> getRunningInstances() {
		DescribeInstancesRequest describeInstanceRequest = new DescribeInstancesRequest();
		
	    DescribeInstancesResult describeInstanceResult 
	    	= executeTryCount(() -> amazonEC2Client.describeInstances(describeInstanceRequest));
	    
	    List<Instance> allInstances = new ArrayList<>(); 
	    describeInstanceResult
	    	.getReservations()
	    	.forEach(res -> allInstances.addAll(res.getInstances()) );
	    
	    List<Instance> runningInstances = allInstances.stream()
	    	.filter(instance -> instance.getState().getName().equals(STATUS_RUNNING))
	    	.filter(instance -> instance.getTags().contains(configuration.getWorkerTag()))
	    	.collect(Collectors.toList());
	    
	    return runningInstances;
	}
	
	private Instance getInstanceInfo(String instanceId) {
	    DescribeInstancesRequest describeInstanceRequest = new DescribeInstancesRequest().withInstanceIds(instanceId);
	    DescribeInstancesResult describeInstanceResult 
	    	= executeTryCount(() -> amazonEC2Client.describeInstances(describeInstanceRequest));
	    	
	    Instance instance = describeInstanceResult.getReservations().get(0).getInstances().get(0);
	    return instance;
	}
	
	private <T> T executeTryCount (Callable<T> callable) {
		return executeTryCount(callable, TRY_COUNT);
	}
	
	private <T> T executeTryCount (Callable<T> callable, int tryCount) {
		T result = null;
		boolean success = false;
		
		do {
			try {
				result = callable.call();
				success = true;
			} catch (Exception e) {
				e.printStackTrace();
				sleep(3*1000);
				
			}
			tryCount--;
		} while (!success && tryCount > 0);
		
		return result;
	}
	
	private void sleep(int milliseconds) {
		try {
			Thread.sleep(milliseconds);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
	}
	
    public static void main( String[] args )
    {
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
    }
}
