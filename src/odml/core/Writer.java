package odml.core;
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
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.Serializable;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;

import org.apache.log4j.Logger;
import org.jdom.Document;
import org.jdom.Element;
import org.jdom.ProcessingInstruction;
import org.jdom.output.Format;
import org.jdom.output.XMLOutputter;


/**
 * The {@link Writer} class is the other user interface that provides the tools to write 
 * odML metadata files. 
 * 
 * @since 08.2009
 * 
 * @author Jan Grewe, Christine Seitz
 *
 */
public class Writer implements Serializable{
	private static final long serialVersionUID = 146L;
	public static Logger logger = Logger.getLogger(Writer.class.getName()); // for logging
	private boolean asTerminology;
	private Document doc;
	private File file;
	private Section odmlTree = null;
	/**
	 * Creates a writer instance. Lets the Wirter write only those properties that have values.
	 * @param filename {@link String} the full name of the destination file (including path). 
	 * @param rootSection {@link Section} the root Section of the metadata tree.
	 */
	public Writer(String filename, Section rootSection){
		this(new File(filename), rootSection);
	}
		/**
	 * Creates a Writer-instance. Writes only non-empty properties into the metadata files. 
	 * @param file {@link File} the File into which the metadata should be written.
	 * @param rootSection {@link Section}: the rootSection of the odml metadata tree.
	 */
	public Writer(File file, Section rootSection) {
		this(file,rootSection,false);
	}
	/**
	 * Creates a Writer-instance. Setting asTerminology to true lets the writer
	 * write also those properties that have no values as is usually the case for 
	 * terminologies.
	 * @param file {@link File} the File into which the metadata should be written.
	 * @param rootSection {@link Section}: the rootSection of the odml metadata tree.
	 * @param asTerminology {@link Boolean}: if true also emtpy properties (no value) are written, otherwise 
	 * only non-emty properties are written to disc.
	 */
	public Writer(File file, Section rootSection, boolean asTerminology) {
		this.file 		= file;
		this.odmlTree	= rootSection;
		this.asTerminology = asTerminology;
	}

	/**
	 * Write the metadata to disc after the tree has been optimized (linked sections are simplified to reduce
	 * redundancy) and validated against the terminologies.
	 * @param optimize {@link Boolean}: remove empty properties and sections, removes redundancy in linked sections.
	 * @param validate {@link Boolean}: validates the metadata against the terminologies.
	 * @return {@link Boolean}: true if writing succeeded, false otherwise.
	 */
	public boolean write(boolean optimize, boolean validate){
		if(optimize){odmlTree.optimizeTree();}
		if(validate){odmlTree.validateTree();}
		return write();
	}
	/**
	 *  Writes the metadata to disc.
	 * @return {@link Boolean} true if operation was successful, false otherwise.
	 */
	public boolean write(){
		if(odmlTree == null){
			logger.error("Writer.write error: there is no metadata to write!");
			return false;
		}
		if(odmlTree instanceof Section){
			return createDom((Section)odmlTree,asTerminology);
		}
		return false;
	}
	/**
	 * 
	 * @param odMLRoot {@link Section}: the section to start the dom creation.
	 * @param asTemplate {@link boolean}: flag to indicate whether Template is used or not
	 * @return {@link boolean}: true if creating Dom successfully, otherwise false
	 */
	private boolean createDom(Section odMLRoot, boolean asTemplate){
		logger.debug("in createDom\twith RootSection");
		doc = new Document();
		//create processing instruction the last one added is the preferred one
		ProcessingInstruction instr = null;
		ProcessingInstruction altInstr = null;
		if(asTemplate){
			altInstr = new ProcessingInstruction("xml-stylesheet",  "type=\"text/xsl\" href=\"odml.xsl\"");
			instr = new ProcessingInstruction("xml-stylesheet",  "type=\"text/xsl\" href=\"odmlTerms.xsl\"");

		}
		else{
			altInstr = new ProcessingInstruction("xml-stylesheet",  "type=\"text/xsl\" href=\"odmlTerms.xsl\"");
			instr = new ProcessingInstruction("xml-stylesheet",  "type=\"text/xsl\" href=\"odml.xsl\"");
		}
		doc.addContent(instr);
		doc.addContent(altInstr);
		Element rootElement = new Element("odML");
		rootElement.setAttribute("version","1");
		doc.setRootElement(rootElement);
		// if the odMLRoot has properties, a dummy root is added to ensure that everything is written
		Section dummyRoot;
		if (odMLRoot.propertyCount() != 0){
			dummyRoot = new Section();
			dummyRoot.add(odMLRoot);
			dummyRoot.setDocumentAuthor(odMLRoot.getDocumentAuthor());
			dummyRoot.setDocumentDate(odMLRoot.getDocumentDate());
			dummyRoot.setDocumentVersion(odMLRoot.getDocumentVersion());
			dummyRoot.setRepository(odMLRoot.getRepository());
		}
		else {
			dummyRoot = odMLRoot;
		}
		String author 	= dummyRoot.getDocumentAuthor();
		if(author!=null){
			Element authorElement = new Element("author");
			authorElement.setText(author);		
			rootElement.addContent(authorElement);
		}
		String version 	= dummyRoot.getDocumentVersion();
		if(version!=null){
			Element versionElement = new Element("version");
			versionElement.setText(version);		
			rootElement.addContent(versionElement);
		}
		String dateString = null;
		Date date 		= dummyRoot.getDocumentDate();
		SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd"); 
		if(date!= null){
			dateString = sdf.format(date);
		}
		else{
			date = new Date(Calendar.getInstance().getTimeInMillis());
			dateString = sdf.format(date);
		}
		if(dateString != null){
			Element dateElement = new Element("date");
			dateElement.setText(dateString);		
			rootElement.addContent(dateElement);
		}
		URL repository 	= dummyRoot.getRepository();
		if(repository!=null){
			Element repElement = new Element("repository");
			repElement.setText(repository.toString());		
			rootElement.addContent(repElement);
		}	
		for(int i=0;i < dummyRoot.sectionCount();i++){
			appendSection(rootElement, dummyRoot.getSection(i),asTemplate);
		}
		streamToFile(file);
		return true;		
	}
	
