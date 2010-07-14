package org.fetegeo.data.fetegeoimport;

import java.util.Arrays;
import java.util.Hashtable;
import java.io.File;

import org.openstreetmap.osmosis.core.lifecycle.CompletableContainer;
import org.openstreetmap.osmosis.core.pgsql.common.CopyFileWriter;

/**
 * Class to download a file full of language codes and names such as
 * http://download.geonames.org/export/dump/iso-languagecodes.txt
 * File should be tab-separated, fields in the order 
 * ISO 639-3, ISO 639-2, ISO 639-1, Language Name
 * 
 * @author dan
 *
 */
public class LanguageImporter extends RemoteResource{
	
	Hashtable<String,Language> langs;
	
	// Just a counter to increment for successful adds
	int fetegeo_id = 0;
	
	public LanguageImporter(String remoteUrl){
		super(remoteUrl);
	}
	
	@Override
	void processLine(String langLine){
		
		if(langs == null){
			langs = new Hashtable<String,Language>();
		}
		
		// Languages come in a handy tab separated format
		String[] fields = langLine.split("\t");
		
		// Make sure we're not looking at the first line, check we have an 639-1 or 639-2 code
		if(!fields[3].equalsIgnoreCase("Language Name") && (!fields[1].isEmpty() || !fields[2].isEmpty())){
			Language language = new Language(fields[2], fields[1], fields[3], fetegeo_id);
			
			// Key on ISO1 if available, otherwise ISO2
			String key;
			if(fields[2].isEmpty()){
				key = fields[1];
			} else {
				key = fields[2];
			}
			
			langs.put(key, language);
			fetegeo_id++;
		}
		
	}
	
	/**
	 * Write the languages out to a file
	 * 
	 * @param container mangagement container for file writers
	 * @param outdir directory where output files are being written
	 */
	public void writeLanguages(CompletableContainer container, File outdir){
		CopyFileWriter langFile = container.add(new CopyFileWriter(new File(outdir.getAbsolutePath(), "languages.txt")));
		
		for(Language lang : getLangauges()){
			langFile.writeField(lang.getId());
			langFile.writeField(lang.getISO639_1());
			langFile.writeField(lang.getISO639_2());
			langFile.writeField(lang.getName());
			langFile.endRecord();
		}
		
	}
	
	/**
	 * Return the full Language object from an ISO 639 code
	 * 
	 * @param isoCode iso339-1 or iso639-2 code which you wish to find
	 * @return the Language object for the lang requested
	 * @throws NullPointerException if the language cannot be found
	 */
	public Language getLanguage(String isoCode) throws NullPointerException{
		return langs.get(isoCode);
	}
	
	/**
	 * Returns only the database ID for the specified ISO639 code
	 * 
	 * @param isoCode ISO 639-1 or 639-2 code
	 * @return Language ID, -1 if no language can be found
	 */
	public int getLanguageId(String isoCode){
		
		// Set English as default lang
		/*if(isoCode.isEmpty()){
			isoCode = "en";
		}*/
		
		try{
			Language lang = getLanguage(isoCode);
			return lang.getId();
		} catch(NullPointerException e){
			return -1;
		}
	}
	
	/**
	 * Return an array of all language objects
	 * @return Array of Languages
	 */
	public Language[] getLangauges(){
		
		// Put the languages into a run-of-the-mill array and sort them
		Language[] temp = new Language[langs.size()];
		Language[] langArr =  langs.values().toArray(temp);
		
		Arrays.sort(langArr, new LanguageComparator());
		
		return langArr;
	}
	
}
