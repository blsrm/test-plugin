/*
 * The MIT License
 *
 * Copyright 2016 rmpestano.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
/*
 * Refer this documentation for help: https://developer.rackspace.com/blog/jenkins-post-build-plugin-part-1/#understanding-the-project-structure
 */
package com.github.jenkins.lastchanges;

import static com.github.jenkins.lastchanges.impl.GitLastChanges.repository;

import java.io.File;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.file.Paths;
import java.text.DateFormat;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.xml.bind.DatatypeConverter;

import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.jenkinsci.Symbol;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.QueryParameter;
import org.tmatesoft.svn.core.auth.ISVNAuthenticationProvider;
import org.tmatesoft.svn.core.wc.SVNRevision;

import com.github.jenkins.lastchanges.filecreation.CodeDifferenceAnalytics;
import com.github.jenkins.lastchanges.impl.GitLastChanges;
import com.github.jenkins.lastchanges.impl.SvnLastChanges;
import com.github.jenkins.lastchanges.model.CommitChanges;
import com.github.jenkins.lastchanges.model.CommitInfo;
import com.github.jenkins.lastchanges.model.FormatType;
import com.github.jenkins.lastchanges.model.LastChanges;
import com.github.jenkins.lastchanges.model.LastChangesConfig;
import com.github.jenkins.lastchanges.model.MatchingType;
import com.github.jenkins.lastchanges.model.SinceType;
import com.github.jenkins.lastchanges.model.TestResults;
import com.github.jenkins.lastchanges.model.TriggerServer;
import com.github.jenkins.lastchanges.model.TriggerTestResult;
import com.github.jenkins.lastchanges.model.TriggerType;
import com.offbytwo.jenkins.JenkinsServer;
import com.offbytwo.jenkins.model.Job;

import hudson.EnvVars;
import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.AbstractProject;
import hudson.model.BuildableItem;
import hudson.model.Computer;
import hudson.model.Item;
import hudson.model.Result;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.scm.SCM;
import hudson.scm.SubversionSCM;
import hudson.slaves.SlaveComputer;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.BuildStepMonitor;
import hudson.tasks.Publisher;
import hudson.tasks.Recorder;
import hudson.util.FormValidation;
import hudson.util.ListBoxModel;
import hudson.util.RunList;
import jenkins.model.Jenkins;
import jenkins.tasks.SimpleBuildStep;
import jenkins.triggers.SCMTriggerItem;

/**
 * @author rmpestano
 */
public class LastChangesPublisher extends Recorder implements SimpleBuildStep {

	private static EnvVars envVars; 
	
	private static Logger LOG = Logger.getLogger(LastChangesPublisher.class.getName());

    private static final String GIT_DIR = ".git";

    private static final String SVN_DIR = ".svn";

    private static final short RECURSION_DEPTH = 50;

    private static final DateFormat DATE_FORMAT = DateFormat.getDateTimeInstance(DateFormat.DEFAULT, DateFormat.DEFAULT);

    private String specificRevision; //revision id to crete the diff

    private String specificBuild; // create the diff with the revision of an specific build

    private SinceType since;//since when you want the get last changes

    private FormatType format;

    private MatchingType matching;

    private String vcsDir;//directory relative to workspace to start searching for the VCS directory (.git or .svn)

    private Boolean showFiles;

    private Boolean synchronisedScroll;

    private String matchWordsThreshold;

    private String matchingMaxComparisons;

    private Repository gitRepository = null;

    private File svnRepository = null;

    private boolean isGit = false;

    private boolean isSvn = false;

    private transient LastChanges lastChanges = null;

    private transient FilePath vcsDirFound = null; //location of vcs directory (.git or .svn) in job workspace (is here for caching purposes)
   
	private String remoteJenkinsBaseUrl;
	
    private String defaultQaJob;    
    
    private TriggerType triggerType;
    
    private TriggerServer triggerServer;
    
    private String remoteUsername;
    
    private String remoteApiToken;

	///QA Automation variables
    
    
    private boolean triggerQAJob;
    public boolean isTriggerQAJob() {
		return triggerQAJob;
	}
    
    @DataBoundSetter
	public void setTriggerQAJob(boolean triggerQAJob) {
		this.triggerQAJob = triggerQAJob;
	}
    
	public String getRemoteJenkinsBaseUrl() {
		return remoteJenkinsBaseUrl;
	}
	
	@DataBoundSetter
	public void setRemoteJenkinsBaseUrl(String remoteJenkinsBaseUrl) {
		this.remoteJenkinsBaseUrl = remoteJenkinsBaseUrl;
	}

	public String getDefaultQaJob() {
		return defaultQaJob;
	}
	
	@DataBoundSetter
	public void setDefaultQaJob(String defaultQaJob) {
		this.defaultQaJob = defaultQaJob;
	}
	    
    public TriggerType getTriggerType() {
		return triggerType;
	}
    
    @DataBoundSetter
	public void setTriggerType(TriggerType triggerType) {
		this.triggerType = triggerType;
	}
    
    public TriggerServer getTriggerServer() {
		return triggerServer;
	}
    
    @DataBoundSetter
	public void setTriggerServer(TriggerServer triggerServer) {
		this.triggerServer = triggerServer;
	}
    
    public String getRemoteUsername() {
		return remoteUsername;
	}
    
    @DataBoundSetter
	public void setRemoteUsername(String remoteUsername) {
		this.remoteUsername = remoteUsername;
	}
    
    public String getRemoteApiToken() {
		return remoteApiToken;
	}
    
