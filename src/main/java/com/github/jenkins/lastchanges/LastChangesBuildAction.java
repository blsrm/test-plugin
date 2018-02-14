package com.github.jenkins.lastchanges;

import com.github.jenkins.lastchanges.model.CommitChanges;
import com.github.jenkins.lastchanges.model.CommitInfo;
import com.github.jenkins.lastchanges.model.LastChanges;
import com.github.jenkins.lastchanges.model.LastChangesConfig;
import com.github.jenkins.lastchanges.model.TestResults;

import hudson.model.Action;
import hudson.model.Run;
import jenkins.tasks.SimpleBuildStep;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class LastChangesBuildAction extends LastChangesBaseAction implements SimpleBuildStep.LastBuildAction {

    private final Run<?, ?> build;
    private LastChanges buildChanges;
    private LastChangesConfig config;
    private List<LastChangesProjectAction> projectActions;
    private TestResults testResults;

    public LastChangesBuildAction(Run<?, ?> build, LastChanges lastChanges, LastChangesConfig config,TestResults testResults) {
        this.build = build;
        buildChanges = lastChanges;
        if (config == null) {
            config = new LastChangesConfig();
        }
        this.config = config;
        List<LastChangesProjectAction> projectActions = new ArrayList<>();
        projectActions.add(new LastChangesProjectAction(build.getParent()));
        this.projectActions = projectActions;
        this.testResults = testResults;
    }

    @Override
    protected String getTitle() {
        return "Last Changes of Build #" + this.build.getNumber();
    }

    public LastChanges getBuildChanges() {
        return buildChanges;
    }

    public Run<?, ?> getBuild() {
        return build;
    }

    public LastChangesConfig getConfig() {
        return config;
    }
    
    public TestResults getTestResults() {
        return testResults;
    }
    
    public CommitRenderer getCommit(String commitId) {

        CommitChanges commit = null;
        for (CommitChanges commitChanges : buildChanges.getCommits()) {
            if(commitId.equals(commitChanges.getCommitInfo().getCommitId())) {
                commit = commitChanges;
                break;
            }
        }

        return new CommitRenderer(this, commit);
    }


    @Override
    public Collection<? extends Action> getProjectActions() {
        return this.projectActions;
    }
}
