package com.github.jenkins.lastchanges

import jenkins.model.*
import hudson.model.*
import hudson.model.TaskListener;
import hudson.model.Cause
import hudson.model.Result
import hudson.model.queue.QueueTaskFuture
import jenkins.model.Jenkins
import hudson.model.Hudson
import hudson.console.ModelHyperlinkNote
import java.util.concurrent.Future
import java.util.Set

/**
 * Triggers QA Automation job(downstream build) and returns build Status, build URL, Console Output
 * @listener -> For Console logging
 * @author 176912
 *
 */
class SameJenkinsServer implements JenkinsServerInterface {	
	
	def listener
	def buildStatus // This will be updated by run method
	def buildUrl // This will be updated by run method
		
	SameJenkinsServer(def listener) {	

		this.listener = listener						
	}
	
	@Override
	public boolean checkJenkinsConnectivity() {
		// TODO Auto-generated method stub
		return true;
	}
	
	public Set<String> getJobList() {		
		Set<String> jobList = new HashSet<String>(Jenkins.activeInstance.getJobNames());
		return jobList;	
	}
	
	public String getFinalBuildStatus() {
		return this.buildStatus;
	}
	
	public String getBuildUrl() {		
		return this.buildUrl;
	}
	
	public void run(String jobName) {
		
		AbstractBuild build = null;
		
		try {
			def currentBuild = Thread.currentThread().executable
			if(currentBuild == null) {
				listener.error("Exception in retrieving the current build to trigger downstream builds")
				return
			}
			def cause = new Cause.UpstreamCause(currentBuild)
			if(cause ==null ) {
				listener.error("Exception in retrieving the current build to trigger downstream builds")
				return
			}
			def causeAction = new hudson.model.CauseAction(cause)
			if(causeAction == null) {
				listener.error("Exception in retrieving the current build to trigger downstream builds")
				return
			}
	
			def hudson = Jenkins.activeInstance //Get the active Instance of jenkins
			
			def job = hudson.getItem(jobName) //Get the job name
			if(job == null ) {
				listener.error("Job: "+jobName+" is NOT present in the Same Server Jenkins")
				return;
			}
			
			def queueItem = ParameterizedJobMixIn.scheduleBuild2(job, 0, causeAction)
			if(queueItem == null) {
				listener.error("Unable to trigger the QA Automation job "+jobName)
			}
			
			def future = queueItem.getFuture(); //Get the future to get the build details on top of it
			
			listener.getLogger().println("Waiting for the Automation Job to be completed ........ QA Console Logs will be printed upon job completion")
			
			Run<?,?> b = future.waitForStart();    // wait for the start
			AbstractBuild abstractBuild = (AbstractBuild) future.get()
			def buildNumber = abstractBuild.getNumber(); //get build Number
			AbstractProject project = (AbstractProject) job;
			build = project.getBuildByNumber(buildNumber);
			listener.getLogger().println("\n=====================QA Automation Console Logs as below============================");
			listener.getLogger().println(build.getLog(1000));
			String url = Jenkins.getInstance().getRootUrl() + "job/" + jobName		
			this.buildUrl = url+"/"+buildNumber
			String link = ModelHyperlinkNote.encodeTo(url, "QA Results Link: "+jobName)
			listener.getLogger().println("\n"+link)
			
		}catch(Exception e) {
			listener.error("Error in running the downstream QA Automation job: "+jobName)
			return
		}
		
		this.buildStatus = build.getResult()		
	}


	
	
}
