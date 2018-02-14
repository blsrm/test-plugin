package com.github.jenkins.lastchanges;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.util.Set;

import javax.xml.bind.DatatypeConverter;

import com.offbytwo.jenkins.JenkinsServer;
import com.offbytwo.jenkins.model.QueueReference;

import hudson.model.TaskListener;
import net.sf.json.JSONObject;
/**
 * Connects to Remote Jenkins and execute job. Checks the build status real time and get the remote console log to the local job
 * @param baseUrl -> Remote Jenkins Base URL
 * @param jobName -> Remote Jenkins job name to be triggered
 * @param username
 * @param apitoken
 * @blockBuildUntilComplete -> Blocks the local job build until the build is complete if it set as true.
 * @listener -> For Console logging
 * @author 176912
 *
 */
public class RemoteJenkinsServer implements JenkinsServerInterface {
	
	JenkinsServer jenkins;
	int httpRetryLimit = 3;
	int buildUrlRetryLimit = 6;
	int consoleRetryLimit = 1;
	int pollInterval = 10;  //seconds
	String baseUrl;	
	String buildUrl; 		//ex:http://localhost:8080/jenkins/jobs/demo/api/json; // This will be updated by run method
	boolean blockBuildUntilComplete = true; // We will bring this in Plugin UI later // This will be updated by run method
	String username;
	String apitoken;
	String consoleOutput; // This will be updated by run method
	String buildStatusStr = "UNKNOWN"; // This will be updated by run method
	String buildNumber; // This will be updated by run method
	TaskListener listener;
	
	public RemoteJenkinsServer(String baseUrl, String username, String apitoken, TaskListener listener) {
		this.baseUrl = baseUrl;		
		this.username = username;
		this.apitoken = apitoken;		
		this.listener = listener;
	}
	
	public boolean isBlockBuildUntilComplete() {
		return blockBuildUntilComplete;
	}

	public void setBlockBuildUntilComplete(boolean blockBuildUntilComplete) {
		this.blockBuildUntilComplete = blockBuildUntilComplete;
	}
	
	public String getFinalBuildStatus() {
		return this.buildStatusStr;
	}
	
	public String getBuildUrl(){
		return buildUrl;
	}
	
	public String getBuildNumber() {
		return buildNumber;
	}
	
	public boolean checkJenkinsConnectivity() {
		
		try {
			
			jenkins = new JenkinsServer(new URI(this.baseUrl),this.username,this.apitoken);
			jenkins.getQueue();
			return true;
		} catch (Exception e) {
			listener.error("Exception in connecting to Jenkins URL: "+baseUrl);
			listener.error(e.getMessage());
			return false;
		}
	}
	
	/*
	 * Execute other method after establishJenkinsConnectivity method
	 */
	
	public Set<String> getJobList() {		
		try {
			return jenkins.getJobs().keySet();
		} catch (IOException e) {
			listener.error("Exception in getting jobList from the Jenkins URL: "+baseUrl);
			return null;
		}		
	}
	