    @DataBoundSetter
	public void setRemoteApiToken(String remoteApiToken) {
		this.remoteApiToken = remoteApiToken;
	}
    @DataBoundConstructor
    public LastChangesPublisher(SinceType since, FormatType format, MatchingType matching, Boolean showFiles, Boolean synchronisedScroll, String matchWordsThreshold,
                                String matchingMaxComparisons, String specificRevision, String vcsDir, String specificBuild, 
                                boolean triggerQAJob, String remoteJenkinsBaseUrl, String defaultQaJob, TriggerType triggerType, TriggerServer triggerServer,
                                String remoteUsername, String remoteApiToken) {
        this.specificRevision = specificRevision;
        this.format = format;
        this.since = since;
        this.matching = matching;
        this.showFiles = showFiles;
        this.synchronisedScroll = synchronisedScroll;
        this.matchWordsThreshold = matchWordsThreshold;
        this.matchingMaxComparisons = matchingMaxComparisons;
        this.vcsDir = vcsDir;
        this.specificBuild = specificBuild;
        this.triggerQAJob = triggerQAJob; 
        this.remoteJenkinsBaseUrl = remoteJenkinsBaseUrl;
        this.defaultQaJob = defaultQaJob;
        this.triggerType = triggerType;
        this.triggerServer = triggerServer;
        this.remoteUsername=remoteUsername;
        this.remoteApiToken=remoteApiToken;

    }

