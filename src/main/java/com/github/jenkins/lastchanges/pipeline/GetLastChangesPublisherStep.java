package com.github.jenkins.lastchanges.pipeline;


import org.jenkinsci.plugins.workflow.steps.AbstractStepDescriptorImpl;
import org.jenkinsci.plugins.workflow.steps.AbstractStepImpl;
import org.jenkinsci.plugins.workflow.steps.AbstractSynchronousStepExecution;
import org.kohsuke.stapler.DataBoundConstructor;

import com.github.jenkins.lastchanges.LastChangesPublisher;
import com.github.jenkins.lastchanges.model.FormatType;
import com.github.jenkins.lastchanges.model.MatchingType;
import com.github.jenkins.lastchanges.model.SinceType;
import com.github.jenkins.lastchanges.model.TriggerServer;
import com.github.jenkins.lastchanges.model.TriggerType;
import com.google.inject.Inject;

import hudson.Extension;


public class GetLastChangesPublisherStep extends AbstractStepImpl {

    private SinceType since;
    private FormatType format;
    private MatchingType matching;
    private Boolean showFiles;
    private Boolean synchronisedScroll;
    private String matchWordsThreshold;
    private String matchingMaxComparisons;
    private String specificRevision;
    private String vcsDir;
    private String specificBuild;
    private boolean triggerQAJob;
    private String remoteJenkinsBaseUrl;
    private String defaultQaJob;
    private TriggerType triggerType;
    private TriggerServer triggerServer;
    private String remoteUsername;
    private String remoteApiToken;
    
    @DataBoundConstructor
    public GetLastChangesPublisherStep(SinceType since,
                                       FormatType format,
                                       MatchingType matching,
                                       Boolean showFiles,
                                       Boolean synchronisedScroll,
                                       String matchWordsThreshold,
                                       String matchingMaxComparisons,
                                       String specificRevision,
                                       String vcsDir,
                                       String specificBuild,
                                       boolean triggerQAJob,
                                       String remoteJenkinsBaseUrl,
                                       String defaultQaJob,
                                       TriggerType triggerType,
                                       TriggerServer triggerServer,
                                       String remoteUsername,
                                       String remoteApiToken) {
        this.since = since;
        this.format = format;
        this.matching = matching;
        this.showFiles = showFiles;
        this.synchronisedScroll = synchronisedScroll;
        this.matchWordsThreshold = matchWordsThreshold;
        this.matchingMaxComparisons = matchingMaxComparisons;
        this.specificRevision = specificRevision;
        this.vcsDir = vcsDir;
        this.specificBuild = specificBuild;
        this.triggerQAJob=triggerQAJob;
        this.remoteJenkinsBaseUrl = remoteJenkinsBaseUrl;
        this.defaultQaJob=defaultQaJob;
        this.triggerType=triggerType;
        this.triggerServer=triggerServer;
        this.remoteUsername=remoteUsername;
        this.remoteApiToken=remoteApiToken;
    }

    public static class Execution extends AbstractSynchronousStepExecution<LastChangesPublisherScript> {

        @Inject(optional = true)
        private transient GetLastChangesPublisherStep step;

        @Override
        protected LastChangesPublisherScript run() throws Exception {

            LastChangesPublisher publisher = new LastChangesPublisher(
                    step.since,
                    step.format,
                    step.matching,
                    step.showFiles,
                    step.synchronisedScroll,
                    step.matchWordsThreshold,
                    step.matchingMaxComparisons,
                    step.specificRevision,
                    step.vcsDir,
                    step.specificBuild,
                    step.triggerQAJob,
                    step.remoteJenkinsBaseUrl,
                    step.defaultQaJob,
                    step.triggerType, 
                    step.triggerServer,
                    step.remoteUsername,
                    step.remoteApiToken);

            return new LastChangesPublisherScript(publisher);
        }
    }

    @Extension
    public static final class DescriptorImpl extends AbstractStepDescriptorImpl {

        public DescriptorImpl() {
            super(Execution.class);
        }

        @Override
        public String getFunctionName() {
            return "getLastChangesPublisher";
        }

        @Override
        public String getDisplayName() {
            return "Get Last Changes Publisher";
        }

    }
}