	public void run(String jobName) {		
		
		QueueReference s = null;
		try {
			s = jenkins.getJobs().get(jobName).build(); //job name should be set prior to the run
		} catch(Exception e){
			listener.error("Unable to trigger the Automation Job: "+jobName+" in Jenkins. Job Name could be  wrong. Please verify it.\n"+e.getMessage());
			return;
		}
		
		String queueUrl = s.getQueueItemUrlPart()+"api/json"; //ex:http://localhost:8080/jenkins/queue/item/90/api/json
		
		System.out.println("Waiting for the build number to be generated");

		waitTillBuildNumberGenerated(queueUrl);	
		
		if(this.buildNumber == null){
			listener.getLogger().println("Error in retrieving the build Number for the triggered Jenkins Job: "+jobName+" Hence unable to get the console logs");
			listener.getLogger().println("Please check the job logs in remote jenkins for the error");
			return;
		}
		
		listener.getLogger().println("\nWaiting for the Automation Job to be completed....");
		
		waitUnTillJobIsComplete(this.buildUrl);

		System.out.println("Automation Job is executed completely. Click this link for detailed results: "+baseUrl+"/job/"+jobName+"/"+buildNumber);
		String consoleUrl = baseUrl+"/job/"+jobName+"/"+buildNumber+"/consoleText"; //ex: http://localhost:8080/jenkins/job/demo/15/consoleText
		listener.getLogger().println("\n***************QA Automation Remote Jenkins Console Logs as below ***********************");
		String consoleOutput = null;
		try {
			consoleOutput = getConsoleOutput(consoleUrl);
		} catch (IOException e) {
			// TODO Auto-generated catch block
			listener.error("Error in getting console logs from the remote Jenkins for the executed Automation Jobs"+"\n"+e.getMessage());
		}
				
		listener.getLogger().println(consoleOutput);
		listener.getLogger().println("QA Console Log: "+consoleUrl);
		listener.getLogger().println("View Results: "+this.buildUrl);

	}
	
    public String getConsoleOutput(String urlString)
            throws IOException {
        
            return getConsoleOutput( urlString,0 );
    }
    
	private String getConsoleOutput(String consoleUrlString, int numberOfAttempts) {
		
		String response = sendHTTPCall(consoleUrlString,"GET");		
		
		if(response == null){
			if(numberOfAttempts < this.consoleRetryLimit){
				listener.getLogger().println("Retrying "+(numberOfAttempts+1)+" time. Still the Console Output is not retrieved for the build");					
                try {
                    // Could do with a better way of sleeping...
                	Thread.sleep(this.pollInterval * 1000);	                    
                } catch (InterruptedException ex) {
                	listener.error(ex.getMessage());
                }
                numberOfAttempts++;
                getConsoleOutput(consoleUrlString,numberOfAttempts);
                
			
            }else if(numberOfAttempts > this.consoleRetryLimit){
                //reached the maximum number of retries, time to fail
                listener.getLogger().println((new Exception("Unable to get Console Output for the triggered job and hence Console logging is not done. Job name could possbily be wrong. Please check in remote jenkins. URL found here is "+consoleUrlString)));
            }else{
                //something failed with the connection and we retried the max amount of times... so throw an exception to mark the build as failed.
            	listener.error("Something failed with the connection. Please view the logs in Remot Jenkins Server");
            }
		}else{

			this.consoleOutput = response;		
		}
		return this.consoleOutput;
	}

	public void waitUnTillJobIsComplete(String buildUrl) {
		
		
		if(this.blockBuildUntilComplete){
			
			 listener.getLogger().println("Blocking local job until remote job completes");
	            // Form the URL for the triggered job
	            String jobLocation = buildUrl+"api/json"; //used the variable from paramterized plugin source code

	            this.buildStatusStr = getBuildCurrentStatus(jobLocation);

	            while (this.buildStatusStr.equals("not started")) {
	                listener.getLogger().println("Waiting for remote build to start.");
//	                listener.getLogger().println("Waiting for " + this.pollInterval + " seconds until next poll.");
	                this.buildStatusStr = getBuildCurrentStatus(jobLocation);
	                // Sleep for 'pollInterval' seconds.
	                // Sleep takes miliseconds so need to convert pollInterval to milisecopnds (x 1000)
	                try {
	                    // Could do with a better way of sleeping...
	                    Thread.sleep(this.pollInterval * 1000);
	                } catch (InterruptedException e) {
	                    listener.getLogger().println(e.getMessage());
	                }
	            }

	            listener.getLogger().println("Remote build started!");
	            while (this.buildStatusStr.equals("running")) {
	                listener.getLogger().println("Waiting for remote build to finish.");
//	                listener.getLogger().println("Waiting for " + this.pollInterval + " seconds until next poll.");
	                this.buildStatusStr = getBuildCurrentStatus(jobLocation);
	                // Sleep for 'pollInterval' seconds.
	                // Sleep takes miliseconds so need to convert pollInterval to milisecopnds (x 1000)
	                try {
	                    // Could do with a better way of sleeping...
	                    Thread.sleep(this.pollInterval * 1000);
	                } catch (InterruptedException e) {
	                    listener.getLogger().println(e.getMessage());
	                }
	            }
	            listener.getLogger().println("Remote build finished with status " + this.buildStatusStr + ".");
		}


	}
	