    @Override
    public void perform(Run<?, ?> build, FilePath workspace, Launcher launcher, TaskListener listener) throws IOException, InterruptedException {
    	
    	envVars = new EnvVars();
    	envVars = build.getEnvironment(listener);    	
    	List<TriggerTestResult> triggerTestResults = new ArrayList<TriggerTestResult>(); // For dashboard
    	int executionCount = 0;
    	
    	boolean buildShouldFail = false;   // to fail the build forcibly if no jobs triggered by this plugin. this decision might change based on the discussion
    	   	
        LastChangesProjectAction projectAction = new LastChangesProjectAction(build.getParent());
        ISVNAuthenticationProvider svnAuthProvider = null;
        FilePath workspaceTargetDir = getMasterWorkspaceDir(build);//always on master
        FilePath vcsDirParam = null; //folder to be used as param on vcs directory search
        FilePath vcsTargetDir = null; //directory on master workspace containing a copy of vcsDir (.git or .svn)

        if (this.vcsDir != null && !"".equals(vcsDir.trim())) {
            vcsDirParam = new FilePath(workspace, this.vcsDir);
        } else {
            vcsDirParam = workspace;
        }

        if (findVCSDir(vcsDirParam, GIT_DIR)) {
            isGit = true;
            // workspace can be on slave so copy resources to master
            vcsTargetDir = new FilePath(new File(workspaceTargetDir.getRemote() + "/.git"));
            vcsDirFound.copyRecursiveTo("**/*", vcsTargetDir);
            gitRepository = repository(workspaceTargetDir.getRemote() + "/.git");
        } else if (findVCSDir(vcsDirParam, SVN_DIR)) {
            isSvn = true;

            SubversionSCM scm = null;
            try {
                Collection<? extends SCM> scMs = SCMTriggerItem.SCMTriggerItems.asSCMTriggerItem(projectAction.getProject()).getSCMs();
                scm = (SubversionSCM) scMs.iterator().next();
                svnAuthProvider = scm.createAuthenticationProvider(build.getParent(), scm.getLocations()[0]);

            } catch (NoSuchMethodError e) {
                if (scm != null) {
                    svnAuthProvider = scm.getDescriptor().createAuthenticationProvider();
                }

            } catch (Exception ex) {
            }

            vcsTargetDir = new FilePath(new File(workspaceTargetDir.getRemote() + "/.svn"));
            vcsDirFound.copyRecursiveTo("**/*", vcsTargetDir);
            svnRepository = new File(workspaceTargetDir.getRemote());

        }

        if (!isGit && !isSvn) {
            throw new RuntimeException(String.format("Git or Svn directories not found in workspace %s.", vcsDirParam.toURI().toString()));
        }


        boolean hasTargetRevision = false;
        String targetRevision = null;
        String targetBuild = null;

        final EnvVars env = build.getEnvironment(listener);
        if (specificRevision != null && !"".equals(specificRevision)) {
            targetRevision = env.expand(specificRevision);
        }

        boolean hasSpecificRevision = targetRevision != null && !"".equals(targetRevision.trim());
        //only look into builds revision if no specific revision is provided (specificRevision has higher priority over build revision)
        if (!hasSpecificRevision && (specificBuild != null && !"".equals(specificBuild))) {
            targetBuild = env.expand(specificBuild);
            targetRevision = findBuildRevision(targetBuild, projectAction.getProject().getBuilds());
            hasSpecificRevision = targetRevision != null && !"".equals(targetRevision.trim());
        }

        listener.getLogger().println("\n\nChange Based Job Trigger Plugin is executed...");
        listener.getLogger().println("\nPublishing build last changes...");

        //only look at 'since' parameter when specific revision is NOT set
        if (since != null && !hasSpecificRevision) {

            switch (since) {

                case LAST_SUCCESSFUL_BUILD: {
                    boolean hasSuccessfulBuild = projectAction.getProject().getLastSuccessfulBuild() != null;
                    if (hasSuccessfulBuild) {
                        LastChangesBuildAction action = projectAction.getProject().getLastSuccessfulBuild().getAction(LastChangesBuildAction.class);
                        if (action != null && action.getBuildChanges().getCurrentRevision() != null) {
                            targetRevision = action.getBuildChanges().getCurrentRevision().getCommitId();
                        }
                    } else {
                        listener.error("No successful build found, last changes will use previous revision.");
                    }
                    break;
                }

                case LAST_TAG: {

                    try {
                        if (isGit) {
                            ObjectId lastTagRevision = GitLastChanges.getInstance().getLastTagRevision(gitRepository);
                            if (lastTagRevision != null) {
                                targetRevision = lastTagRevision.name();
                            }
                        } else if (isSvn) {
                            SVNRevision lastTagRevision = getSvnLastChanges(svnAuthProvider).getLastTagRevision(svnRepository);
                            if (lastTagRevision != null) {
                                targetRevision = lastTagRevision.toString();
                            }
                        }
                    } catch (Exception e) {
                        LOG.log(Level.WARNING, "Could not resolve last tag revision, last changes will use previous revision.",e);
                        listener.error("Could not resolve last tag revision, last changes will use previous revision.");
                    }
                    break;
                }              

                
            }

        }

        hasTargetRevision = targetRevision != null && !"".equals(targetRevision);

        try {
            if (isGit) {
                if (hasTargetRevision) {
                    //compares current repository revision with provided revision
                    lastChanges = GitLastChanges.getInstance().changesOf(gitRepository, GitLastChanges.getInstance().resolveCurrentRevision(gitRepository), gitRepository.resolve(targetRevision));
                    List<CommitInfo> commitInfoList = getCommitsBetweenRevisions(lastChanges.getCurrentRevision().getCommitId(), targetRevision, null);
                    lastChanges.addCommits(commitChanges(commitInfoList, lastChanges.getPreviousRevision().getCommitId(),null));
                } else {
                    //compares current repository revision with previous one
                    lastChanges = GitLastChanges.getInstance().changesOf(gitRepository);
                    lastChanges.addCommit(new CommitChanges(lastChanges.getCurrentRevision(),lastChanges.getDiff()));
                }
                
                /*
                 *  Custom code included to get class differences and trigger QA automation based on code change - Implemented only for GIT SCM
                 */
                
                listener.getLogger().println("Comparing the current build with previous version...");                    
                CodeDifferenceAnalytics codeDifference = new CodeDifferenceAnalytics(gitRepository,workspaceTargetDir,lastChanges.getDiff(),listener);
                
                if(codeDifference.getClassDifferences() == null){
                	listener.getLogger().println("No Class modified in this build and Hence no automation jobs are triggered");
                }
                else{
                	listener.getLogger().println("Class that are changed in the current build are: \n "+codeDifference.getClassDifferences()+"\n");
                	Set<String> controllerNames = codeDifference.getControllerFileNames();
                	String commitMessage = lastChanges.getCurrentRevision().getCommitMessage();
//                	triggerAutomationJob.getResourcePath(); // Currently not implemented to trigger QA jobs based resource path changes
                    listener.getLogger().println("\nCommit Message from the current build: \n"+commitMessage);
                    
                    /*
                     *  Here we go with QA Job trigger part 
                     */
                    
                    boolean defaultQaJob = false;                                   
                    
                    // Logging QA Automation Job configuration saved and to be used for this build
                    listener.getLogger().println("QA Automation - Execution of the plugin as below:");
                    listener.getLogger().println("Configuration to be used for this build are");
                    if(this.triggerQAJob) {listener.getLogger().println(" -> QA Trigger Job flag is enabled for this build");} else {listener.getLogger().println(" -> QA Trigger Job flag is disable for this build");}
                	if(this.triggerType==TriggerType.COMMIT_MESSAGE) listener.getLogger().println(" -> Job will be triggered based on Commit message");
                	if(this.triggerType==TriggerType.CONTROLLER_FILE) listener.getLogger().println(" -> Job will be triggered based on Controller File");
                	if(this.triggerServer==TriggerServer.SAME_SERVER) listener.getLogger().println(" -> Same Server Jenkins will be used for the execution");
                	if(this.triggerServer==TriggerServer.REMOTE_SERVER)listener.getLogger().println(" -> Remote Server Jenkins will be used for the execution");                	
                	if(this.defaultQaJob.isEmpty()){ listener.getLogger().println(" -> Default QA Job is NOT present");} else{listener.getLogger().println(" -> Default QA Job is present -> "+this.defaultQaJob); defaultQaJob = true; }
                
                	// Flow of execution is below
		                /*Check triggerQAJob
		                	-> Yes 
		                		Check triggerType
		                			-> Commit Message
		                				-> Check triggerServer
		                					-> Same Server - Call SameJenkinsServer.java				
		                					-> Remote Server - Call RemoteJenkinsServer.java - use remote details(url,username,apitoken)
		                			
		                			-> Controller File				
		                				-> Check triggerServer
		                					-> Same Server - Call SameJenkinsServer.java				
		                					-> Remote Server - Call RemoteJenkinsServer.java - use remote details(url,username,apitoken)             	
		                	-> No 
		                		-> Message("No QA Job is triggered as the QA Trigger Job flag is disabled")                	 
	                	 */
                	
                	if(this.triggerQAJob){
                		
                		switch(this.triggerType){
                		
                		/* SameServerJenkins and RemoteServerJenkins implements JenkinServerInterface
                		 * Instantiate object of SameServerJenkins and RemoteServerJenkins class based on the trigger server and method calling applies same to both since both share same interface
                		 * By using interface, the code has been shortened i.e method invocation is common to both
                		 */
                		
	                		case COMMIT_MESSAGE: {
	                			
	                			JenkinsServerInterface jenkinsServer = null;
	                			
	                			switch(this.triggerServer){
	                				case SAME_SERVER: {
	                					jenkinsServer = new SameJenkinsServer(listener);				
	                					break;
	                				}	                				
	                				case REMOTE_SERVER: {	                					
	                					jenkinsServer = new RemoteJenkinsServer(this.remoteJenkinsBaseUrl,this.remoteUsername,this.remoteApiToken,listener);
	                					listener.getLogger().println("\nChecking Remote Jenkins connectivity before execution");
	                		        	if(jenkinsServer.checkJenkinsConnectivity()){
	                		        		listener.getLogger().println("connection is successfull"); //connection established
	                		        	}else{
	                		        		listener.error("connection to remote jenkins is not successfull and hence QA Automation Execution is not performed"+" Remote Jenkins URL is "+this.remoteJenkinsBaseUrl); //connection established
	                		        		buildShouldFail = true;
	                		        		return;
	                		        	}
	                					break;
	                				}
	                			}
	                		        		
	                			Set<String> jenkinsJobList = jenkinsServer.getJobList(); //Get the jenkins job list
	                			
	                			int i = 0; //Execution count
	                			
	                			//All the job triggering part goes here
	                			listener.getLogger().println("\nFinding the jobs in Jenkins server matching the commit message in this build: "+commitMessage);
	                			Set<String> jobNamesMatched = getMatchedJobName(jenkinsJobList,commitMessage);
	                			if(jobNamesMatched.size() == 0 ){
	                				listener.error("No matching Jobs found in Jenkins matching the commit message: "+commitMessage);
	                				if(defaultQaJob){ 
	                					jobNamesMatched.add(this.defaultQaJob);
	                					listener.getLogger().println("Hence Default Job will be used for triggering the Automation for this build change");                                       		
	                					listener.getLogger().println("\nExecuting the Default QA Job: "+this.defaultQaJob);
	                					jenkinsServer.run(this.defaultQaJob);
	                					listener.getLogger().println("Jenkins Status of the QA job is "+jenkinsServer.getFinalBuildStatus());
	                					if(!jenkinsServer.getFinalBuildStatus().equalsIgnoreCase("SUCCESS")) buildShouldFail = true;
	                					//Dashboard
	                					TriggerTestResult triggerTestResult1 = new TriggerTestResult(commitMessage,"Not Available","","","No Automation Job present for this Commit Message");
	                					triggerTestResults.add(triggerTestResult1);                                        		
	                					TriggerTestResult triggerTestResult2 = new TriggerTestResult("",this.defaultQaJob,jenkinsServer.getBuildUrl(),jenkinsServer.getFinalBuildStatus(),"Default Job triggered since No Automation found for the Commit Message: "+commitMessage);
	                					triggerTestResults.add(triggerTestResult2);                  		
	                					i++;	                                			
	                				} 
	                				else{
	                					listener.error("No Default Job is present in this build pipleline to trigger in the abense of job available for given Commit Message");
	                					listener.error("Hence QA Automation is not triggered in this build pipline and the build will be failed for this reason");
	                					buildShouldFail = true;
	                					TriggerTestResult triggerTestResult1 = new TriggerTestResult(commitMessage,"Not Available","","","No Automation Job present for this Commit Message");
	                					triggerTestResults.add(triggerTestResult1);                                        		
	                					TriggerTestResult triggerTestResult2 = new TriggerTestResult("",this.defaultQaJob,jenkinsServer.getBuildUrl(),jenkinsServer.getFinalBuildStatus(),"Default Job triggered since No Automation found for the Commit Message: "+commitMessage);
	                					triggerTestResults.add(triggerTestResult2);                         		
	                					return;
	                					//here we are logging triggerResult object since Dashboard will automatically display no job triggered when the count of triggerresult object is zero
	                				}
	                			}else{
	                				listener.getLogger().println("QA Automation Job names found matching the commit message: "+commitMessage+" and the Job Names are "+jobNamesMatched);
	                				listener.getLogger().println("Starting the execution of matched Automation jobs for the commit message: "+commitMessage);
	                			
	                				//Execute the automation jobs for the commit messge match
	                				for(String jobName : jobNamesMatched){                                  		
	                					listener.getLogger().println("\nExecuting the QA Automation job# "+(i+1)+" : "+jobName+"....");
	                					jenkinsServer.run(jobName);
	                					listener.getLogger().println("Jenkins Status of the QA job is "+jenkinsServer.getFinalBuildStatus());  
	                					if(!jenkinsServer.getFinalBuildStatus().equalsIgnoreCase("SUCCESS")) buildShouldFail = true;
	                					//Dashboard
	                					TriggerTestResult triggerTestResult = new TriggerTestResult(commitMessage,jobName,jenkinsServer.getBuildUrl(),jenkinsServer.getFinalBuildStatus(),"");
	                					triggerTestResults.add(triggerTestResult);                        		
	                					i++;
	                				}               			
	                			}	        
	                			
	                			executionCount= i;
	                			break;
	
	                		}	                		
                		
                			case CONTROLLER_FILE: {
                				JenkinsServerInterface jenkinsServer = null;
                        		switch(this.triggerServer){                        			
	                    			case SAME_SERVER: {
	                    				jenkinsServer = new SameJenkinsServer(listener);
	                    				break;
	                    			}	                    			
	                    			case REMOTE_SERVER: {	                    				
	                    				jenkinsServer = new RemoteJenkinsServer(this.remoteJenkinsBaseUrl,this.remoteUsername,this.remoteApiToken,listener);
	                            		listener.getLogger().println("\nChecking Jenkins connectivity before execution");
	                            	  	if(jenkinsServer.checkJenkinsConnectivity()){
	                            	  		listener.getLogger().println("connection is successfull"); //connection established
	                            	  	}else{
	                            	  		listener.error("connection to remote jenkins is not successfull and hence QA Automation Execution is not performed"+" Jenkins Remote URL is "+this.remoteJenkinsBaseUrl); //connection established
	                            	  		buildShouldFail = true;
	                            	  		return;
	                            	  	}
	                    				break;
	                    			}	
                        		}

                        		Set<String> jenkinsJobList = jenkinsServer.getJobList(); // get the jenkins job list

                        	  	int i =0; //execution count
                        	  	
                        	  	if(controllerNames == null || controllerNames.size() == 0){ //if not controller files present, the default job will be triggered later in the code
                        	  		listener.getLogger().println("\nController files are not existing for the code that is changed in this build. Perhaps, it is not MVC framework");                                    		  

                        	  	}else{                                        	                                        	
                        	      	for(String controllerName : controllerNames){      //if controller file is present                            		
                        	      		listener.getLogger().println("\nFinding the jobs in Jenkins server for the Controller file changed: "+controllerName);
                        	      		
                        	      		Set<String> jobNamesMatched = getMatchedJobName(jenkinsJobList,controllerName);                       		
                        	      		
                        	          	//All the job triggering part goes here
                        	          	if(jobNamesMatched.size() == 0 ){
                        	          		listener.error("No matching Jobs found in Remote Jenkins URL: "+this.remoteJenkinsBaseUrl+" matching the controller file name: "+controllerName);
                        	          		//Dashboard
                        	          		TriggerTestResult triggerTestResult = new TriggerTestResult(controllerName,"Not Available","","","No Automation Job present for this controller file");
                        	          		triggerTestResults.add(triggerTestResult);                                                		

                        	          		continue;
                        	          	}else{        	                                		
                        	          		listener.getLogger().println("QA Automation Job name found matching the controller file: "+controllerName+" and the Job Names are "+jobNamesMatched);
                        	              	listener.getLogger().println("Starting the execution of matched Automation jobs for the controller file: "+controllerName);
                        	              	//Execute the automation jobs for the controller file impacted
                        	              	for(String jobName : jobNamesMatched){                             		
                        	              		
                        	              		listener.getLogger().println("\nExecuting the QA Automation job# "+(i+1)+" : "+jobName+"....");
                        	              		jenkinsServer.run(jobName);
                        	              		listener.getLogger().println("Jenkins Status of the QA job is "+jenkinsServer.getFinalBuildStatus());   
                        	              		if(!jenkinsServer.getFinalBuildStatus().equalsIgnoreCase("SUCCESS")) buildShouldFail = true;
                        	              		//Dashboard
                        	              		TriggerTestResult triggerTestResult = new TriggerTestResult(controllerName,jobName,jenkinsServer.getBuildUrl(),jenkinsServer.getFinalBuildStatus(),"");
                        	              		triggerTestResults.add(triggerTestResult);                                                		
                        	              		i++;
                        	                  }               			

                        	          		
                        	          	}
                        	      	}
                        	      	
                        	      	//If no job match found, check if default job present to trigger. This section is present at end of this CASE statement unlike Commit message. 
                        	      	//Because in commit message execution, you can find the need to trigger Default job very early 
                        	      	if(i ==0 ){
                        	      		if(defaultQaJob){    	                                			
                        	      			listener.getLogger().println("Default Job will triggered for the QA in this build since No matched jobs found for any of the controller file changes");                                          		
                        	          		listener.getLogger().println("\nExecuting the Default QA Job: "+this.defaultQaJob);
                        	          		jenkinsServer.run(this.defaultQaJob);
                        	          		listener.getLogger().println("Jenkins Status of the QA job is "+jenkinsServer.getFinalBuildStatus());
                        	          		if(!jenkinsServer.getFinalBuildStatus().equalsIgnoreCase("SUCCESS")) buildShouldFail = true;
                        	          		//Dashboard
                        	          		TriggerTestResult triggerTestResult = new TriggerTestResult("",this.defaultQaJob,jenkinsServer.getBuildUrl(),jenkinsServer.getFinalBuildStatus(),"Default Job triggered since No Automation found for this controller file");
                        	          		triggerTestResults.add(triggerTestResult);                             		
                        	          		i++;
                        	      		} 
                        	      		else{
                        	      			listener.error("No matched jobs found in Jenkins for any of the controller file changes and also No Default Job is present in this build pipeline to trigger ");
                        	      			listener.error("Hence QA Automation is not triggered in this build pipline and the build will be failed for this reason");
                        	      			buildShouldFail = true;
                        	      			return;
                        	      			//here we are logging triggerResult object since Dashboard will automatically display no job triggered when the count of triggerresult object is zero
                        	      		}    	                                			
                        	      	}          			
                        	  	}
                        	 	executionCount = i;        	
                        		break;
                			}
						default:
							listener.getLogger().println("Trigger Type should be either CONTROLLE_FILE or COMMIT_MESSAGE but found different one"); //this would not execute since it is drop down value
							break;
                		}
                	} else{
                		listener.getLogger().println("No QA Job is triggered as the QA Trigger Job flag is disabled");
                	}
                	
                	
                }
                
//                listener.getLogger().println("Branch Name: \n"+ new Git(gitRepository).branchList().call().get(0).getName()); 
//                listener.getLogger().println("Repository Name: \n"+workspaceTargetDir);

            } else if (isSvn) {
                SvnLastChanges svnLastChanges = getSvnLastChanges(svnAuthProvider);
                if (hasTargetRevision) {
                    //compares current repository revision with provided revision
                    Long svnRevision = Long.parseLong(targetRevision);
                    lastChanges = svnLastChanges.changesOf(svnRepository, SVNRevision.HEAD, SVNRevision.create(svnRevision));
                    List<CommitInfo> commitInfoList = getCommitsBetweenRevisions(lastChanges.getCurrentRevision().getCommitId(), targetRevision, svnAuthProvider);
                    lastChanges.addCommits(commitChanges(commitInfoList, lastChanges.getPreviousRevision().getCommitId(),svnAuthProvider));
                } else {
                    //compares current repository revision with previous one
                    lastChanges = svnLastChanges.changesOf(svnRepository);
                    //in this case there will be only one commit
                    lastChanges.addCommit(new CommitChanges(lastChanges.getCurrentRevision(),lastChanges.getDiff()));
                }
                
                // Code based Automation trigger is not implemented for SVN
            }

            String resultMessage = String.format("Last changes from revision %s to %s published successfully!", truncate(lastChanges.getCurrentRevision().getCommitId(), 8), truncate(lastChanges.getPreviousRevision().getCommitId(), 8));
            listener.hyperlink("../" + build.getNumber() + "/" + LastChangesBaseAction.BASE_URL, resultMessage);
            listener.getLogger().println("");            
            String buildStatus = "SUCCESS";
            if(buildShouldFail) buildStatus = "FAILURE";
            TestResults testResults = new TestResults(this.triggerServer,this.triggerType,buildStatus,lastChanges.getCurrentRevision().getCommitMessage(),executionCount,triggerTestResults);
            build.addAction(new LastChangesBuildAction(build, lastChanges,
                    new LastChangesConfig(since, specificRevision, format, matching, showFiles, synchronisedScroll, matchWordsThreshold, matchingMaxComparisons),testResults));
        } catch (Exception e) {
            listener.error("QA Automation Jobs are not triggered and Build last Changes NOT published to the dashboard due to the following error: " + (e.getMessage() == null ? e.toString() : e.getMessage()) + (e.getCause() != null ? " - " + e.getCause() : ""));            
            listener.error(e.getMessage());
            LOG.log(Level.SEVERE, "Could not trigger QA Jobs and publish build LastChanges.",e);
        } finally {
            if (vcsTargetDir != null && vcsTargetDir.exists()) {
                vcsTargetDir.deleteRecursive();//delete copied dir on master
            }
        }

        // fail only if the plugin does not find a match for the job to trigger and no default job too.
        if(buildShouldFail){
        	build.setResult(Result.FAILURE);
        }
        else{
        build.setResult(Result.SUCCESS);
        }

    }
    
