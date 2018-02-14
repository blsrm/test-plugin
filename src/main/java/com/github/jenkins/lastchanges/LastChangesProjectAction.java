package com.github.jenkins.lastchanges;

import hudson.model.Job;
import hudson.model.ProminentProjectAction;
import hudson.model.Run;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class LastChangesProjectAction extends LastChangesBaseAction implements ProminentProjectAction {

    private final Job<?, ?> job;

    private String jobName;

    public LastChangesProjectAction(Job<?, ?> job) {
        this.job = job;
    }

    public String job() {
        if (jobName == null) {
            jobName = job.getName();
        }
        return jobName;
    }

    public Job<?, ?> getProject() {
        return job;
    }

 
    @Override
    protected String getTitle() {
        return this.job.getDisplayName();
    }

    public List<Run<?, ?>> getLastChangesBuilds() {
        List<Run<?, ?>> builds = new ArrayList<>();
        try{
	        for (Run<?, ?> build : job.getBuilds()) {
	            LastChangesBuildAction action = build.getAction(LastChangesBuildAction.class);
	            if (action != null) {
	                builds.add(build);
	            }
	        }
        } catch(Exception e){
        	System.out.println("Error in retrieving the build details. It will be displayed after the first run");
        }
        return builds;
    }

}