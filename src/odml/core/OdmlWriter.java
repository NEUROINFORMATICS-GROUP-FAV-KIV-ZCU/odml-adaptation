package odml.core;

/************************************************************************
 * odML - open metadata Markup Language -
 * Copyright (C) 2009, 2010 Jan Grewe, Jan Benda
 * 
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License (LGPL) as published by
 * the Free Software Foundation; either version 3 of the License, or
 * (at your option) any later version.
 * 
 * odML is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU Lesser General Public License
 * along with this software. If not, see <http://www.gnu.org/licenses/>.
 */
import java.io.*;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.*;
import org.jdom.*;
import org.jdom.output.Format;
import org.jdom.output.XMLOutputter;
import org.slf4j.*;


/**
 * The {@link OdmlWriter} class provides the tools to write odML metadata to streams.
 * <p>
 * Most of the code of this class was adopted from {@link Writer} but the file output was replaced by streams.
 * </p>
 * 
 * @author Jakub Krauz
 */
public class OdmlWriter implements Serializable {

    private static final long serialVersionUID = 1L;

    public static Logger logger = LoggerFactory.getLogger(Writer.class);

    private final boolean asTerminology;

    private Document doc;

    private Section odmlTree = null;

    private final static SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");

    private final static SimpleDateFormat datetimeFormat = new SimpleDateFormat("yyyy-MM-dd hh:mm:ss");

    private final static SimpleDateFormat timeFormat = new SimpleDateFormat("hh:mm:ss");


    /**
     * Creates a writer instance. Lets the Wirter write only those properties that have values.
     * 
     * @param rootSection {@link Section} the root Section of the metadata tree.
     */
    public OdmlWriter(Section rootSection) {
        this(rootSection, false);
    }


    /**
     * Creates a Writer-instance. Setting asTerminology to true lets the writer
     * write also those properties that have no values as is usually the case for
     * terminologies.
     * 
     * @param rootSection {@link Section}: the rootSection of the odml metadata tree.
     * @param asTerminology {@link Boolean}: if true also emtpy properties (no value) are written, otherwise
     *        only non-emty properties are written to disc.
     */
    public OdmlWriter(Section rootSection, boolean asTerminology) {
        this.odmlTree = rootSection;
        this.asTerminology = asTerminology;
    }


    /**
     * Write the metadata to an output stream after the tree has been optimized (linked sections are simplified to reduce
     * redundancy) and validated against the terminologies.
     * 
     * @param stream {@link OutputStream}: output stream to which to write the document
     * @param optimize {@link Boolean}: remove empty properties and sections, removes redundancy in linked sections.
     * @param validate {@link Boolean}: validates the metadata against the terminologies.
     * @return {@link Boolean}: true if writing succeeded, false otherwise.
     */
    public boolean write(OutputStream stream, boolean optimize, boolean validate) {
        if (optimize) {
            odmlTree.optimizeTree();
        }
        if (validate) {
            odmlTree.validateTree();
        }
        return write(stream);
    }


    /**
     * Writes the metadata to disc.
     * 
     * @param stream {@link OutputStream}: output stream to which to write the document
     * @return {@link Boolean} true if operation was successful, false otherwise.
     */
    public boolean write(OutputStream stream) {
        if (odmlTree == null) {
            logger.error("Writer.write error: there is no metadata to write!");
            return false;
        }
        if (odmlTree instanceof Section) {
            if (!createDom(odmlTree, asTerminology))
                return false;
            return writeToStream(stream, doc);
        }
        return false;
    }


