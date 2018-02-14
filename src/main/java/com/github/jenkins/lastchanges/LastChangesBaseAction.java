package com.github.jenkins.lastchanges;

import hudson.model.Action;

public abstract class LastChangesBaseAction implements Action {

    protected static final String BASE_URL = "change-based-job-trigger";

    public String getUrlName() {
        return BASE_URL;
    }

    public String getDisplayName() {
        return "View Results";
    }

    public String getIconFileName() {
        return "/plugin/change-based-job-trigger/git.png";
    }


    protected abstract String getTitle();


}