    /**
     * @return 
     * @returns the matches job from the job list
     */
    
    public Set<String> getMatchedJobName (Set<String> jobList, String jobNamePattern) {
    	
    	Set<String> matchedJobs = new HashSet<String>();
    	
    	/*
    	 *  Splitting by space and taking the first value of first index. This will take care of grabbing the JIRA Key from commit message as well.
    	 *  As per JIRA Github integration, developer should place the JIRA Key as the first word in the commit message !!!! Very important
    	 */
    	try{
	    	String newJobNamePattern = jobNamePattern.split("\\s+")[0];
	    	
	    	//Assuming QA would name their name after ControllerName followed Regression
	    	//Currently looking for below three patterns. This might go increase based on the QA requirement
	    	String stringPattern =  ".*"+newJobNamePattern+".*Regression.*";
	    	String stringPattern2 =  ".*"+newJobNamePattern+".*"; 
	    	Pattern pattern = Pattern.compile(stringPattern,Pattern.CASE_INSENSITIVE);
	    	Pattern pattern2 = Pattern.compile(stringPattern2,Pattern.CASE_INSENSITIVE);
	    	
	    	for(String jobName : jobList) {
	    		//Do the best pattern match
	    		Matcher matcher = pattern.matcher(jobName);
	    		if(matcher.matches()==true){
	    			matchedJobs.add(jobName);
	    		}else{
	    			//Else do the minum patter match
	    			Matcher matcher2 = pattern2.matcher(jobName);
	    			if(matcher2.matches()==true){
		    			matchedJobs.add(jobName);
		    		}
	    		}
	    	}
    	}catch(Exception e){
    		//Not catching it.. Anyways the matchedJobs would be returned as zero and action would be taken accordingly
    	}

    	return matchedJobs;
    }
    
