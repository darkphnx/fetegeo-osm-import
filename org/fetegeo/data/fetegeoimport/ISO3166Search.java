package org.fetegeo.data.fetegeoimport;

import java.util.Hashtable;
import java.util.Map;

/**
 * RemoteResource to fetch a list of ISO3166 country codes
 * @author dan
 *
 */
public class ISO3166Search extends RemoteResource{

	public Hashtable<String,String> countries;
	int lineNo = 0;
	
	/**
	 * Constructor
	 * @param remoteUrl location of remote resource
	 */
	public ISO3166Search(String remoteUrl){
		super(remoteUrl);
	}
	
	@Override
	void processLine(String line) {
		if(countries == null){
			countries = new Hashtable<String,String>();
		}
		
		if(lineNo > 1){
			String[] fields = line.split(";");
			countries.put(fields[0], fields[1]);
			countries.size();
		}
		lineNo++;
	}
	
	/**
	 * Get the ISO code for a country name
	 * @param countryName the full name of a country
	 * @return the ISO code for that country
	 */
	public String getISOCode(String countryName){
		String isoCode = countries.get(countryName.toUpperCase());
		if(isoCode == null){
			isoCode = "";
		}
		
		return isoCode.toLowerCase();
	}
	
	/**
	 * Get the country name from an ISO code
	 * @param ISOCode an ISO3166 code to look up
	 * @return the full country name
	 */
	public String getCountryName(String ISOCode){
		
		String countryName = "";
		for(Map.Entry<String, String> country : countries.entrySet()){
			if(country.getValue().equals(ISOCode)){
				countryName = country.getKey();
			}
		}
		
		return countryName;
	}

}
