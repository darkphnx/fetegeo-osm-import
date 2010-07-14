package org.fetegeo.data.fetegeoimport;

/* 
 * This is probably all horrible
 * Sorry, I'm a java noob.
 */

import java.io.File;
import java.util.ArrayList;
import java.lang.Integer;

import org.openstreetmap.osmosis.core.container.v0_6.EntityContainer;
import org.openstreetmap.osmosis.core.domain.v0_6.Entity;
import org.openstreetmap.osmosis.core.domain.v0_6.Node;
import org.openstreetmap.osmosis.core.domain.v0_6.Way;
import org.openstreetmap.osmosis.core.domain.v0_6.Relation;
import org.openstreetmap.osmosis.core.domain.v0_6.Tag;
import org.openstreetmap.osmosis.core.lifecycle.CompletableContainer;
import org.openstreetmap.osmosis.core.pgsql.common.CopyFileWriter;
import org.openstreetmap.osmosis.core.pgsql.common.PointBuilder;
import org.openstreetmap.osmosis.core.task.v0_6.Sink;

import org.postgis.Geometry;
import org.postgis.Point;

/**
 * Main import writer loop for fetegeo
 * Extends Sink
 * @author dan
 *
 */
public class FetegeoImportWriter implements Sink {
	
	Integer place_id = 0;
	Integer place_name_id = 0;
	Integer country_id = 0;
	Integer country_name_id = 0;

	CompletableContainer writerContainer;
	CopyFileWriter placeWriter;
	CopyFileWriter placeNameWriter;
	CopyFileWriter placeNameWordsWriter;
	CopyFileWriter countryWriter;
	CopyFileWriter countryNameWriter;
	
	LocationBuilder locationBuilder;
	LanguageImporter languageImporter;
	ISO3166Search countryCodeSearch;
	
	Point nullPoint = new Point(0,0);
	
	PointBuilder pointBuilder;
	
	// Type constants
	private final int TYPE_MISC = 0;
	private final int TYPE_ROAD = 1;
	private final int TYPE_BOUNDARY = 2;
	private final int TYPE_PLACE = 3;
	
	/**
	 * Constructor for FetegeoImportWriter
	 * @param outdir Directory where files are to be written
	 */
	public FetegeoImportWriter(final File outdir) {
		//Set up our writers
		writerContainer = new CompletableContainer();		
		placeWriter = writerContainer.add(new CopyFileWriter(new File(outdir.getAbsolutePath(), "places.txt")));
		placeNameWriter = writerContainer.add(new CopyFileWriter(new File(outdir.getAbsolutePath(), "place_names.txt")));
		placeNameWordsWriter = writerContainer.add(new CopyFileWriter(new File(outdir.getAbsolutePath(), "place_name_words.txt")));
		
		countryWriter = writerContainer.add(new CopyFileWriter(new File(outdir.getAbsolutePath(), "countries.txt")));
		countryNameWriter = writerContainer.add(new CopyFileWriter(new File(outdir.getAbsolutePath(), "country_names.txt")));
		
		locationBuilder = new LocationBuilder();
		
		languageImporter = new LanguageImporter("http://download.geonames.org/export/dump/iso-languagecodes.txt");
		languageImporter.writeLanguages(writerContainer, outdir);
		
		countryCodeSearch = new ISO3166Search("http://www.iso.org/iso/list-en1-semic-3.txt");
		
		nullPoint.srid = 4326;
	}
	
	
	/**
	 * Gets hit for every entity in the reader
	 * @param entityContainer a Node, Way or Relation
	 */
	@Override
	public void process(EntityContainer entityContainer) {

		//Map<String, String> names = new HashMap<String, String>();
		Entity entity = entityContainer.getEntity();
		Boolean country = false;
		ArrayList<String[]> names = new ArrayList<String[]>();
		
		// Gather up the names before we process the record, we dont want to process one which
		// has no name identifying it
		for(Tag tag : entity.getTags()){
			
			// Relations can be identified by their ref, since they often contain roads.
			if(tag.getKey().startsWith("name") || tag.getKey().startsWith("short_name") 
					|| (entity instanceof Relation && tag.getKey().equals("ref"))){
				
				// Name tags come in format name:lang or just name for canonical names
				String[] nameTag = tag.getKey().split(":");
				if(nameTag.length > 1){
					//ÊWrite the hashmap keyed on name
					//names.put(tag.getValue(), nameTag[1]);
					names.add( new String[]{tag.getValue(), nameTag[1]} );
				} else {
					// If we have no lang part then it's a canonical name, no lang is listed
				    //names.put(tag.getValue(), "");
				    names.add( new String[]{tag.getValue(), "CANONICAL"} );
				}
				
			}
			
			// If we're looking at a country, it needs to go in the countries file
			if((tag.getKey().equals("place") && tag.getValue().equals("country")) || 
					(entity instanceof Relation && tag.getKey().equals("admin_level") && Integer.parseInt(tag.getValue()) <= 2)){
				country = true;
			}
			
		}
		
		if(country && names.size() > 0){
			writeCountry(entity, names);
		} else {
			// Check if we have a name before writing a node
			//if(names.size() > 0 && entity.getId() != 189){ // Horrid erroneous data, will fix source. Patch for now.
			if(names.size() > 0){
				writePlace(entity, names);
			} else if(entity instanceof Node){
				// If a node doesn't have a name, we still want to cache it for future use in ways etc.
				locationBuilder.cache((Node) entity);
			} else if(entity instanceof Way){
				// If a way doesn't have a name, we still want to cache it for future use in relations
				locationBuilder.cache((Way) entity);
			}
			
		}
		
	}
	