    /**
     * @return gets the LastChanges from current publisher
     */
    public LastChanges getLastChanges() {
        return lastChanges;
    }

    /**
     *
     * Gets the commit changes of each commitInfo
     * First we sort commits by date and then call lastChanges of each commit with previous one
     *
     * @param commitInfoList list of commits between current and previous revision
     *
     * @param oldestCommit is the first commit from previous tree (in git)/revision(in svn) see {@link LastChanges}
     * @param svnAuthProvider
     * @return
     */
    private List<CommitChanges> commitChanges(List<CommitInfo> commitInfoList, String oldestCommit, ISVNAuthenticationProvider svnAuthProvider) {
        if(commitInfoList == null || commitInfoList.isEmpty()) {
            return null;
        }

        List<CommitChanges> commitChanges = new ArrayList<>();

        try {
            Collections.sort(commitInfoList, new Comparator<CommitInfo>() {
                @Override
                public int compare(CommitInfo c1, CommitInfo c2) {
                    try {
                        return DATE_FORMAT.parse(c1.getCommitDate()).compareTo(DATE_FORMAT.parse(c2.getCommitDate()));
                    } catch (ParseException e) {
                        LOG.severe(String.format("Could not parse commit dates %s and %s ", c1.getCommitDate(), c2.getCommitDate()));
                        return 0;
                    }
                }
            });

            for (int i = commitInfoList.size() - 1; i >= 0; i--) {
                LastChanges lastChanges = null;
                if(isGit) {
                    ObjectId previousCommit = gitRepository.resolve(commitInfoList.get(i).getCommitId()+"^1");
                    lastChanges = GitLastChanges.getInstance().
                            changesOf(gitRepository, gitRepository.resolve(commitInfoList.get(i).getCommitId()), previousCommit);
                } else {
                    if (i == 0) { //here we can't compare with (i -1) so we compare with first commit of oldest commit (retrieved in main diff)
                        //here we have the older commit from current tree (see LastChanges.java) which diff must be compared with oldestCommit which is currentRevision from previous tree
                        lastChanges =   SvnLastChanges.getInstance(svnAuthProvider)
                                .changesOf(svnRepository, SVNRevision.parse(commitInfoList.get(i).getCommitId()), SVNRevision.parse(oldestCommit));
                    } else { //get changes comparing current commit (i) with previous one (i -1)
                        lastChanges = SvnLastChanges.getInstance(svnAuthProvider)
                                .changesOf(svnRepository, SVNRevision.parse(commitInfoList.get(i).getCommitId()), SVNRevision.parse(commitInfoList.get(i-1).getCommitId()));
                    }
                }
                String diff = lastChanges != null ? lastChanges.getDiff() : "";
                commitChanges.add(new CommitChanges(commitInfoList.get(i), diff));
            }

        }catch (Exception e) {
            LOG.log(Level.SEVERE,"Could not get commit changes.",e);
        }

        return commitChanges;
    }

