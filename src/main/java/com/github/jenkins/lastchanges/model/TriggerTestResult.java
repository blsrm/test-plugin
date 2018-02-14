package com.github.jenkins.lastchanges.model;

import java.io.Serializable;
import java.util.List;

import org.jenkinsci.plugins.scriptsecurity.sandbox.whitelists.Whitelisted;

/**
 * This class represents the test results of automation jobs triggered by this plugin
 * This object represents the test results w.r.t Controller File Name or Commit message

 */

public class TriggerTestResult implements Serializable {

	private String triggerName;  //Controller file name or Commit message
	private String qaAutomationJobName;
	private String qaAutomationJobUrl;
	private String buildStatus;
	private String comments;
      
    
    public TriggerTestResult(String triggerName, String qaAutomationJobName, String qaAutomationJobUrl, String buildStatus, String comments) {
    	this.triggerName = triggerName;
    	this.qaAutomationJobName = qaAutomationJobName;
    	this.qaAutomationJobUrl = qaAutomationJobUrl;
    	this.buildStatus = buildStatus;
    	this.comments = comments;   
    }
    
    @Whitelisted
    public String getTriggerName() {
        return triggerName;
    }
    
    @Whitelisted
    public String getQaAutomationJobName() {
        return qaAutomationJobName;
    }
    
    @Whitelisted
    public String getQaAutomationJobUrl() {
        return qaAutomationJobUrl;
    }
    
    @Whitelisted
    public String getBuildStatus() {
        return buildStatus;
    }
    
    @Whitelisted
    public String getComments() {
        return comments;
    }

}
