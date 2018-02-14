package com.github.jenkins.lastchanges.pipeline;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.Map;

import javax.xml.bind.DatatypeConverter;

import com.offbytwo.jenkins.JenkinsServer;
import com.offbytwo.jenkins.model.Job;
import com.offbytwo.jenkins.model.QueueReference;

import net.sf.json.JSONObject;

public class JenkinsSDK {
	
	static JenkinsServer jenkins;
	static int httpRetryLimit = 3;
	static int buildUrlRetryLimit = 6;
	static int consoleRetryLimit = 3;
	static int pollInterval = 3;  //seconds
	static String baseUrl = "http://localhost:8080/jenkins";
	static String jobName = "test";
	static String buildUrl; 		//ex:http://localhost:8080/jenkins/jobs/demo/api/json;
	static boolean blockBuildUntilComplete = true;
	static String username;
	static String apitoken;
	static String consoleOutput;
	static String buildStatusStr = "UNKNOWN";
	static String buildNumber;
	
	public static void main(String[] args) throws URISyntaxException, IOException {
		
		System.out.println(checkJenkinsConnectivity());
		jenkins = new JenkinsServer(new URI(baseUrl),username,apitoken);	
		for(String key : getJobList().keySet()){
			System.out.println("Key is "+key);
			System.out.println("Value is "+getJobList().get(key).getUrl());
		}
		System.out.println("Executing Automation Jenkins Job "+jobName+"........");
		QueueReference s = null;
		try {
			s = jenkins.getJobs().get(jobName).build();
		} catch(Exception e){
			System.out.println("Unable to trigger the Automation Job: "+jobName+" in Jenkins. Job Name could be  wrong. Please verify it");
			return;
		}
		
		String queueUrl = s.getQueueItemUrlPart()+"api/json"; //ex:http://localhost:8080/jenkins/queue/item/90/api/json
		
		System.out.println("Job is executed");
		System.out.println("Waiting for the build number to be generated");
		waitTillBuildNumberGenerated(queueUrl);	
		System.out.println("Waiting for the Automation Job to be completed");
		waitUnTillJobIsComplete(buildUrl);	
		System.out.println("Automation Job is executed completely. Click this link for detailed results: "+baseUrl+"/job/"+jobName+"/"+buildNumber);
		String consoleUrl = baseUrl+"/job/"+jobName+"/"+buildNumber+"/consoleText"; //ex: http://localhost:8080/jenkins/job/demo/15/consoleText
		String consoleOutput = getConsoleOutput(consoleUrl);
		System.out.println(consoleOutput);
		
	}
	
	public static Map<String, Job> getJobList() {		
		try {
			return jenkins.getJobs();
		} catch (IOException e) {
			System.out.println("Exception in getting jobList from the Jenkins URL: "+baseUrl);
			return null;
		}		
	}
	
	public static boolean checkJenkinsConnectivity() {
		
		try {
			JenkinsServer jenkins = new JenkinsServer(new URI(baseUrl),username,apitoken);
			jenkins.getQueue();
			return true;
		} catch (Exception e) {
			System.out.println("Exception in connecting to Jenkins URL: "+baseUrl);
			System.out.println(e.getMessage());
			return false;
		}
	}
    public static String getConsoleOutput(String urlString)
            throws IOException {
        
            return getConsoleOutput( urlString,0 );
    }
    
