package org.fetegeo.data.fetegeoimport;

import java.util.ArrayList;
import java.util.List;
import java.io.File;
import java.io.IOException;
//import java.util.Arrays;

import org.postgis.Geometry;
import org.postgis.Point;
import org.postgis.LineString;
import org.postgis.LinearRing;
import org.postgis.Polygon;
import org.postgis.MultiLineString;
import org.postgis.MultiPolygon;

import org.openstreetmap.osmosis.core.domain.v0_6.Entity;
import org.openstreetmap.osmosis.core.domain.v0_6.EntityType;
import org.openstreetmap.osmosis.core.domain.v0_6.Node;
import org.openstreetmap.osmosis.core.domain.v0_6.Way;
import org.openstreetmap.osmosis.core.domain.v0_6.WayNode;
import org.openstreetmap.osmosis.core.domain.v0_6.Relation;
import org.openstreetmap.osmosis.core.domain.v0_6.RelationMember;
import org.openstreetmap.osmosis.core.domain.v0_6.Tag;

// Temp storage shiv
import org.openstreetmap.osmosis.core.lifecycle.CompletableContainer;
import org.openstreetmap.osmosis.core.store.ComparableComparator;
import org.openstreetmap.osmosis.core.store.RandomAccessObjectStore;
import org.openstreetmap.osmosis.core.store.RandomAccessObjectStoreReader;
import org.openstreetmap.osmosis.core.store.SingleClassObjectSerializationFactory;
import org.openstreetmap.osmosis.core.store.IndexStore;
import org.openstreetmap.osmosis.core.store.IndexStoreReader;
import org.openstreetmap.osmosis.core.store.LongLongIndexElement;
import org.openstreetmap.osmosis.core.store.NoSuchIndexElementException;

/**
 * Class to build PostGIS objects from Osmosis Node/Way/Relations
 * @author dan
 *
 */
public class LocationBuilder {

	Entity entity;
	Geometry location;
	
	private CompletableContainer storeContainer;
	
	private RandomAccessObjectStore<Node> nodeObjectStore;
	private RandomAccessObjectStoreReader<Node> nodeObjectReader;
	private IndexStore<Long, LongLongIndexElement> nodeObjectOffsetIndexWriter;
	private IndexStoreReader<Long, LongLongIndexElement> nodeObjectOffsetIndexReader;
	
	private RandomAccessObjectStore<Way> wayObjectStore;
	private RandomAccessObjectStoreReader<Way> wayObjectReader;
	private IndexStore<Long, LongLongIndexElement> wayObjectOffsetIndexWriter;
	private IndexStoreReader<Long, LongLongIndexElement> wayObjectOffsetIndexReader;
	
	/**
	 * Constructor, sets up the node and way caches and indexes on disk
	 */
	public LocationBuilder(){
		// Create a cache container
		storeContainer = new CompletableContainer();
		
		// Set up node caching silos
		try {
			nodeObjectStore = storeContainer.add(new RandomAccessObjectStore<Node>(
					new SingleClassObjectSerializationFactory(Node.class),
					File.createTempFile("fgeonod-", ".tmp" )));
		} catch (IOException e1) {
			System.out.println("Could not create node cache file");
			e1.printStackTrace();
		}
		
		try {
			nodeObjectOffsetIndexWriter = storeContainer.add(
					new IndexStore<Long, LongLongIndexElement>(
					LongLongIndexElement.class,
					new ComparableComparator<Long>(),
					File.createTempFile("fgeonod-", ".idx.tmp")));
		} catch (IOException e1) {
			System.out.println("Could not create node cache index");
			e1.printStackTrace();
		}
		
		
		// Set up way caching silos	
		try {
			wayObjectStore = storeContainer.add(new RandomAccessObjectStore<Way>(
					new SingleClassObjectSerializationFactory(Way.class),
					File.createTempFile("fgeoway-", ".tmp" )));
		} catch (IOException e) {
			System.out.println("Could not create way cache file");
			e.printStackTrace();
		}
		
		try {
			wayObjectOffsetIndexWriter = storeContainer.add(
					new IndexStore<Long, LongLongIndexElement>(
					LongLongIndexElement.class,
					new ComparableComparator<Long>(),
					File.createTempFile("fgeoway-", ".idx.tmp")));
		} catch (IOException e) {
			System.out.println("Could not create way cache index");
			e.printStackTrace();
		}
		
	}
	
