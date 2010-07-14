package org.fetegeo.data.fetegeoimport;

import java.io.File;

import org.openstreetmap.osmosis.core.pipeline.common.TaskConfiguration;
import org.openstreetmap.osmosis.core.pipeline.common.TaskManager;
import org.openstreetmap.osmosis.core.pipeline.common.TaskManagerFactory;
import org.openstreetmap.osmosis.core.pipeline.v0_6.SinkManager;

import org.fetegeo.data.fetegeoimport.FetegeoImportWriter;

public class FetegeoImportFactory extends TaskManagerFactory {

	@Override
	protected TaskManager createTaskManagerImpl(TaskConfiguration taskConfig) {
		File outdir;
		FetegeoImportWriter task;
		
		String outdir_name = getStringArgument(taskConfig, 
				 	      	 "outdir", 
				 	      	 getDefaultStringArgument(taskConfig, "/tmp/"));
		
		// Add a trailing slash if one is not present
		if(!outdir_name.endsWith("/")){
			outdir_name = outdir_name+'/';
		}
		
		// Check the file exists and is a directory
		outdir = new File(outdir_name);
		if(!outdir.isDirectory()){
			System.err.println(outdir+" does not exist or is not a directory");
		}
		
		task = new FetegeoImportWriter(outdir);
		
		return new SinkManager(taskConfig.getId(), task, taskConfig.getPipeArgs());
	}

}
