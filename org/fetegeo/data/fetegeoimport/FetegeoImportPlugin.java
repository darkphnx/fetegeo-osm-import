package org.fetegeo.data.fetegeoimport;

import java.util.HashMap;
import java.util.Map;

import org.fetegeo.data.fetegeoimport.FetegeoImportFactory;

import org.openstreetmap.osmosis.core.pipeline.common.TaskManagerFactory;
import org.openstreetmap.osmosis.core.plugin.PluginLoader;

public class FetegeoImportPlugin implements PluginLoader {

	@Override
	public Map<String, TaskManagerFactory> loadTaskFactories() {
		HashMap<String, TaskManagerFactory> map = new HashMap<String, TaskManagerFactory>();
		map.put("dump-fetegeo", new FetegeoImportFactory());
		
		return map;
	}

}