	public String getBuildCurrentStatus(String urlPath) {		
		String buildStatus = "UNKNOWN";
		String response = sendHTTPCall(urlPath,"GET");
		if(response == null){
			listener.getLogger().println("Unable to get Build Status for the triggered job and hence Console logging is not done. Job name could possbily be wrong. Please check in remote jenkins. URL found here is"+urlPath);				
			return null;
		}
		JSONObject responseObject = null;
		try{
			responseObject = JSONObject.fromObject(response);	
		} catch(Exception e){
			listener.error("Jenkins API Response Object for given BUILD is not valid JSON format");
		}
		
		if (responseObject == null || responseObject.getString("result") == null && responseObject.getBoolean("building") == false) {
            // build not started
            buildStatus = "not started";
            listener.getLogger().println("Build is not started for the Build Number: "+this.buildNumber);
        } else if (responseObject.getBoolean("building")) {
            // build running
            buildStatus = "running";
        } else if (responseObject.getString("result") != null) {
            // build finished
            buildStatus = responseObject.getString("result");
        } else {
            // Add additional else to check for unhandled conditions
            listener.getLogger().println(("WARNING: Unhandled condition!"));
        }

        return buildStatus;
	}
	
	/*
	 * Refer https://issues.jenkins-ci.org/browse/JENKINS-12827 for this implementation to get Build number for the triggered job
	 */
	public void waitTillBuildNumberGenerated(String queueUrl) {
		
		waitTillBuildNumberGenerated(queueUrl,0);
	}
	
	public void waitTillBuildNumberGenerated(String queueUrl, int numberOfAttempts) {
			
		String response = sendHTTPCall(queueUrl,"GET");		
		if(response == null){
				listener.getLogger().println("Unable to get Build URL for the triggered job and hence Console logging is not done. Job name could possbily be wrong. Please check in remote jenkins. URL found here is"+queueUrl);			
				
		}else{			
			JSONObject json = JSONObject.fromObject(response);					
			try {				
				this.buildUrl = JSONObject.fromObject(json.get("executable")).get("url").toString();		
				this.buildNumber =  JSONObject.fromObject(json.get("executable")).get("number").toString();	
				listener.getLogger().println("\nBuild URL and Build number is generated for the job name:  and BuildURL is "+this.buildUrl);
			} catch(Exception e){
				if(numberOfAttempts < this.buildUrlRetryLimit){
//					listener.getLogger().println("Retrying "+(numberOfAttempts+1)+" time. Still the build number is not generated from the queue");					
	                try {
	                    // Could do with a better way of sleeping...
	                	Thread.sleep(this.pollInterval * 1000);	                    
	                } catch (InterruptedException ex) {
	                    listener.getLogger().println(ex.getMessage());
	                }
	                numberOfAttempts++;
	                waitTillBuildNumberGenerated(queueUrl,numberOfAttempts);
	                
				
	            }else if(numberOfAttempts > this.buildUrlRetryLimit){
	                //reached the maximum number of retries, time to fail
	                listener.getLogger().println((new Exception("Max number of connection retries have been exeeded.")));
	            }else{
	                //something failed with the connection and we retried the max amount of times... so throw an exception to mark the build as failed.
	                listener.getLogger().println(e.getMessage());
	            }
			}		
			
		}			
		
	}
	
	
	/*
	 * Refer https://github.com/jenkinsci/parameterized-remote-trigger-plugin/blob/master/src/main/java/org/jenkinsci/plugins/ParameterizedRemoteTrigger/RemoteBuildConfiguration.java for this implementation
	 */
	
