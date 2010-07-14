package org.fetegeo.data.fetegeoimport;

/**
 * Contains all of the information about a language
 * @author dan
 */
public class Language {
	
	// OSM supports ISO639_1 and ISO639_2
	private String iso639_1;
	private String iso639_2;
	private String name;
	private int fetegeo_id;
	
	/**
	 * Constructor for language object
	 * 
	 * @param iso1 ISO639-1 code
	 * @param iso2 ISO639-2 code
	 * @param langName Languages full name
	 * @param id unique id for the database
	 */
	public Language(String iso1, String iso2, String langName, int id){
		iso639_1 = iso1;
		iso639_2 = iso2;
		name = langName;
		fetegeo_id = id;
	}
	
	/**
	 * Set the language code
	 * @param isoCode ISO639-1 code
	 */
	public void setISO639_1(String isoCode){
		iso639_1 = isoCode;
	}
	
	/**
	 * Get the ISO639-1 code
	 * @return ISO639-1 code
	 */
	public String getISO639_1(){
		return iso639_1;
	}
	
	/**
	 * Set the ISO639-2 code
	 * @param isoCode ISO639-2 code to be set
	 */
	public void setISO639_2(String isoCode){
		iso639_2 = isoCode;
	}
	
	/**
	 * Get the ISO639-2 code
	 * @return ISO639-2 code
	 */
	public String getISO639_2(){
		return iso639_2;
	}
	
	/**
	 * Set the full name of the language e.g. "United Kingdom"
	 * @param langName Full name of the language
	 */
	public void setName(String langName){
		name = langName;
	}
	
	/**
	 * Get the full name of a language
	 * @return the full name of a language
	 */
	public String getName(){
		return name;
	}
	
	/**
	 * Set the id for storage in the database
	 * @param id set the id for storage in the database
	 */
	public void setId(int id){
		fetegeo_id = id;
	}
	
	/**
	 * Get the id to be put in the database
	 * @return the id
	 */
	public int getId(){
		return fetegeo_id;
	}
	
}

