package com.github.jenkins.lastchanges;

import java.util.Set;

public interface JenkinsServerInterface {
	
	public boolean checkJenkinsConnectivity();
	
	public Set<String> getJobList();
	
	public String getFinalBuildStatus();

	public void run(String jobName);

	public String getBuildUrl();

	
}
