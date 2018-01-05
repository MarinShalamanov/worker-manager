package org.pesho.workermanager;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.stream.Collectors;

import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpDelete;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.HttpClients;

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
	private static final Tag WORKER_TAG = new Tag("type", "worker");
	private static final String STATUS_RUNNING = "running";
	private static final String WORKER_PORT = "8089";
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
		
		if (configuration.isRegisterInstances()) {
			instanceIpsToKill.stream().forEach(ip -> deregisterWorker(ip));
		}
		
		executeTryCount(() -> amazonEC2Client.terminateInstances(terminateInstancesRequest));
	}
	
	public void runInstances(int numNewInstances) {
		
		RunInstancesRequest runInstancesRequest =
				   new RunInstancesRequest();

		runInstancesRequest
			.withImageId(configuration.getImageId())
			.withInstanceType(InstanceType.T2Micro)
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

		if (configuration.isRegisterInstances()) {
			ips.stream().forEach(ip -> registerWorker(ip));
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
			.withTags(WORKER_TAG);
		
		CreateTagsResult result = executeTryCount(() -> amazonEC2Client.createTags(createTagsRequest));
		
		if (result == null) {
			System.err.println("Can't tag the new instances.");
		}
	}
	
	private void registerWorker(String workerIp) {
		HttpClient httpclient = HttpClients.createDefault();
		String url = configuration.getWorkerRegistryEndpoint() 
				+ "/" + workerIp + ":" + WORKER_PORT + ".";
		HttpPost httppost = new HttpPost(url);
		
		HttpResponse response = executeTryCount(() -> httpclient.execute(httppost));
		if (response.getStatusLine().getStatusCode() >= 400) {
			System.out.println("Error while registering worker " + workerIp);
		}
	}
	
	private void deregisterWorker(String workerIp) {
		HttpClient httpclient = HttpClients.createDefault();
		String url = configuration.getWorkerRegistryEndpoint() 
				+ "/" + workerIp + ":" + WORKER_PORT + ".";
		HttpDelete httppost = new HttpDelete(url);
		
		HttpResponse response = executeTryCount(() -> httpclient.execute(httppost));
		if (response.getStatusLine().getStatusCode() >= 400) {
			System.out.println("Error while registering worker " + workerIp);
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
	    	.filter(instance -> instance.getTags().contains(WORKER_TAG))
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
    	Configuration configuration = new Configuration()
    			.setImageId("ami-a8e97fd1")
    			.setSecurityGroup("default")
    			.setNewInstanceKeyName("test")
    			.setWorkerRegistryEndpoint("http://localhost:8889/workers")
    			.setRegisterInstances(true);
    	
    	WorkerManager manager = new WorkerManager(configuration);
    	manager.ensureNumberOfInstances(1);
    }
}
