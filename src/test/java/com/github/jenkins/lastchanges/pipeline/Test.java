package com.github.jenkins.lastchanges.pipeline;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.StringReader;
import java.util.HashSet;
import java.util.Set;

import org.apache.commons.lang.StringUtils;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectLoader;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevTree;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.eclipse.jgit.treewalk.filter.PathFilter;
 
 
public class Test
{
  public static void main(String[] args) throws Exception
  {
	File gitWorkDir = new File("C:/Users/176912/Desktop/Automation/CHC/Source Code/git");
    Git git = Git.open(gitWorkDir);
    Repository repo = git.getRepository();
 
    ObjectId lastCommitId = repo.resolve(Constants.HEAD);
 
    RevWalk revWalk = new RevWalk(repo);
    RevCommit commit = revWalk.parseCommit(lastCommitId);
 
    RevTree tree = commit.getTree();
 
    TreeWalk treeWalk = new TreeWalk(repo);
    treeWalk.addTree(tree);
    treeWalk.setRecursive(true);
    treeWalk.setFilter(PathFilter.create("src/main/java"));
    if (!treeWalk.next()) 
    {
      System.out.println("No files found in src/main/java in the current commit. Hence QA Automation job is not triggered");
      return;
    }
    
    Set<String> controllerNames = new HashSet<String>();
    
    while (treeWalk.next()) {
    	if(treeWalk.getPathString().contains("controller")){
    		System.out.println("Controller file present in the commit are: " + treeWalk.getPathString());    
    		controllerNames.add(StringUtils.substringAfterLast(treeWalk.getPathString(), "/").split("\\.")[0]);   
    	    ObjectId objectId = treeWalk.getObjectId(0);
    	    ObjectLoader loader = repo.open(objectId);    	 
    	    ByteArrayOutputStream out = new ByteArrayOutputStream();
    	    loader.copyTo(out);
    	    getRequestMapping(out.toString());

    	}
    }    

  }
  
  public static void getRequestMapping(String str){
	    StringReader reader = new StringReader(str);
	    BufferedReader br = new BufferedReader(reader);
	    System.out.println("Resources available in services are:");
	    String line = "";
	    try {
			while((line = br.readLine())!=null)
			{			    
				if(line.contains("RequestMapping")){
					System.out.println(line.split("RequestMapping")[1].replaceAll("[\\( \\)]","") );
				}
			}
		} catch (IOException e) {			
			e.printStackTrace();			
		}

	  
  }
}