    /**
     * 
     * @param odMLRoot {@link Section}: the section to start the dom creation.
     * @param asTerminology {@link boolean}: flag to indicate whether Template is used or not
     * @return {@link boolean}: true if creating Dom successfully, otherwise false
     */
    private boolean createDom(Section odMLRoot, boolean asTerminology) {
        logger.debug("in createDom\twith RootSection");
        doc = new Document();
        // create processing instruction the last one added is the preferred one
        ProcessingInstruction instr = null;
        ProcessingInstruction altInstr = null;
        if (asTerminology) {
            altInstr = new ProcessingInstruction("xml-stylesheet",
                    "type=\"text/xsl\" href=\"odml.xsl\"");
            instr = new ProcessingInstruction("xml-stylesheet",
                    "type=\"text/xsl\" href=\"odmlTerms.xsl\"");

        } else {
            altInstr = new ProcessingInstruction("xml-stylesheet",
                    "type=\"text/xsl\" href=\"odmlTerms.xsl\"");
            instr = new ProcessingInstruction("xml-stylesheet",
                    "type=\"text/xsl\" href=\"odml.xsl\"");
        }
        doc.addContent(instr);
        doc.addContent(altInstr);
        Element rootElement = new Element("odML");
        rootElement.setAttribute("version", "1");
        doc.setRootElement(rootElement);
        // if the odMLRoot has properties, a dummy root is added to ensure that everything is written
        Section dummyRoot;
        if (odMLRoot.propertyCount() != 0) {
            dummyRoot = new Section();
            dummyRoot.add(odMLRoot);
            dummyRoot.setDocumentAuthor(odMLRoot.getDocumentAuthor());
            dummyRoot.setDocumentDate(odMLRoot.getDocumentDate());
            dummyRoot.setDocumentVersion(odMLRoot.getDocumentVersion());
            dummyRoot.setRepository(odMLRoot.getRepository());
        } else {
            dummyRoot = odMLRoot;
        }
        String author = dummyRoot.getDocumentAuthor();
        if (author != null) {
            Element authorElement = new Element("author");
            authorElement.setText(author);
            rootElement.addContent(authorElement);
        }
        String version = dummyRoot.getDocumentVersion();
        if (version != null) {
            Element versionElement = new Element("version");
            versionElement.setText(version);
            rootElement.addContent(versionElement);
        }
        String dateString = null;
        Date date = dummyRoot.getDocumentDate();
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
        if (date != null) {
            dateString = sdf.format(date);
        } else {
            date = new Date(Calendar.getInstance().getTimeInMillis());
            dateString = sdf.format(date);
        }
        if (dateString != null) {
            Element dateElement = new Element("date");
            dateElement.setText(dateString);
            rootElement.addContent(dateElement);
        }
        URL repository = dummyRoot.getRepository();
        if (repository != null) {
            Element repElement = new Element("repository");
            repElement.setText(repository.toString());
            rootElement.addContent(repElement);
        }
        for (int i = 0; i < dummyRoot.sectionCount(); i++) {
            appendSection(rootElement, dummyRoot.getSection(i), asTerminology);
        }

        return true;
    }


    /**
     * Method to append a section-element to the dom-tree.
     * 
     * @param parent {@link Element}: the parent where the section shall be appended
     * @param section {@link Section}: the section to append to the parent-element
     * @param asTemplate {@link boolean}: flag to indicate whether template or not; if template then also writing
     *        value-information (e.g. unit or type) without having actual value-content
     */
    private void appendSection(Element parent, Section section, boolean asTemplate) {
        logger.debug("in appendSection\twith Section section");
        Element sectionElement = new Element("section");

        Element type = new Element("type");
        type.setText(section.getType());
        sectionElement.addContent(type);

        Element name = new Element("name");
        name.setText(section.getName());
        sectionElement.addContent(name);

        String definition = section.getDefinition();
        if (definition != null) {
            Element nameDefinition = new Element("definition");
            nameDefinition.setText(definition);
            sectionElement.addContent(nameDefinition);
        }

        URL termUrl = section.getRepository();
        if (termUrl != null) {
            Element repository = new Element("repository");
            repository.setText(termUrl.toString());
            sectionElement.addContent(repository);
        }

        URL mapUrl = section.getMapping();
        if (mapUrl != null) {
            Element mapping = new Element("mapping");
            mapping.setText(mapUrl.toString());
            sectionElement.addContent(mapping);
        }
        
        String sectionLink = section.getLink();
        if (sectionLink != null) {
            Element link = new Element("link");
            link.setText(sectionLink);
            sectionElement.addContent(link);
        }
        
        String sectionInclude = section.getInclude();
        if (sectionInclude != null) {
            Element include = new Element("include");
            include.setText(sectionInclude);
            sectionElement.addContent(include);
        }
        
        String sectionReference = section.getReference();
        if (sectionReference != null) {
            Element reference = new Element("reference");
            reference.setText(sectionReference);
            sectionElement.addContent(reference);
        }
        
        // append the properties.
        for (int i = 0; i < section.propertyCount(); i++) {
            appendProperty(sectionElement, section.getProperty(i), asTemplate);
        }
        // cycle through the subsections
        for (int i = 0; i < section.sectionCount(); i++) {
            appendSection(sectionElement, section.getSection(i), asTemplate);
        }
        // append to parent
        parent.addContent(sectionElement);
    }


