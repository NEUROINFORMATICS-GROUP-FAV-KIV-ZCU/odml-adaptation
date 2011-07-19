package odml.util;
/************************************************************************
 *	odML - open metadata Markup Language - 
 * Copyright (C) 2009, 2010 Jan Grewe, Jan Benda 
 * 
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the  GNU Lesser General Public License (LGPL) as published by
 * the Free Software Foundation; either version 3 of the License, or
 * (at your option) any later version.
 *
 * odML is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU Lesser General Public License
 * along with this software.  If not, see <http://www.gnu.org/licenses/>.
 */
import java.io.IOException;
import java.net.URL;
import java.util.HashMap;

import org.apache.log4j.Logger;
import org.apache.log4j.PropertyConfigurator;

import odml.core.Property;
import odml.core.Section;
/**
 * The {@link Mapper} controls the mapping procedure. Mapping information can be given for {@link Property} and {@link Section} elements 
 * and is used to transform from one, custom, organization of the metadata structure, to another, the odml standard or any else, structure. 
 * 
 * @author grewe
 *
 */
public class Mapper {
	static Logger logger = Logger.getLogger(Mapper.class.getName());
	private Section original= null, mapped = null;
	private HashMap<String,String>  forwardMap = new HashMap<String, String>();
	private HashMap<String,String>  reverseMap = new HashMap<String, String>();
	
