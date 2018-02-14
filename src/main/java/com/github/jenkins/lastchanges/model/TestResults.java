package com.github.jenkins.lastchanges.model;

import java.io.Serializable;
import java.util.List;

import org.jenkinsci.plugins.scriptsecurity.sandbox.whitelists.Whitelisted;

/**
 * This class represents the test results of automation jobs triggered by this plugin

 */

public class TestResults implements Serializable {

    private TriggerServer triggerServer;
    private TriggerType triggerType;
    private String buildStatus;
    private String commitMessage;
    private int numberOfJobsTriggered;
    private List<TriggerTestResult> triggerTestResults;
      
    
    public TestResults(TriggerServer triggerServer, TriggerType triggerType, String buildStatus, String commitMessage, int numberOfJobsTriggered,List<TriggerTestResult> triggerTestResults ) {
    	this.triggerServer = triggerServer;
    	this.triggerType = triggerType;
    	this.buildStatus = buildStatus;
    	this.commitMessage = commitMessage;
    	this.numberOfJobsTriggered = numberOfJobsTriggered;
    	this.triggerTestResults = triggerTestResults;
    
    }
    
    @Whitelisted
    public TriggerServer getTriggerServer() {
        return triggerServer;
    }
    
    @Whitelisted
    public TriggerType getTriggerType() {
        return triggerType;
    }

    @Whitelisted
    public String getBuildStatus() {
        return buildStatus;
    }

    @Whitelisted
    public String getCommitMessage() {
        return commitMessage;
    }
    
    @Whitelisted
    public int getNumberOfJobsTriggered() {
        return numberOfJobsTriggered;
    }

    @Whitelisted
    public List<TriggerTestResult> getTriggerTestResults() {
        return triggerTestResults;
    }


}