	/**
     * Orchestrates all calls to the remote server.
     * Also takes care of any credentials or failed-connection retries.
     * 
     * @param urlString     the URL that needs to be called
     * @param requestType   the type of request (GET, POST, etc)
     * @return              a valid JSON String object, or null
     * @throws IOException
     */
	
	public String sendHTTPCall(String urlPath, String requestType) {
		return sendHTTPCall(urlPath, requestType,0);
	}
	
	public String sendHTTPCall(String urlPath, String requestType, int numberOfAttempts) {
		
		String response = null;
	
		URL url = null;
		try {
			url = new URL (urlPath.replaceAll("\\s+", "%20"));
		} catch (MalformedURLException e) {
			// TODO Auto-generated catch block			
			listener.getLogger().println(e.getMessage());			
		}
	      HttpURLConnection connection = null;
		try {
			connection = (HttpURLConnection) url.openConnection();
		} catch (IOException e) {
			listener.getLogger().println(e.getMessage());			
		}
	     String authStr = this.username +":"+  this.apitoken;

	     String encoding = null;
		try {
			encoding = DatatypeConverter.printBase64Binary(authStr.getBytes("utf-8"));
		} catch (UnsupportedEncodingException e) {
			listener.getLogger().println(e.getMessage());
			return null;
		}
		try{
	      connection.setRequestMethod("GET");
	      connection.setDoOutput(true);
	      connection.setConnectTimeout(5000);
	      connection.setRequestProperty("Authorization", "Basic " + encoding);
	      connection.connect();
	      InputStream is = null;
	      
	      try {
	    	  is= connection.getInputStream();
	      } catch(FileNotFoundException  e){
	    	  // In case of a e.g. 404 status	    
	    	  listener.getLogger().println(e.getMessage());
	      }
	      
	      if(is !=null)response = getStringFromInputStream(is);	      
		} catch(IOException  e){
			listener.getLogger().println(e.getMessage());
			if( numberOfAttempts < this.httpRetryLimit) {
                listener.getLogger().println("Connection to remote server failed, waiting for to retry - " + this.pollInterval + " seconds until next attempt.");
                e.printStackTrace();
                
                // Sleep for 'pollInterval' seconds.
                // Sleep takes miliseconds so need to convert pollInterval to milisecopnds (x 1000)
                try {
                    // Could do with a better way of sleeping...
                    Thread.sleep(this.pollInterval * 1000);
                } catch (InterruptedException ex) {
                    listener.getLogger().println(ex.getMessage());
                }

 
                listener.getLogger().println("Retry attempt #" + (numberOfAttempts +1)+ " out of " + this.httpRetryLimit );
                numberOfAttempts++;
                sendHTTPCall(urlPath, requestType, numberOfAttempts);
            }else if(numberOfAttempts > this.httpRetryLimit){
                //reached the maximum number of retries, time to fail
                listener.getLogger().println((new Exception("Max number of connection retries have been exeeded.")));
            }else{
                //something failed with the connection and we retried the max amount of times... so throw an exception to mark the build as failed.
                listener.getLogger().println(e.getMessage());
            }
		}
		return response;

	}
	
	private String getStringFromInputStream(InputStream is) {

		BufferedReader br = null;
		StringBuilder sb = new StringBuilder();

		String line;
		try {

			br = new BufferedReader(new InputStreamReader(is));
			while ((line = br.readLine()) != null) {
				sb.append(line+"\n");
			}

		} catch (IOException e) {
			e.printStackTrace();
		} finally {
			if (br != null) {
				try {
					br.close();
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		}

		return sb.toString();

	}

}	