	/**
	 * Write a place to places.txt, place_names.txt, place_names_words.txt
	 * @param entity the Node, Way or relation
	 * @param names ArrayList of the different names for that entity
	 */
	private void writePlace(Entity entity, ArrayList<String[]> names){		
		
		// Assume everywhere is a place to start with
		Integer type = TYPE_MISC;
		
		// OSM admin level is 0 initially (for not administrative boundary)
		Integer admin_level = 0;
		
		Integer population = 0;
		
		for(Tag tag : entity.getTags()){
			if(tag.getKey().equals("population")){
				// Get the population and strip out any pesky commas etc.
				try{
					population = Integer.parseInt(tag.getValue().replaceAll("[^0-9]", ""));
				} catch(java.lang.NumberFormatException e){
					population = 0;
				}
			} else if(tag.getKey().equals("boundary")){
				type = TYPE_BOUNDARY;
			} else if(tag.getKey().equals("highway")){
				type = TYPE_ROAD;
			} else if(tag.getKey().equals("place")){
				type = TYPE_PLACE;
			} else if(tag.getKey().equals("admin_level")){
				admin_level = Integer.valueOf(tag.getValue());
			}
		}
		
		placeWriter.writeField(place_id); // Place id
		placeWriter.writeField(entity.getId()); // OSM id
		Geometry location = locationBuilder.getLocation(entity); // PostGIS location
		// We may have cases where there's a linestring, but no points available, this will produce a NPE
		try{
			// Do something to get a NPE before we try to write the field
			location.getValue();
			placeWriter.writeField(location);
		} catch(NullPointerException e){
			placeWriter.writeField(nullPoint);
		}
		
		placeWriter.writeField(type);
		placeWriter.writeField(admin_level);
		placeWriter.writeField(population); // Population
		placeWriter.endRecord();
		
		String canonicalName = "";
		for(String[] name : names){
			
			String placeName = name[0];
			String lang = name[1];
			
			if(lang.equals("CANONICAL")){
				canonicalName = placeName;
				lang = "";
			}
			
			placeNameWriter.writeField(place_name_id);
			placeNameWriter.writeField(place_id); // Place id
			placeNameWriter.writeField(languageImporter.getLanguageId(lang)); // Language
			placeNameWriter.writeField(placeName); // Text value
			placeNameWriter.writeField(hash_name(placeName)); // Text hash
			placeNameWriter.writeField(canonicalName.equalsIgnoreCase(placeName)); // Official name (t/f)
			placeNameWriter.endRecord();
			
			String firstWord = "";
			// For now, only write the first word in a name, makes searching easier.
			try{
				firstWord = placeName.split("[ ,-/]")[0];
			} catch(java.lang.ArrayIndexOutOfBoundsException e) {
				// If the string is empty, or someone has just used a space for a name
				// then we'll try and hash the whole thing
				firstWord = placeName;
			}
			
			placeNameWordsWriter.writeField(place_name_id);
			placeNameWordsWriter.writeField(String.valueOf(hash_name(firstWord)));
			placeNameWordsWriter.endRecord();
			
			// Uncomment to write all words to place_name_words.txt
			/*for(String part : placeName.split("[ ,-/]")){
				placeNameWordsWriter.writeField(place_name_id);
				placeNameWordsWriter.writeField(String.valueOf(hash_name(part)));
				placeNameWordsWriter.endRecord();
			}*/
			
			place_name_id++;
		}
		
		place_id++;
	}
	
