package org.fetegeo.data.fetegeoimport;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.logging.Logger;

/**
 * Abstract class for fetching remote data streams from a specified URL
 * @author dan
 *
 */
public abstract class RemoteResource {
	
	protected static final Logger LOG = Logger.getLogger(LanguageImporter.class.getName());
	BufferedReader lang_buf;
	String resourceUrl;
	
	/**
	 * Constructor, passed the URL for the remote resource
	 * @param remoteUrl remote resource URL
	 */
	public RemoteResource(String remoteUrl) {
		resourceUrl = remoteUrl;
		fetchResource();
	}
	
	/**
	 * Downloads the requested resource, and feeds it line by line to 
	 * processLine()
	 */
	protected void fetchResource(){
		LOG.info("Fetching resource "+resourceUrl);
		
		URL langURL = null;
		
		try {
			langURL = new URL(resourceUrl);
		} catch (MalformedURLException e1) {
			LOG.severe(resourceUrl+" is an invalid URL");
			System.exit(1);
		}

		try {
			lang_buf = new BufferedReader(new InputStreamReader(langURL.openStream()));
			
			String line;
			while((line = lang_buf.readLine()) != null){
				processLine(line);
			}
		} catch (IOException e) {
			// TODO Auto-generated catch block
			LOG.severe(langURL.toString()+" could not be read");
			System.exit(1);
		}
	}
	
	/**
	 * Override to define how each line is processed
	 * @param line individual line from fetched resource
	 */
	abstract void processLine(String line);
}