	/**
	 * Convenience function to chose the correct method to process an OSM
	 * object
	 * @param entity Node/Way/Relation to be processed into a Location
	 * @return PostGIS Geometry representing the entity
	 */
	public Geometry getLocation(Entity entity){

		if(entity instanceof Node){
			location = process((Node) entity);
			
			// Cache point so we can look it up later for ways etc.
			cache((Node)entity);
		} else if(entity instanceof Way){
			location = process((Way) entity);
			
			cache((Way) entity);
		} else if(entity instanceof Relation){
			location = process((Relation) entity);
		}
		
		if(location != null){
			location.srid = 4326;
		}
		
		return location;
	}
	
	/**
	 * Adds a node to the cache
	 * @param node the Node to be cachced
	 */
	public void cache(Node node){
		long objectOffset = nodeObjectStore.add(node);
		nodeObjectOffsetIndexWriter.write(new LongLongIndexElement(node.getId(), objectOffset));
	}
	
	/**
	 * Adds a way to the cache
	 * @param way the Way to be cached
	 */
	public void cache(Way way){
		long objectOffset = wayObjectStore.add(way);
		wayObjectOffsetIndexWriter.write(new LongLongIndexElement(way.getId(), objectOffset));
	}
	
	/**
	 * Turn an OSM node into a PostGIS Point
	 * @param entity the OSM node
	 * @return the PostGIS representation of the entity
	 */
	private Point process(Node entity){
		double lat = entity.getLatitude();
		double lon = entity.getLongitude();
		
		Point result = new Point(lat, lon);
		
		return result;
	}
	
	/**
	 * Process a Way into an appropriate PostGIS geometry
	 * @param entity the OSM Way
	 * @return a LineString if the Way is open, a Polygon if the Way is closed, Node if way only has 1 point
	 */
	private Geometry process(Way entity){
		// OSM Node References
		List<WayNode> nodes = entity.getWayNodes();
		// PostGIS format points
		Point[] points = new Point[nodes.size()];
		
		if (nodeObjectReader == null) {
			nodeObjectStore.complete();
			nodeObjectReader = nodeObjectStore.createReader();
		}
		if (nodeObjectOffsetIndexReader == null) {
			nodeObjectOffsetIndexWriter.complete();
			nodeObjectOffsetIndexWriter.indexStore.complete();
			nodeObjectOffsetIndexReader = nodeObjectOffsetIndexWriter.createReader();
		}
		
		int i = 0;
		for(WayNode wayNode : nodes){			
			// Fetch the node from our cache		
			try{
				Node nodeLocation = nodeObjectReader.get(nodeObjectOffsetIndexReader.get(wayNode.getNodeId()).getValue());
				points[i] = new Point(nodeLocation.getLatitude(), nodeLocation.getLongitude());
				i++;
			} catch(NoSuchIndexElementException e){
				System.out.println("No such node "+wayNode.getNodeId());
			}
		}
		
		// With roads we almost always want LineStrings and not polygons (not sure where carparks fit into things)
		boolean forceLine = false;
		for(Tag tag : entity.getTags()){
			if(tag.getKey().equals("highway") || tag.getKey().equals("route")){
				forceLine = true;
			} else if(tag.getKey().equals("junction") && tag.getValue().equals("roundabout")){
				return null;
			}
		}
		
		// If our last node is the same as the first we have a closed loop aka an Area
		// i - 1 should give us the last valid point index
		if(points[0].equals(points[i-1]) && points.length > 3 && !forceLine){
			// Area
			//LinearRing polyPoints[] = new LinearRing[1];
			//polyPoints[0] = new LinearRing(points);
			LinearRing polyPoints[] = new LinearRing[]{ new LinearRing(points) };
			return new Polygon(polyPoints);
		} else if(i > 1) {
			// Line
			return new LineString(points);
		} else {
			return points[0];
		}
	}
	