    private static String findBuildRevision(String targetBuild, RunList<?> builds) {

        if (builds == null || builds.isEmpty()) {
            return null;
        }

        Integer buildParam = null;
        try {
            buildParam = Integer.parseInt(targetBuild);
        } catch (NumberFormatException ne) {

        }
        if (buildParam == null) {
            throw new RuntimeException(String.format("%s is an invalid build number for 'specificBuild' param. It must resolve to an integer.", targetBuild));
        }
        LastChangesBuildAction actionFound = null;
        for (Run build : builds) {
            if (build.getNumber() == buildParam) {
                actionFound = build.getAction(LastChangesBuildAction.class);
                break;
            }
        }

        if (actionFound == null) {
            throw new RuntimeException(String.format("No build found with number %s. Maybe the build was discarded or not has published LastChanges.", buildParam));
        }

        return actionFound.getBuildChanges().getCurrentRevision().getCommitId();


    }

    /**
     * Retrieve commits between two revisions
     *
     * @param currentRevision
     * @param previousRevision
     */
    private List<CommitInfo> getCommitsBetweenRevisions(String currentRevision, String previousRevision, ISVNAuthenticationProvider svnAuthProvider) throws IOException {
        List<CommitInfo> commits = new ArrayList<>();
        if (isGit) {
            commits = GitLastChanges.getInstance().getCommitsBetweenRevisions(gitRepository, gitRepository.resolve(currentRevision),
                    gitRepository.resolve(previousRevision));
        } else if (isSvn) {
            commits = SvnLastChanges.getInstance(svnAuthProvider).getCommitsBetweenRevisions(svnRepository, SVNRevision.create(Long.parseLong(currentRevision)),
                    SVNRevision.create(Long.parseLong(previousRevision)));
        }

        return commits;
    }