	private static String getConsoleOutput(String consoleUrlString, int numberOfAttempts) {
		
		String response = sendHTTPCall(consoleUrlString,"GET");		
		
		if(response == null){
			if(numberOfAttempts <= consoleRetryLimit){
				System.out.println("Retrying "+(consoleRetryLimit+1)+" time. Still the Console Output is not retrieved for the build");					
                try {
                    // Could do with a better way of sleeping...
                	Thread.sleep(pollInterval * 1000);	                    
                } catch (InterruptedException ex) {
                    System.out.println(ex.getMessage());
                }
                consoleRetryLimit++;
                getConsoleOutput(consoleUrlString,consoleRetryLimit);
                
			
            }else if(numberOfAttempts > consoleRetryLimit){
                //reached the maximum number of retries, time to fail
                System.out.println((new Exception("Unable to get Console Output for the triggered job and hence Console logging is not done. Job name could possbily be wrong. Please check in remote jenkins. URL found here is"+consoleUrlString)));
            }else{
                //something failed with the connection and we retried the max amount of times... so throw an exception to mark the build as failed.
                System.out.println("Something failed with the connection. Please view the logs in Remot Jenkins Server");
            }
		}else{

			consoleOutput = response;		
		}
		return consoleOutput;
	}

	public static void waitUnTillJobIsComplete(String buildUrl) {
		
		
		if(blockBuildUntilComplete){
			
			 System.out.println("Blocking local job until remote job completes");
	            // Form the URL for the triggered job
	            String jobLocation = buildUrl+"api/json"; //used the variable from paramterized plugin source code

	            buildStatusStr = getBuildStatus(jobLocation);

	            while (buildStatusStr.equals("not started")) {
	                System.out.println("Waiting for remote build to start.");
	                System.out.println("Waiting for " + pollInterval + " seconds until next poll.");
	                buildStatusStr = getBuildStatus(jobLocation);
	                // Sleep for 'pollInterval' seconds.
	                // Sleep takes miliseconds so need to convert pollInterval to milisecopnds (x 1000)
	                try {
	                    // Could do with a better way of sleeping...
	                    Thread.sleep(pollInterval * 1000);
	                } catch (InterruptedException e) {
	                    System.out.println(e.getMessage());
	                }
	            }

	            System.out.println("Remote build started!");
	            while (buildStatusStr.equals("running")) {
	                System.out.println("Waiting for remote build to finish.");
	                System.out.println("Waiting for " + pollInterval + " seconds until next poll.");
	                buildStatusStr = getBuildStatus(jobLocation);
	                // Sleep for 'pollInterval' seconds.
	                // Sleep takes miliseconds so need to convert pollInterval to milisecopnds (x 1000)
	                try {
	                    // Could do with a better way of sleeping...
	                    Thread.sleep(pollInterval * 1000);
	                } catch (InterruptedException e) {
	                    System.out.println(e.getMessage());
	                }
	            }
	            System.out.println("Remote build finished with status " + buildStatusStr + ".");
		}


	}
	
	public static String getBuildStatus(String urlPath) {		
		String buildStatus = "UNKNOWN";
		String response = sendHTTPCall(urlPath,"GET");
		if(response == null){
			System.out.println("Unable to get Build Status for the triggered job and hence Console logging is not done. Job name could possbily be wrong. Please check in remote jenkins. URL found here is"+urlPath);				
			return null;
		}
		JSONObject responseObject = null;
		try{
			responseObject = JSONObject.fromObject(response);	
		} catch(Exception e){
			System.out.println("Jenkins API Response Object for given BUILD is not valid JSON format");
		}
		
		if (responseObject == null || responseObject.getString("result") == null && responseObject.getBoolean("building") == false) {
            // build not started
            buildStatus = "not started";
            System.out.println("Build is not started for the Build Number: "+buildNumber);
        } else if (responseObject.getBoolean("building")) {
            // build running
            buildStatus = "running";
        } else if (responseObject.getString("result") != null) {
            // build finished
            buildStatus = responseObject.getString("result");
        } else {
            // Add additional else to check for unhandled conditions
            System.out.println(("WARNING: Unhandled condition!"));
        }

        return buildStatus;
	}
	
	/*
	 * Refer https://issues.jenkins-ci.org/browse/JENKINS-12827 for this implementation to get Build number for the triggered job
	 */
	public static void waitTillBuildNumberGenerated(String queueUrl) {
		
		waitTillBuildNumberGenerated(queueUrl,0);
	}
	