    /**
     * Appends a property elements to the dom tree. Empty properties (those with no values)
     * will only be written to file if the file is to become a terminology.
     * 
     * @param parent {@link Element}: the parent Element to which the properties belong.
     * @param prop {@link Property}: the property to append.
     * @param asTerminology boolean: defines whether the file will be a terminology.
     */
    private void appendProperty(Element parent, Property prop, boolean asTerminology) {
        logger.debug("in appendProperty\twith Property and Terminology");
        if (!asTerminology) {
            prop.removeEmptyValues();
            if (prop.isEmpty()) {
                logger.warn("Writer.appendProperty: Property " + prop.getName()
                        + "is empty and will not be written to file!");
                return;
            }
        }
        
        Element propertyElement = new Element("property");
        Element name = new Element("name");
        name.setText(prop.getName());
        propertyElement.addContent(name);

        String nameDef = prop.getDefinition();
        if (nameDef != null && !nameDef.isEmpty()) {
            Element nameDefinition = new Element("definition");
            nameDefinition.setText(nameDef);
            propertyElement.addContent(nameDefinition);
        }
        
        String dep = prop.getDependency();
        if (dep != null && !dep.isEmpty()) {
            Element dependency = new Element("dependency");
            dependency.setText(dep);
            propertyElement.addContent(dependency);
        }

        String depVal = prop.getDependencyValue();
        if (depVal != null && !depVal.isEmpty()) {
            Element dependencyValue = new Element("dependencyValue");
            dependencyValue.setText(depVal);
            propertyElement.addContent(dependencyValue);
        }

        URL mapURL = prop.getMapping();
        if (mapURL != null) {
            Element mapping = new Element("mapping");
            mapping.setText(mapURL.toString());
            propertyElement.addContent(mapping);
        }

        // appending the values.
        for (int i = 0; i < prop.valueCount(); i++) {
            appendValue(propertyElement, prop.getWholeValue(i), asTerminology);
        }

        // append to the parent
        parent.addContent(propertyElement);
    }


    /**
     * Appends a value element to the dom tree.
     * 
     * @param parent {@link Element}: the parent Element to which the values belong.
     * @param prop {@link Value}: the value to append.
     * @param terms {@link Terminology}: The terminology that should be used to validate the properties.
     *        (non-functional so far). BUT: if false: not writing values with empty 'name' (value itself)
     */
    private void appendValue(Element parent, Value val, boolean asTemplate) {
        if (!asTemplate) {
            if (val.getContent() == null || val.getContent().toString().isEmpty()) { return; }
        }

        Element valueElement = new Element("value");
        if (val.getContent() != null && (!val.getContent().toString().isEmpty())) {
            if (val.getContent() instanceof Date) {
                Date d = (Date) val.getContent();
                if (val.getType().equalsIgnoreCase("date")) {
                    valueElement.setText(dateFormat.format(d));
                } else if (val.getType().equalsIgnoreCase("datetime")) {
                    valueElement.setText(datetimeFormat.format(d));
                } else if (val.getType().equalsIgnoreCase("time")) {
                    valueElement.setText(timeFormat.format(d));
                } else {
                    valueElement.setText(val.getContent().toString());
                }
            } else {
                valueElement.setText(val.getContent().toString());
            }
        }

        String type = val.getType();
        if (type != null && (!type.isEmpty())) {
            Element typeElement = new Element("type");
            typeElement.setText(type);
            valueElement.addContent(typeElement);
        }
        
        String unit = val.getUnit();
        if (unit != null && (!unit.isEmpty())) {
            Element unitElement = new Element("unit");
            unitElement.setText(unit);
            valueElement.addContent(unitElement);
        }
        
        Object uncertainty = val.getUncertainty();
        if (uncertainty != null && (!uncertainty.toString().isEmpty())) {
            Element errorElement = new Element("uncertainty");
            errorElement.setText(uncertainty.toString());
            valueElement.addContent(errorElement);
        }
        
        String filename = val.getFilename();
        if (filename != null && (!filename.isEmpty())) {
            Element filenameElement = new Element("filename");
            filenameElement.setText(filename);
            valueElement.addContent(filenameElement);
        }
        
        String valueDefinition = val.getDefinition();
        if (valueDefinition != null && (!valueDefinition.isEmpty())) {
            Element defElement = new Element("definition");
            defElement.setText(valueDefinition);
            valueElement.addContent(defElement);
        }
        
        String id = val.getReference();
        if (id != null && (!id.isEmpty())) {
            Element idElement = new Element("reference");
            idElement.setText(id);
            valueElement.addContent(idElement);
        }
        
        String encoder = val.getEncoder();
        if (encoder != null && (!encoder.isEmpty())) {
            Element encoderElement = new Element("encoder");
            encoderElement.setText(encoder);
            valueElement.addContent(encoderElement);
        }
        
        String checksum = val.getChecksum();
        if (checksum != null && (!checksum.isEmpty())) {
            Element checksumElement = new Element("checksum");
            checksumElement.setText(checksum);
            valueElement.addContent(checksumElement);
        }
        
        // append to the parent
        parent.addContent(valueElement);
    }
    
    
    private boolean writeToStream(OutputStream stream, Document doc) {
        if (doc == null) {
            logger.error("doc empty");
            return false;
        }
        try {
            Format frmt = Format.getPrettyFormat().setIndent("  ");
            XMLOutputter outp = new XMLOutputter(frmt);
            outp.output(doc, stream);
        } catch (IOException ie) {
            logger.error("Write to stream failed: ", ie);
            return false;
        }
        return true;
    }
    
}