	/**
	 * Method to append a section-element to the dom-tree.
	 * @param parent {@link Element}: the parent where the section shall be appended
	 * @param section {@link Section}: the section to append to the parent-element
	 * @param asTemplate {@link boolean}: flag to indicate whether template or not; if template then also writing 
	 * value-information (e.g. unit or type) without having actual value-content
	 */
	private void appendSection(Element parent, Section section, boolean asTemplate){
		logger.debug("in appendSection\twith Section section");
		Element sectionElement 	= new Element("section");
		
		Element type = new Element("type");
		type.setText(section.getType());		
		sectionElement.addContent(type);
		
		Element name = new Element("name");
		name.setText(section.getName());		
		sectionElement.addContent(name);
		
		Element nameDefinition = new Element("definition");
		nameDefinition.setText(section.getDefinition());		
		sectionElement.addContent(nameDefinition);

		Element repository = new Element("repository");
		URL termUrl = section.getRepository();
		if(termUrl != null){
			repository.setText(termUrl.toString());
			sectionElement.addContent(repository);
		}
		
		Element mapping = new Element("mapping");
		URL mapUrl = section.getMapping();
		if(mapUrl != null){
			mapping.setText(mapUrl.toString());
			sectionElement.addContent(mapping);
		}
		Element link = new Element("link");
		String sectionLink = section.getLink();
		if(sectionLink != null){
			link.setText(sectionLink);
			sectionElement.addContent(link);
		}
		Element include = new Element("include");
		String sectionInclude = section.getInclude();
		if(sectionInclude != null){
			include.setText(sectionInclude);
			sectionElement.addContent(include);
		}
		Element reference = new Element("reference");
		String sectionReference = section.getReference();
		if(sectionReference != null){
			reference.setText(sectionReference);
			sectionElement.addContent(reference);
		}
		// append the properties.
		for(int i =0; i<section.propertyCount();i++){
			appendProperty(sectionElement, section.getProperty(i),asTemplate);
		}
		// cycle through the subsections
		for(int i =0; i<section.sectionCount();i++){
			appendSection(sectionElement, section.getSection(i),asTemplate);
		}
		// append to parent
		parent.addContent(sectionElement);
	}
	/**
	 * Appends a property elements to the dom tree. If the property contains more than a single value
	 * a respective number of properties are created.
	 * 
	 * @param parent {@link Element}: the parent Element to which the properties belong.
	 * @param prop {@link Property}: the property to append.
	 * @param terms {@link Terminology}: The terminology that should be used to validate the properties. (non-functional so far)
	 */	
	private void appendProperty(Element parent, Property prop, boolean asTemplate){
		logger.debug("in appendProperty\twith Property and Terminology");

		Element propertyElement = new Element("property");
		//actually write the property
		Element name = new Element("name");
		name.setText(prop.getName());
		propertyElement.addContent(name);
			
		Element nameDefinition = new Element("definition");
		String nameDef = prop.getDefinition();
		if(nameDef!=null){
			nameDefinition.setText(nameDef);
			propertyElement.addContent(nameDefinition);
		}			
		Element dependency = new Element("dependency");
		String dep = prop.getDependency();
		if(dep != null){
			dependency.setText(dep);
			propertyElement.addContent(dependency);
		}

		Element dependencyValue = new Element("dependencyValue");
		String depVal = prop.getDependencyValue();
		if(depVal != null){
			dependencyValue.setText(depVal);
			propertyElement.addContent(dependencyValue);
		}

		Element mapping = new Element("mapping");
		URL mapURL = prop.getMapping();
		if(mapURL != null){
			mapping.setText(mapURL.toString());
			propertyElement.addContent(mapping);
		}

		// appending the values.
		for(int i =0; i<prop.valueCount();i++){
			appendValue(propertyElement, prop.getWholeValue(i), asTemplate);
		}

		//append to the parent			
		parent.addContent(propertyElement);
	}
	
