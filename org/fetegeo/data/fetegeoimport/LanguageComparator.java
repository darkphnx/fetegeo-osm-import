package org.fetegeo.data.fetegeoimport;

import java.util.Comparator;

/**
 * Compares two Language objects
 * @author dan
 *
 */
public class LanguageComparator implements Comparator<Language> {

	/**
	 * @param o1 first language to compare
	 * @param o2 second language to compare
	 * @return 1 if o1s ID > o2s, 0 if they're the same, -1 otherwise 
	 */
	@Override
	public int compare(Language o1, Language o2) {
		if(o1.getId() > o2.getId()){
			return 1;
		} else if(o1.getId() == o2.getId()){
			return 0;
		} else {
			return -1;
		}
	}	
	
}
