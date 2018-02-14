package com.github.jenkins.lastchanges.pipeline;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;

import javax.xml.bind.DatatypeConverter;

public class JavaTest {
	 public static void main(String[] args){
		 	
		 String treeWalkPath = "member-service/src/main/java/com/member/controller/MemberController.java";
		 
		 Set<String> packageDiff = new HashSet<String>();
		 packageDiff.add("com/employee");
		 packageDiff.add("com/member");
		 
		 for( String packageStartName : packageDiff) {
			 
			 if(treeWalkPath.contains(packageStartName)) System.out.println(treeWalkPath);
		 }
		 
		 String urlPath = "http://localhost:8080/jenkins/job/JIRA-4567   Regression/3/consoleText";
//		 System.out.println(urlPath.replaceAll(" ", "%20"));
		 
		 String response = null;
			
			String encodedUrl = null;

			encodedUrl = urlPath.replaceAll("\\s+", "%20");
			
			URL url = null;
			try {
				url = new URL (encodedUrl);
			} catch (MalformedURLException e) {
				// TODO Auto-generated catch block			
				System.out.println(e.getMessage());			
			}
		      HttpURLConnection connection = null;
			try {
				connection = (HttpURLConnection) url.openConnection();
				System.out.println("Successfully Connected");
			} catch (IOException e) {
				System.out.println(e.getMessage());			
			}
			
			String authStr = "admin"+":"+"6678cae4f5f965d1e8e2375c670ed7e6";

		     String encoding = null;
			try {
				encoding = DatatypeConverter.printBase64Binary(authStr.getBytes("utf-8"));
			} catch (UnsupportedEncodingException e) {
				System.out.println(e.getMessage());		
			}
			try{
		      connection.setRequestMethod("GET");
		      connection.setDoOutput(true);
		      connection.setConnectTimeout(5000);
		      connection.setRequestProperty("Authorization", "Basic " + encoding);
		      connection.connect();
		      InputStream is = null;
		      
		      try {
		    	  is= connection.getInputStream();
		      } catch(FileNotFoundException  e){
		    	  // In case of a e.g. 404 status	    
		    	  System.out.println(e.getMessage());		
		      }
		      
		      if(is !=null)response = getStringFromInputStream(is);
		      System.out.println("Response is"+response);
			} catch(IOException  e){
				System.out.println(e.getMessage());		
			}
			

		}
		
		private static String getStringFromInputStream(InputStream is) {

			BufferedReader br = null;
			StringBuilder sb = new StringBuilder();

			String line;
			try {

				br = new BufferedReader(new InputStreamReader(is));
				while ((line = br.readLine()) != null) {
					sb.append(line+"\n");
				}

			} catch (IOException e) {
				e.printStackTrace();
			} finally {
				if (br != null) {
					try {
						br.close();
					} catch (IOException e) {
						e.printStackTrace();
					}
				}
			}

			return sb.toString();

		}
		
	 }
	 
	 
	 
	 