    private boolean isSlave() {
        return Computer.currentComputer() instanceof SlaveComputer;
    }

    private SvnLastChanges getSvnLastChanges(ISVNAuthenticationProvider svnAuthProvider) {
        return svnAuthProvider != null ? SvnLastChanges.getInstance(svnAuthProvider) : SvnLastChanges.getInstance();
    }

    private String truncate(String value, int length) {
        if (value == null || value.length() <= length) {
            return value;
        }

        return value.substring(0, length - 1);
    }

    /**
     * .git directory can be on a workspace sub dir, see JENKINS-36971
     *
     * @return boolean indicating weather the vcs directory was found or not
     */
    private boolean findVCSDir(FilePath workspace, String dir) throws IOException, InterruptedException {
        FilePath vcsDir = null;
        if (workspace.child(dir).exists()) {
            vcsDirFound = workspace.child(dir);
            return true;
        }
        int recursionDepth = RECURSION_DEPTH;
        while ((vcsDir = findVCSDirInSubDirectories(workspace, dir)) == null && recursionDepth > 0) {
            recursionDepth--;
        }
        if (vcsDir == null) {
            return false;
        } else {
            vcsDirFound = vcsDir; //vcs directory  gitDir;
            return true;
        }
    }

    private FilePath findVCSDirInSubDirectories(FilePath sourceDir, String dir) throws IOException, InterruptedException {
        List<FilePath> filePaths = sourceDir.listDirectories();
        if (filePaths == null || filePaths.isEmpty()) {
            return null;
        }

        for (FilePath filePath : sourceDir.listDirectories()) {
            if (filePath.getName().equalsIgnoreCase(dir)) {
                return filePath;
            } else {
                return findVCSDirInSubDirectories(filePath, dir);
            }
        }
        return null;
    }

    /**
     * mainly for findbugs be happy
     *
     * @param build
     * @return
     */
    private FilePath getMasterWorkspaceDir(Run<?, ?> build) {
        if (build != null && build.getRootDir() != null) {
            return new FilePath(build.getRootDir());
        } else {
            return new FilePath(Paths.get("").toFile());
        }
    }

    @Override
    public BuildStepMonitor getRequiredMonitorService() {
        return BuildStepMonitor.NONE;
    }


    @Extension
    @Symbol("lastChanges")
    public static class DescriptorImpl extends BuildStepDescriptor<Publisher> {

        private List<Run<?, ?>> builds;

        public boolean isApplicable(Class<? extends AbstractProject> aClass) {
            // Indicates that this builder can be used with all kinds of project
            // types
            return true;
        }

        /**
         * This human readable name is used in the configuration screen.
         */
        public String getDisplayName() {
            return "Change Based Job Trigger";
        }

        @Restricted(NoExternalUse.class) // Only for UI calls
        public ListBoxModel doFillFormatItems() {
            ListBoxModel items = new ListBoxModel();
            for (FormatType formatType : FormatType.values()) {
                items.add(formatType.getFormat(), formatType.name());
            }
            return items;
        }

        @Restricted(NoExternalUse.class) // Only for UI calls
        public ListBoxModel doFillMatchingItems() {
            ListBoxModel items = new ListBoxModel();
            for (MatchingType matchingType : MatchingType.values()) {
                items.add(matchingType.getMatching(), matchingType.name());
            }
            return items;
        }

        @Restricted(NoExternalUse.class) // Only for UI calls
        public ListBoxModel doFillSinceItems() {
            ListBoxModel items = new ListBoxModel();
            for (SinceType sinceType : SinceType.values()) {
                items.add(sinceType.getName(), sinceType.name());
            }
            return items;
        }

        @Restricted(NoExternalUse.class) // Only for UI calls
        public ListBoxModel doFillTriggerTypeItems() {
            ListBoxModel items = new ListBoxModel();
            for (TriggerType triggerType : TriggerType.values()) {
                items.add(triggerType.getName(), triggerType.name());
            }
            return items;
        }
        