	public static void waitTillBuildNumberGenerated(String queueUrl, int numberOfAttempts) {
			
		String response = sendHTTPCall(queueUrl,"GET");		
		if(response == null){
				System.out.println("Unable to get Build URL for the triggered job and hence Console logging is not done. Job name could possbily be wrong. Please check in remote jenkins. URL found here is"+queueUrl);			
				
		}else{			
			JSONObject json = JSONObject.fromObject(response);					
			try {				
				buildUrl = JSONObject.fromObject(json.get("executable")).get("url").toString();		
				buildNumber =  JSONObject.fromObject(json.get("executable")).get("number").toString();	
				System.out.println("Build URL and Build number is generated for the job name: "+jobName+" and BuildURL is "+buildUrl);
			} catch(Exception e){
				if(numberOfAttempts <= buildUrlRetryLimit){
					System.out.println("Retrying "+(numberOfAttempts+1)+" time. Still the build number is not generated from the queue");					
	                try {
	                    // Could do with a better way of sleeping...
	                	Thread.sleep(pollInterval * 1000);	                    
	                } catch (InterruptedException ex) {
	                    System.out.println(ex.getMessage());
	                }
	                numberOfAttempts++;
	                waitTillBuildNumberGenerated(queueUrl,numberOfAttempts);
	                
				
	            }else if(numberOfAttempts > buildUrlRetryLimit){
	                //reached the maximum number of retries, time to fail
	                System.out.println((new Exception("Max number of connection retries have been exeeded.")));
	            }else{
	                //something failed with the connection and we retried the max amount of times... so throw an exception to mark the build as failed.
	                System.out.println(e.getMessage());
	            }
			}		
			
		}			
		
	}
	
	public String getBuildUrl(){
		return buildUrl;
	}
	
	public String getBuildNumber() {
		return buildNumber;
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
	
	public static String sendHTTPCall(String urlPath, String requestType) {
		return sendHTTPCall(urlPath, requestType,0);
	}
	
	public static String sendHTTPCall(String urlPath, String requestType, int numberOfAttempts) {
		
		String response = null;
		
		URL url = null;
		try {
			url = new URL (urlPath);
		} catch (MalformedURLException e) {
			// TODO Auto-generated catch block			
			System.out.println(e.getMessage());			
		}
	      HttpURLConnection connection = null;
		try {
			connection = (HttpURLConnection) url.openConnection();
		} catch (IOException e) {
			System.out.println(e.getMessage());			
		}
	      String authStr = username +":"+  apitoken;

	      String encoding = null;
		try {
			encoding = DatatypeConverter.printBase64Binary(authStr.getBytes("utf-8"));
		} catch (UnsupportedEncodingException e) {
			System.out.println(e.getMessage());
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
	      }
	      
	      if(is !=null)response = getStringFromInputStream(is);	      
		} catch(IOException  e){
			System.out.println(e.getMessage());
			if( numberOfAttempts <= httpRetryLimit) {
                System.out.println("Connection to remote server failed, waiting for to retry - " + pollInterval + " seconds until next attempt.");
                e.printStackTrace();
                
                // Sleep for 'pollInterval' seconds.
                // Sleep takes miliseconds so need to convert pollInterval to milisecopnds (x 1000)
                try {
                    // Could do with a better way of sleeping...
                    Thread.sleep(pollInterval * 1000);
                } catch (InterruptedException ex) {
                    System.out.println(ex.getMessage());
                }

 
                System.out.println("Retry attempt #" + numberOfAttempts + " out of " + httpRetryLimit );
                numberOfAttempts++;
                sendHTTPCall(urlPath, requestType, numberOfAttempts);
            }else if(numberOfAttempts > httpRetryLimit){
                //reached the maximum number of retries, time to fail
                System.out.println((new Exception("Max number of connection retries have been exeeded.")));
            }else{
                //something failed with the connection and we retried the max amount of times... so throw an exception to mark the build as failed.
                System.out.println(e.getMessage());
            }
		}
		return response;

	}
	
	private static String getStringFromInputStream(InputStream is) {

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