	/**
	 * Constructor.
	 * @param original A {@link Section} that contains mapping information.
	 */
	public Mapper(Section original) {
		this.original = original;
		try {
			PropertyConfigurator.configure(this.getClass().getResource("/resources/loggingParams.txt"));	
		} catch (Exception e) {
			System.out.println("logger could not be properly configured: "+e);
		}
	}
	/**
	 * Apply the mapping information and return a converted tree.
	 * @return {@link Section} the converted tree.
	 */
	public Section map(){
		try{
			original.resolveAllLinks();//before start of mapping resolve all links and
			original.loadAllIncludes();//load all external information.
			if(mapped == null && original.isRoot()){//the original Section is a root section...
				mapped = new Section();
				mapped.setDocumentAuthor(original.getDocumentAuthor());
				mapped.setDocumentDate(original.getDocumentDate());
				mapped.setDocumentVersion(original.getDocumentVersion());
			}
			for (int i=0; i<original.sectionCount();i++){ // map all subsections
				mapSection(original.getSection(i),mapped);
			}
			mapProperties(original.getRootSection());// map the properties 
			mapped.optimizeTree(); // optimize the tree, i.e. remove empty properties and section that may result from loading terminologies etc.
		}catch (Exception e) {
			e.printStackTrace();
		}
		return mapped;
	}
	/**
	 * Controls the mapping of a {@link Section}.
	 * @param origin {@link Section} the original section that needs to be mapped
	 * @param mappedParent {@link Section} the parent section in the mapped tree.
	 * @throws Exception
	 */
	private void mapSection(Section origin, Section mappedParent) throws Exception{
		Section dest = null; // the destination
		if(origin.getMapping() == null){//no mapping information could be found just create a copy
			dest = new Section(origin.getName(),origin.getType());
			dest.setDefinition(origin.getDefinition());
			dest.setReference(origin.getReference());
		}
		else {// there is mapping information
			URL url = origin.getMapping(); //get the mapping
			String destType = url.getRef(); //find the reference part, i.e. the part following the '#' which is the destination type

			if(destType != null && !destType.isEmpty()){ //there is a destination type; load terminology
				dest = TerminologyManager.instance().loadTerminology(origin.getMapping(), destType);
				dest.setName(origin.getName());
				dest.setReference(origin.getReference());
			}
			else{//there is no type definition: try if an assignment can be uniquely made. 
				dest = TerminologyManager.instance().loadTerminology(origin.getMapping(), null);
				if(dest.getSections().size()==1){//yes, only one type defined in the terminology, use it!
					dest = dest.getSection(0);
					dest.setName(origin.getName());
					dest.setReference(origin.getReference());
				}
				else{//error, not unique, raise an error
					logger.error("\tMapper.mapSection(): cannot uniquely map section: "+origin.getName()+
					". Section skipped! Extend the desired section type to the mapping url as reference (#someType)");
					dest = null;
				}
			}
		}
		if(mappedParent == null){//if there is no section so far
			mappedParent = dest;
			forwardMap.put(origin.getPath(),dest.getPath());
			reverseMap.put(dest.getPath(), origin.getPath());
		}
		else{//otherwise add it
			mappedParent.add(dest);
			forwardMap.put(origin.getPath(),dest.getPath()); //take a note of the origin.
			reverseMap.put(dest.getPath(), origin.getPath());//reverse map (not used, so far)
		}
		for(int i=0;i<origin.sectionCount();i++){//recursively map all subsections.
			mapSection(origin.getSection(i), dest);
		}
	}
	/**
	 *  Map all Properties of the section
	 * @param sec {@link Section} the section.
	 */
	private void mapProperties(Section sec){
		//map the Properties of this section
		for(int i=0;i<sec.propertyCount(); i++){
			mapProperty(sec.getProperty(i));
		}
		//recursive call to map the properties of subsections.
		for(int i=0;i<sec.sectionCount();i++){
			mapProperties(sec.getSection(i));
		}
	}
	/**
	 * Maps a certain property.
	 * @param p {@link Property} the Property.
	 */
	public void mapProperty(Property p){
		//create a copy of the property.
		Property myCopy = null;
		try {
			myCopy = p.copy();
		} catch (IOException e) {
			e.printStackTrace();
		} catch (ClassNotFoundException e) {
			e.printStackTrace();
		}

		Section destination = mapped.getSection(forwardMap.get(p.getParentSection().getPath()));//find the destination section
		if(p.getMapping()== null){ //no mapping defined, just add to destination
			destination.add(myCopy);
		}
		else{// there is mapping information 
			String ref = p.getMapping().getRef(); //get the reference part of the URL
			String destType = null;
			String mapName = null;
			if(ref == null || ref.isEmpty()){// mapping has no reference part
				logger.warn("Property mapping does not containt reference information! Just appending to current section!");
				destination.add(myCopy);
			}
			else if(!ref.contains(":")){// there is a reference part but  no  section type
				mapName = ref;
				logger.warn("Property.mapProperty: reference part of mapping does not indicate the type of destination section! Please change!");
				Section temp = TerminologyManager.instance().loadTerminology(p.getMapping(), null); //load the terminology
			
				if(temp.getSection(0).getProperty(mapName)!=null)// check if terminology contains a matching property, change name
					myCopy.setName(mapName);
				else	//otherwise, keep it
					logger.warn("Property.mapProperty: terminology does not contain a property with the name specidied in the mapping!");
				
				if(temp.sectionCount()>1){// there is more than one section type defined in the terminology --> no unique assignment possible
					logger.error("Property.mapProperty: Could not uniquely map the property due to missing section type in reference part of mapping url.");
				}
				else{//only one type: assuming that this will be the target 
					destType = temp.getSection(0).getType();
					if(destination.getType().equalsIgnoreCase(destType)){//if the destination type matches the terminology type, add the property
						destination.add(myCopy);//add to destination section
					}
					else if(destination.getSectionsByType(destType).size()>0){
						if(destination.getSectionsByType(destType).size()>1)
							logger.error("Cannot uniquely assign property: "+p.getName() +" in section: "+p.getParentSection().getPath());
						else{
							destination.getSectionByType(destType).add(myCopy);
						}
					}
					else if(destination.getParent().getSectionByType(destType)!= null){
						if(destination.getParent().getSectionsByType(destType).size()>1)
							logger.warn("Cannot uniquely assign property: "+p.getName() +" in section: "+p.getParentSection().getPath());
						else{//check if there are multiple dependencies, create subsection and create a link 
							Section destSection = destination.getParent().getSectionByType(destType);
							if(destSection.getRelatedSections(destination.getType()).size()>1){//there are multiple dependencies
								temp.add(myCopy);
								destination.add(temp);
								temp.setLink(destSection.getPath());
							}
							else{//no multiple dependencies, just add.
								destSection.add(myCopy);
							}
						}
					}
//					else if(destination.getParent().getSectionByType(destType)!= null){//else try a sibling section
//						destination.getParent().getSectionByType(destType).add(myCopy);
//					}
					else{// if neither mapped Section nor sibling is of matching type add the terminology
						temp.getSection(0).add(myCopy);
						destination.getParent().add(temp.getSection(0));
					}
				}
			}
			else{//there is a reference part and it contains section-type information
				String type = ref.substring(0, ref.indexOf(":"));
				mapName = ref.substring(ref.indexOf(":")+1);
				Section temp = TerminologyManager.instance().loadTerminology(p.getMapping(), type);
				//check for propertyName 
				if(temp.getProperty(mapName)!=null)
					myCopy.setName(mapName);				
				else
					logger.warn("Property.mapProperty: terminology does not contain a property with the name specidied in the mapping!");
				// figure out the right destination
				if(destination.getType().equalsIgnoreCase(type)){ //if it is the dstination section itself, add property
					destination.add(myCopy);
				}
				else if(destination.getSectionsByType(type).size()>0){
					if(destination.getSectionsByType(type).size()>1)
						logger.error("Cannot uniquely assign property: "+p.getName() +" in section: "+p.getParentSection().getPath());
					else{
						destination.getSectionByType(type).add(myCopy);
					}
				}
				else if(destination.getParent().getSectionByType(type)!= null){
					if(destination.getParent().getSectionsByType(type).size()>1)
						logger.warn("Cannot uniquely assign property: "+p.getName() +" in section: "+p.getParentSection().getPath());
					else{//check if there are unique or multiple dependencies
						Section destSection = destination.getParent().getSectionByType(type);
						if(destSection.getRelatedSections(destination.getType()).size()>1){//there are multiple dependencies
							temp.add(myCopy);
							destination.add(temp);
							temp.setLink(destSection.getPath());
						}
						else{//there is a single dependency
							destSection.add(myCopy);
						}
					}
				}
				else{//neither itself nor sibling is of the given type
					//						System.out.println("\tMapper.mapProperty: neither mappedSection nor sibling is of desired type.");
					if(temp != null){
						temp.add(myCopy);
						destination.getParent().add(temp);
					}
					else{
						logger.error("Property.mapProperty: could not find section of type: "+type+" in the mapped terminology!");
					}
				}
			}
		}
	}

}
