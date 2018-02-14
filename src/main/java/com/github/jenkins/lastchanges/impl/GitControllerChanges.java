package com.github.jenkins.lastchanges.impl;

/*
 * This class is to get Controller files present in repository if there are changes in build and get corresponding resource path reading the controller class
 */
import java.io.BufferedReader;

/** GIT sample code with BSD license. Copyright by Steve Jin */

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.StringReader;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.apache.commons.lang.StringUtils;
import org.eclipse.jgit.errors.RevisionSyntaxException;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectLoader;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevTree;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.eclipse.jgit.treewalk.filter.PathFilter;
 
 
public class GitControllerChanges
{
	private Repository repository;
	private Map<String,Set<String>> resourcePathMap;
	
	public GitControllerChanges(Repository repository) {
		this.repository = repository;		
	}
	
	public Set<String> getControllerFileNames(Set<String> packageStartNameDiff) {
		
		Set<String> controllerNames = new HashSet<String>();
		
		resourcePathMap = new HashMap<String,Set<String>>();
 
	    ObjectId lastCommitId = null;
		try {
			lastCommitId = repository.resolve(Constants.HEAD);
		} catch (RevisionSyntaxException | IOException e) {			
			e.printStackTrace();
		}
	 
	    RevWalk revWalk = new RevWalk(repository);
	    RevCommit commit = null;
		try {
			commit = revWalk.parseCommit(lastCommitId);
		} catch (IOException e) {			
			e.printStackTrace();
		}
	 
	    RevTree tree = commit.getTree();
	 
	    TreeWalk treeWalk = new TreeWalk(repository);
	    try {
			treeWalk.addTree(tree);
		} catch (IOException e) {			
			e.printStackTrace();
			return null;
		}

	    treeWalk.setRecursive(true);	    
//	    treeWalk.setFilter(PathFilter.create("repo1/src/main/java)"));
	    try {
	        if (!treeWalk.next()) 
	        {
	          System.out.println("No files found in src/main/java in the current commit. Hence QA Automation job is not triggered");
	          return null;
	        }	       
	        
	        while (treeWalk.next()) {	        	
	        	if(treeWalk.getPathString().contains("src/main/java") && treeWalk.getPathString().contains("controller")){
	        		String treeWalkPath = treeWalk.getPathString();  //ex: member-service/src/main/java/com/member/controller/MemberController.java
	        		/*
	        		 * Below is execution  flow
	        		 * 
	        		 * 1. Get the treeWalkPath from the code diff that has src/main/java and controller -> ex: member-service/src/main/java/com/member/controller/MemberController.java
	        		 * 2. Loop packageDiff - this contains the package start name of the code differences.
	        		 * 3. Match if the treeWalkPath contains package start name of the code difference.
	        		 * 4. If yes, then it should be considered as Controller file name for the code differences.
	        		 */
	       		 	for( String packageStartName : packageStartNameDiff) {
	       		 		if(treeWalkPath.contains(packageStartName)){  //3. Match if the treeWalkPath contains package start name of the code difference.
			        		String controllerName = StringUtils.substringAfterLast(treeWalk.getPathString(), "/").split("\\.")[0]; //get controller name from the filepath
			        		controllerNames.add(controllerName); 			        		
			        		//Get Resource Path for each controller file
			        	    ObjectId objectId = treeWalk.getObjectId(0);
			        	    ObjectLoader loader = repository.open(objectId);    	 
			        	    ByteArrayOutputStream out = new ByteArrayOutputStream();
			        	    loader.copyTo(out);
			        	    resourcePathMap.put(controllerName, getRequestMapping(out.toString()));       		 		}
	       		 	}
	        	}
	        }    

		} catch (IOException e) {			
			e.printStackTrace();
			return null;
		}
	    
	    return controllerNames;
	}
	
	public Set<String> getRequestMapping(String str){
		Set<String> resourcePath = new HashSet<String>();
	    StringReader reader = new StringReader(str);
	    BufferedReader br = new BufferedReader(reader);
//	    System.out.println("Resources available in services are:");
	    String line = "";
	    try {
			while((line = br.readLine())!=null)
			{			    
				if(line.contains("RequestMapping")){
					resourcePath.add((line.split("RequestMapping")[1].replaceAll("[\\( \\)]","") ));
				}
			}
		} catch (IOException e) {			
			e.printStackTrace();			
		}
	    
	    return resourcePath;
	  
  }
	
	public Map<String, Set<String>> getResourcePath(){
		return this.resourcePathMap;
	}

}