	/**
	 * Appends a value element to the dom tree.
	 * @param parent {@link Element}: the parent Element to which the values belong.
	 * @param prop {@link Value}: the value to append.
	 * @param terms {@link Terminology}: The terminology that should be used to validate the properties. 
	 * (non-functional so far). BUT: if false: not writing values with empty 'name' (value itself)
	 */	
	private void appendValue(Element parent, Value val, boolean asTemplate){
		if(!asTemplate){
			if(val.getContent()==null || val.getContent().toString().isEmpty()){
				return;
			}
		}
		
		Element valueElement = new Element("value");
		if(val.getContent() != null  && (!val.getContent().toString().isEmpty())){
			valueElement.setText(val.getContent().toString());
		}

		Element typeElement = new Element("type");
		String type = val.getType();
		if(type != null && (!type.isEmpty())){
			typeElement.setText(type);
			valueElement.addContent(typeElement);	
		}
		Element unitElement = new Element("unit");
		String unit = val.getUnit();
		if(unit != null && (!unit.isEmpty())){
			unitElement.setText(unit);
			valueElement.addContent(unitElement);
		}
		Element errorElement 	= new Element("uncertainty");
		Object uncertainty = val.getUncertainty();
		if(uncertainty != null && (!uncertainty.toString().isEmpty())){
			errorElement.setText(uncertainty.toString());
			valueElement.addContent(errorElement);
		}	
		Element filenameElement = new Element("filename");
		String filename = val.getFilename();
		if(filename!=null && (!filename.isEmpty())){
			filenameElement.setText(filename);
			valueElement.addContent(filenameElement);
		}	
		Element defElement = new Element("definition");
		String valueDefinition = val.getDefinition();
		if(valueDefinition != null && (!valueDefinition.isEmpty())){
			defElement.setText(valueDefinition);
			valueElement.addContent(defElement);
		}
		Element idElement = new Element("reference");
		String id = val.getReference();
		if(id != null && (!id.isEmpty())){
			idElement.setText(id);
			valueElement.addContent(idElement);
		}			
		Element encoderElement = new Element("encoder");
		String encoder = val.getEncoder();
		if(encoder != null && (!encoder.isEmpty())){
			encoderElement.setText(encoder);
			valueElement.addContent(encoderElement);
		}	
		Element checksumElement = new Element("checksum");
		String checksum = val.getChecksum();
		if(checksum != null && (!checksum.isEmpty())){
			checksumElement.setText(checksum);
			valueElement.addContent(checksumElement);
		}	
		//append to the parent			
		parent.addContent(valueElement);
	}
	/**
	 * Stream the dom tree to file. 
	 * @return boolean returns whether the operation succeeded or not.
	 */
	private boolean streamToFile(File newFile){
		try
		{
			logger.debug("in streamToFile");
			Format frmt = Format.getPrettyFormat().setIndent("    ");
			XMLOutputter outp = new XMLOutputter(frmt);
			FileOutputStream fileStream = new FileOutputStream(newFile);
			if(doc.equals(null)){logger.error("doc empty");}						
			else {
				logger.debug("Zeug in doc: "+doc.toString());
			}			
			outp.output(doc, fileStream);				
		} 
		catch(IOException ie) {
			logger.error("StreamToFile failed: ", ie);
			return false;
		}
		logger.info("StreamToFile successfull");
		return true;
	}
}