        @Restricted(NoExternalUse.class) // Only for UI calls
        public ListBoxModel doFillTriggerServerItems() {
            ListBoxModel items = new ListBoxModel();
            for (TriggerServer triggerServer : TriggerServer.values()) {
                items.add(triggerServer.getName(), triggerServer.name());
            }
            return items;
        }


        public FormValidation doCheckSpecificBuild(@QueryParameter String specificBuild, @AncestorInPath AbstractProject project) {
            if (specificBuild == null || "".equals(specificBuild.trim())) {
                return FormValidation.ok();
            }
            boolean isOk = false;
            try {
                if (project.isParameterized() && specificBuild.contains("$")) {
                    return FormValidation.ok();//skip validation for parametrized build number as we don't have the parameter value
                }
                Integer.parseInt(specificBuild);
                findBuildRevision(specificBuild, project.getBuilds());
                isOk = true;
            } catch (NumberFormatException e) {
                return FormValidation.error("Build number is invalid, it must resolve to an Integer.");
            } catch (Exception e) {

            }

            if (isOk) {
                return FormValidation.ok();
            } else {
                return FormValidation.error(String.format("Build #%s is invalid or does not exists anymore or not has published LastChanges.", specificBuild));
            }
        }


    
    public FormValidation doValidateAddress(@QueryParameter("remoteJenkinsBaseUrl") final String remoteAddress,@QueryParameter("remoteUsername") final String username,@QueryParameter("remoteApiToken") final String apitoken) {
    	URL host = null;
    	// check if we have a valid, well-formed URL
        try {
            host = new URL(remoteAddress);
            URI uri = host.toURI();
        } catch (Exception e) {
            return FormValidation.error("Oops... Malformed address (" + remoteAddress + "), please double-check it.");
        }
        
	     String authStr = username +":"+  apitoken;

	     String encoding = null;
		try {
			encoding = DatatypeConverter.printBase64Binary(authStr.getBytes("utf-8"));
		} catch (UnsupportedEncodingException e) {			
			return FormValidation.warning("Sorry, Error in encoding the username and token. Please reach to admin");
		}

        // check that the host is reachable
        try {
            HttpURLConnection connection = (HttpURLConnection) host.openConnection();
            connection.setConnectTimeout(5000);
            connection.setRequestProperty("Authorization", "Basic " + encoding);
            connection.connect();
        } catch (Exception e) {
            return FormValidation.warning("Sorry, Unable to hit this URL. Please verify");
        }
        
        return FormValidation.okWithMarkup("Great!!! Address looks good");
    	
    }
    
    public FormValidation doValidateJob(@QueryParameter("remoteJenkinsBaseUrl") String remoteAddress, @QueryParameter("remoteUsername") final String username,@QueryParameter("remoteApiToken") final String apitoken, @QueryParameter("defaultQaJob") String job) {    	
    	
    	if(remoteAddress.isEmpty() || remoteAddress ==null){
	    	try{
	    		Jenkins jenkins = Jenkins.getInstance();    	
		    	Item item = jenkins.getItem(job);	    	
		    	        if(item instanceof BuildableItem){	    	        	
//		    	        	((BuildableItem) item).scheduleBuild();
		    	        	return FormValidation.okWithMarkup("Great!! Job name exists");
		    	        }else{
		    	        	return FormValidation.warning("Oops.. Job name does not exists");
		    	       }    	    	
		    	}catch(Exception e){
		    		return FormValidation.warning("Oops.. Job name does not exists");
	    		}
    	}
    	else {
	        	try {        	
	            	JenkinsServer jenkins = new JenkinsServer(new URI(remoteAddress),username,apitoken);
	        		Job s = jenkins.getJobs().get(job);;
	        		return FormValidation.okWithMarkup("Job name exists");
	        	}catch(Exception e){
	        		return FormValidation.warning("Job name does not exists");
	        	}
	    		
    		}
    	}
    }
    public SinceType getSince() {
        return since;
    }

    public String getSpecificRevision() {
        return specificRevision;
    }

    public FormatType getFormat() {
        return format;
    }

    public MatchingType getMatching() {
        return matching;
    }

    public String getMatchWordsThreshold() {
        return matchWordsThreshold;
    }

    public String getMatchingMaxComparisons() {
        return matchingMaxComparisons;
    }

    public Boolean getShowFiles() {
        return showFiles;
    }

    public Boolean getSynchronisedScroll() {
        return synchronisedScroll;
    }

    public String getVcsDir() {
        return vcsDir;
    }

    public String getSpecificBuild() {
        return specificBuild;
    }

    @DataBoundSetter
    public void setSince(SinceType since) {
        this.since = since;
    }

    @DataBoundSetter
    public void setSpecificRevision(String specificRevision) {
        this.specificRevision = specificRevision;
    }

    @DataBoundSetter
    public void setFormat(FormatType format) {
        this.format = format;
    }

    @DataBoundSetter
    public void setMatching(MatchingType matching) {
        this.matching = matching;
    }

    @DataBoundSetter
    public void setMatchingMaxComparisons(String matchingMaxComparisons) {
        this.matchingMaxComparisons = matchingMaxComparisons;
    }

    @DataBoundSetter
    public void setMatchWordsThreshold(String matchWordsThreshold) {
        this.matchWordsThreshold = matchWordsThreshold;
    }

    @DataBoundSetter
    public void setShowFiles(Boolean showFiles) {
        this.showFiles = showFiles;
    }

    @DataBoundSetter
    public void setSynchronisedScroll(Boolean synchronisedScroll) {
        this.synchronisedScroll = synchronisedScroll;
    }

    @DataBoundSetter
    public void setVcsDir(String vcsDir) {
        this.vcsDir = vcsDir;
    }

    @DataBoundSetter
    public void setSpecificBuild(String buildNumber) {
        this.specificBuild = buildNumber;
    }


}
