package com.github.jenkins.lastchanges.filecreation;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.StringReader;
import java.util.HashSet;
import java.util.Set;

import org.eclipse.jgit.lib.Repository;

import com.github.jenkins.lastchanges.impl.GitControllerChanges;

import hudson.FilePath;
import hudson.model.TaskListener;

/**
 * This class is to
 * a. Write code difference in a text file
 * b. Get Class differences
 * c. Get Controller names for the code changes
 * d. Trigger respective QA automation job
 */

public class CodeDifferenceAnalytics {

	private FilePath workspaceTargetDir;
	private String diff;
	private TaskListener listener;
	private Set<String> classDiff;
	private Set<String> packageStartNameDiff; //contains the package start names for the code diff
	private static String textFilePath = "diff.txt";
	private GitControllerChanges gitControllerChanges;
	
	public CodeDifferenceAnalytics(Repository repository, FilePath workspaceTargetDir, String diff, TaskListener listener) {
		this.workspaceTargetDir = workspaceTargetDir;
		this.diff = diff;
		this.listener=listener;
		gitControllerChanges = new GitControllerChanges(repository);		
	}
	
	/*
	 * Create diff.txt inside Build root directory with code differences from the previous build
	 */
	public boolean createDiffFile(){		
		String diffFilePath = workspaceTargetDir + "/" + textFilePath;		
		FilePath textFile = new FilePath(new File(diffFilePath));
		try {
			textFile.write(this.diff,"UTF-8");		
			listener.getLogger().println("Code differnces are logged in "+diffFilePath);
			return true;
		} catch (IOException | InterruptedException e) {
			listener.error("Error in creating code difference files: "+diffFilePath);
			listener.getLogger().println(e.getStackTrace());
			return false;
			
		}
	}
	
	public Set<String> getClassDifferences() {		
		classDiff = new HashSet<String>();
		packageStartNameDiff = new HashSet<String>();
	    StringReader reader = new StringReader(diff);	    
	    BufferedReader br = new BufferedReader(reader);
	    String split = "/src/main/java/";
	    
	    String line = "";
	    try {
			while((line = br.readLine())!=null)
			{			    
					if((line.contains("---") || line.contains("+++")) && (line.contains(split))){					
						String classPath = line.split(split)[1];  // ex: com/employee/controller/EmployeeController.java
						String packageStartName =   classPath.split("/")[0]+"/"+classPath.split("/")[1];			
						classDiff.add(classPath);				    
					    this.packageStartNameDiff.add(packageStartName);
				}
			}
		} catch (Exception e) {			
			listener.getLogger().println(e.getMessage());
			e.printStackTrace();
			return null;
		}
	    
	    return classDiff;
	}
	
	/*
	 * This method should be called after getClassDifferences() since you can get controllerFileNames only after classDifferences are obtained
	 */
	public Set<String> getControllerFileNames() {		
		
		Set<String> controllerFileNames = gitControllerChanges.getControllerFileNames(this.packageStartNameDiff);
		if(controllerFileNames == null) {
			listener.getLogger().println("Error in retriveing the controller files from the new commit");
			return null;
		}
		if(controllerFileNames.size() ==0 ){
			listener.getLogger().println("No controller files present in src/main/java in the new commit");
			return null;
		}
		else{
			listener.getLogger().println("Controller files present in the new commit are");
			for(String controllerFileName : controllerFileNames){
				listener.getLogger().println("-> "+controllerFileName);
			}
			return controllerFileNames;
		}
				
	}
	
	public void getResourcePath() {
		listener.getLogger().println("Controller Endpoints or resource path as below:");
		for(String controllerName : gitControllerChanges.getResourcePath().keySet() ){
			listener.getLogger().println("Controller File Name: "+controllerName);
			listener.getLogger().println("Resource Paths are: ");
			for(String resourcePath : gitControllerChanges.getResourcePath().get(controllerName)) {
				listener.getLogger().println(resourcePath);
			}
		}			
		

	}
	
}