	/**
	 * Write a country to countries.txt, country_names.txt
	 * @param entity the Relation or Way representing a country
	 * @param ArrayList of different names for this country
	 */
	private void writeCountry(Entity entity, ArrayList<String[]> names){
		String iso2 = "";
		// Iso country codes are referenced by english name
		String isoName = "";
		
		for(Tag tag : entity.getTags()){
			if(tag.getKey().equals("country_code_iso3166_1_alpha_2") ||
				tag.getKey().equals("country_code") || tag.getKey().equals("ISO3166-1")){
				iso2 = tag.getValue();
			}
			
			if(tag.getKey().equals("name:en") || tag.getKey().equals("name")){
				isoName = tag.getValue();
			}
		}
		
		// If we can't find an ISO2 code in the OSM metadata, consult the list provided by ISO
		if(iso2.trim().isEmpty()){
			try{
				iso2 = countryCodeSearch.getISOCode(isoName);
			} catch(NullPointerException e){
				System.out.println("Could not find iso2 code for "+isoName);
			}
		}

		countryWriter.writeField(country_id);
		countryWriter.writeField(entity.getId());
		countryWriter.writeField(iso2.trim().toUpperCase());
		// countryWriter.writeField(locationBuilder.getLocation(entity));
		
		Geometry location = locationBuilder.getLocation(entity);
		try{
			// Do something to get a NPE before we try to write the field
			location.getValue();
			countryWriter.writeField(location);
		} catch(NullPointerException e){
			countryWriter.writeField(nullPoint);
		}
		
		countryWriter.endRecord();
		
		String canonicalName = "";
		
		for(String[] name : names){
			
			// If we've got no lang then it's the canonical name
			/*if(name.getKey() == ""){
				canonicalName = name.getKey();
			}*/
			
			String placeName = name[0];
			String lang = name[1];
			
			if(lang.equals("CANONICAL")){
				canonicalName = placeName;
				lang = "";
			}
			
			countryNameWriter.writeField(country_name_id);
			countryNameWriter.writeField(country_id);
			countryNameWriter.writeField(languageImporter.getLanguageId(lang));
			countryNameWriter.writeField(canonicalName.equalsIgnoreCase(placeName));
			countryNameWriter.writeField(placeName);
			countryNameWriter.writeField(hash_name(placeName));
			countryNameWriter.endRecord();
			
			country_name_id++;
		}
		
		country_id++;
	}
	
	/**
	 * Generate a hash of a name, strings will be converted to lower case,
	 * spaces replaced with underscores and hased with the algorithm
	 * s[0]*31^(n-1) + s[1]*31^(n-2) + ... + s[n-1]
	 * 
	 * @param name the name to be hashed
	 * @return a 32bit signed integer of the hash
	 */
	private int hash_name(String name){
		// hashCode implementation: s[0]*31^(n-1) + s[1]*31^(n-2) + ... + s[n-1]
		// Wraps 32-bit signed integers.
		return name.toLowerCase().trim().replace(" ", "_").hashCode();
	}

	@Override
	public void complete() {
		writerContainer.complete();

	}

	@Override
	public void release() {
		writerContainer.release();
	}

}