	/**
	 * Process an OSM relation into an appropriate PostGIS Geometry
	 * Evaluates which is more relevant, lines or polygons and returns based
	 * upon which has more results.
	 * <p>
	 * In the case of boundaries a MultiLineString will always be returned to
	 * be post-processed by ST_BuildArea since PostGIS JDBC does not support 
	 * this operation
	 * 
	 * @param entity the OSM relation to be processed
	 * @return A multipolygon or multilinestring based upon the input
	 */
	private Geometry process(Relation entity){
		// Relations are logical groups of other entities, string them together to form roads etc.
		
		List<RelationMember> members = entity.getMembers();
		ArrayList<Geometry> geoms = new ArrayList<Geometry>();
		Geometry result = null;
		EntityType type = null;
		
		if (wayObjectReader == null) {
			wayObjectStore.complete();
			wayObjectReader = wayObjectStore.createReader();
		}
		
		if (wayObjectOffsetIndexReader == null) {
			wayObjectOffsetIndexWriter.complete();
			wayObjectOffsetIndexWriter.indexStore.complete();
			wayObjectOffsetIndexReader = wayObjectOffsetIndexWriter.createReader();
		}
		
		
		// We're going to count the number of polys vs lines, to try and determine which is more important
		int lines = 0;
		int polys = 0;
		
		for(RelationMember member : members){
			
			type = member.getMemberType();
			long id = member.getMemberId();
			
			// We only deal with ways in relations atm.
			if(type.equals(EntityType.Way)){
				
				try{
					
					Way way = wayObjectReader.get(wayObjectOffsetIndexReader.get(id).getValue());
					
					if(way == null){
						continue;
					}
					
					Geometry node_geom = process(way);
					geoms.add(node_geom);

					if(node_geom instanceof LineString){
						lines++;
					} else if(node_geom instanceof Polygon){
						polys++;
					}

				} catch(NoSuchIndexElementException e){
					System.err.println("No such way "+id);
				}
				
			}
			
		}
		
		// Boundaries are always polygons, find out if we're dealing with one
		boolean boundary = false;
		
		for(Tag tag : entity.getTags()){
			if(tag.getKey().equalsIgnoreCase("boundary")){
				boundary = true;
			}
		}

		// Boundaries are typically made up of multiple linestrings, each decribing a border
		if(lines > polys || boundary){
			// Filter out non linestrings
			geoms = pureList(Geometry.LINESTRING, geoms);
			if(geoms.size() > 1){
				LineString[] temp = new LineString[geoms.size()];
				result = new MultiLineString((LineString[]) geoms.toArray(temp));
			} else if(geoms.size() == 1) {
				result = geoms.get(0);
			} else {
				result = null;
			}
		} else {
			// Filter out non polys
			geoms = pureList(Geometry.POLYGON, geoms);
			if(geoms.size() > 1){
				Polygon[] temp = new Polygon[geoms.size()];
				result = new MultiPolygon((Polygon[]) geoms.toArray(temp));
			} else if(geoms.size() == 1) {
				result = geoms.get(0);
			} else {
				result = null;
			}
		}

		return result;
	
	}
	
	/**
	 * Ensures that a list is all of one type, removing any others
	 * @param type an int representing the class of the type we want to keep
	 * e.g. org.postgis.Geometry.POLYGON
	 * @param list an ArrayList of Geometries to be filtered
	 * @return an ArrayList containing only the geometry types specified
	 */
	private ArrayList<Geometry> pureList(int type, ArrayList<Geometry> list){
		
		ArrayList<Geometry>rtnList = new ArrayList<Geometry>();
		
		for(Geometry geom : list){
			if(geom != null && geom.type == type){
				rtnList.add(geom);
			}
		}
		
		return rtnList;
		
	}
		
}