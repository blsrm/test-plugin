package com.github.jenkins.lastchanges.pipeline;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

import javax.xml.bind.DatatypeConverter;

public class RemoteTest {

	public static void main(String[] args) {
	    try {
	      URL url = new URL ("http://localhost:8080/jenkins/job/demo/build"); // Jenkins URL localhost:8080, job named 'test'
	      String user = "developer"; // username
	      String pass = "developer"; // password or API token
	      String authStr = user +":"+  pass;
	      String encoding = DatatypeConverter.printBase64Binary(authStr.getBytes("utf-8"));

	      HttpURLConnection connection = (HttpURLConnection) url.openConnection();
	      connection.setRequestMethod("POST");
	      connection.setDoOutput(true);
	      connection.setConnectTimeout(5000);
	      connection.setRequestProperty("Authorization", "Basic " + encoding);
	      connection.connect();
	      InputStream content = connection.getInputStream();
	      BufferedReader in   =
	          new BufferedReader (new InputStreamReader (content));
	      String line;
	      while ((line = in.readLine()) != null) {
	        System.out.println(line);
	      }
	    } catch(Exception e) {
	      e.printStackTrace();
	    }
	  }
}
