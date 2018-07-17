/*
 * Copyright Public Record Office Victoria 2018
 * Licensed under the CC-BY license http://creativecommons.org/licenses/by/3.0/au/
 * Author Andrew Waugh
 * Version 1.0 February 2018
 */
package VPA;

/**
 * V3Process
 *
 * This class processes a V3 VEO, validates it, and generates the required
 * packages for the DAS, AMS, & SAMS.
 */
import VERSCommon.AppFatal;
import VERSCommon.AppError;
import VERSCommon.HandleElement;
import VERSCommon.XMLParser;
import VERSCommon.XMLConsumer;
import VEOAnalysis.VEOAnalysis;
import VERSCommon.VEOError;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import org.xml.sax.*;

public class V3Process {

    private final Packages packages;      // Utility class to create the various packages
    private final VEOAnalysis va;         // Analysis package
    private final VEOContentParser vcp;   // Parser to parse the VEOContent.xml file
    private final VEOHistoryParser vhp;   // Parser to parse the VEOHistory.xml file
    private final PIDService ps;          // Class to encapsulate the PID service

    // global variables storing information about this export (as a whole)
    private final StringBuilder log1;      // place to capture logging

    private final static Logger LOG = Logger.getLogger("VPA.V3Process");

    /**
     * Default constructor
     *
     * @param ps encapsulation of the PID service used to mint PIDS
     * @param outputDir directory in which to create the packages from this VEO
     * @param schemaDir directory in which the schema information for V3 can be
     * found
     * @param packages methods used to create the packages
     * @param logLevel logging level (INFO = verbose, FINE = debug)
     * @throws AppFatal if a system error occurred
     */
    public V3Process(PIDService ps, Path outputDir, Path schemaDir, Packages packages, Level logLevel) throws AppFatal {
        LogHandler handlr;
        boolean verbose, debug;

        this.ps = ps;
        this.packages = packages;

        // set up logging
        log1 = new StringBuilder();
        handlr = new LogHandler(log1);

        if (logLevel == Level.FINE) {
            verbose = true;
            debug = true;
        } else if (logLevel == Level.INFO) {
            verbose = true;
            debug = false;
        } else {
            verbose = false;
            debug = false;
        }

        // set up analysis code
        try {
            va = new VEOAnalysis(schemaDir, outputDir, handlr, false, true, false, true, verbose, debug, true);
        } catch (VEOError ve) {
            throw new AppFatal(ve.getMessage());
        }

        // set up the parsers
        vcp = new VEOContentParser();
        vhp = new VEOHistoryParser();
    }

    /**
     * Log Handler to capture Log entries into a StringBuilder. Note that the
     * calling method passes in the StringBuilder to use.
     */
    private class LogHandler extends Handler {

        private final StringBuilder out;

        public LogHandler(StringBuilder out) {
            super();
            this.out = out;
        }

        @Override
        public void publish(LogRecord logRecord) {
            out.append(logRecord.getLevel());
            out.append(": ");
            out.append(logRecord.getMessage());
        }

        @Override
        public void flush() {
        }

        @Override
        public void close() {
        }
    }

    /**
     * Process VEO
     *
     * This method opens an XML file and calls SAX to parse it looking for
     * information
     *
     * @param setMetadata metadata about the set as a whole
     * @param veo	the file to parse
     * @param recordName the name of the Information Object to be produced
     * @param packageDir directory in which to create the packages
     * @return a string containing a description of the result of processing the
     * VEO
     * @throws AppFatal if a system error occurred
     * @throws AppError if an unexpected error occurred processing this VEO
     */
    public VEOResult process(String setMetadata, Path veo, String recordName, Path packageDir) throws AppFatal, AppError {
        ArrayList<Event> events;
        ArrayList<InformationObject> ios;
        Path xmlFile;
        String veoPID;
        VEOResult res;
        Path veoDir;
        int i;

        // check parameters
        if (veo == null) {
            throw new AppError("V3Process.process: Passed null VEO file to be processed");
        }
        if (recordName == null) {
            throw new AppError("V3Process.process: Passed null recordName");
        }
        LOG.log(Level.INFO, "Processing ''{0}''", new Object[]{veo.toAbsolutePath().toString()});

        // result
        res = new VEOResult(VEOResult.V3_VEO);
        res.packages = packageDir;

        // reset log1
        log1.setLength(0);

        ios = null;
        events = null;
        try {
            // unpack & test the VEO
            veoDir = va.testVEO(veo.toString(), res.packages);
            res.success = va.isErrorFree();

            // parse the VEOHistory.xml file
            xmlFile = veoDir.resolve("VEOHistory.xml");
            events = vhp.parse(xmlFile);
            events.add(Event.Ingest());
            events.add(Event.CustodyAccepted());

            // parse the VEOContent.xml file
            xmlFile = veoDir.resolve("VEOContent.xml");
            ios = vcp.parse(xmlFile, veoDir, veo);

            // assign the PIDs
            veoPID = ps.mint();
            for (i = 0; i < ios.size(); i++) {
                ios.get(i).assignPIDs(veoPID);
            }

            // create AMS and SAMS outputs
            packages.createAMSPackage(setMetadata, ios, events);
            packages.createSAMSPackage(ios);
            packages.createDASPackage(veo, veoPID);
        } catch (AppFatal ae) {
            res.free();
            free(ios, events);
            throw ae;
        } catch (AppError | VEOError ve) {
            finishResult(res, false, ve.getMessage());
            free(ios, events);
            return res;
        }

        finishResult(res, true, null);
        free(ios, events);
        return res;
    }

    /**
     * Finish result
     */
    private void finishResult(VEOResult res, boolean success, String desc) {
        res.success = success;
        if (desc != null) {
            log1.append(desc);
        }
        res.result = log1.toString();
        res.timeProcEnded = Instant.now();
    }

    /**
     * Free the internally allocated memory
     *
     * @param ios
     */
    private void free(ArrayList<InformationObject> ios, ArrayList<Event> events) {
        int i;
        InformationObject io1;
        Event e;

        if (ios != null) {
            for (i = 0; i < ios.size(); i++) {
                io1 = ios.get(i);
                io1.free();
            }
            ios = null;
        }
        if (events != null) {
            for (i = 0; i < events.size(); i++) {
                e = events.get(i);
                e.free();
            }
            events = null;
        }
    }

    /**
     * Private class to encapsulate reading the VEOHistory.xml file from this
     * VEO
     */
    private class VEOHistoryParser implements XMLConsumer {

        private final XMLParser parser;
        private ArrayList<Event> events;        // list of events
        private Event event;                    // current vers:Event being parsed

        public VEOHistoryParser() throws AppFatal {
            parser = new XMLParser(this);
            events = null;
            event = null;
        }

        /**
         * Parse the file
         *
         * @return a list of events in the history of the VEO
         */
        private ArrayList<Event> parse(Path xmlFile) throws AppError, AppFatal {
            events = new ArrayList<>();
            event = null;

            // parse
            parser.parse(xmlFile);

            // free everything about processing the VEO document
            return events;
        }

        /**
         * SAX Events captured
         */
        private final static String VEOCONTENTPREFIX = "vers:VEOHistory/";

        /**
         * Start of element
         *
         * This event is called when the parser finds a new element. The element
         * name is pushed onto the stack. The stack keeps the element path from
         * the root of the parse tree to the current element. We then check this
         * path to see if the current element matches one of the elementName we
         * are interested in (the parent and grandparent of this element may be
         * checked on the stack to check the contex). If they match, we start
         * recording the element value.
         *
         * @param eFound
         * @param attributes
         * @throws SAXException
         */
        @Override
        public HandleElement startElement(String eFound, Attributes attributes)
                throws SAXException {
            HandleElement wtdwv;

            // match a prefix
            if (eFound.startsWith(VEOCONTENTPREFIX)) {
                eFound = eFound.substring(VEOCONTENTPREFIX.length());
            }

            // match the path to see if do we do something special?
            switch (eFound) {
                case "vers:Event":
                    event = new Event();
                    events.add(event);
                    wtdwv = null;
                    break;
                case "vers:Event/vers:EventDateTime":
                case "vers:Event/vers:EventType":
                case "vers:Event/vers:Initiator":
                case "vers:Event/vers:Description":
                    wtdwv = new HandleElement(HandleElement.VALUE_TO_STRING);
                    break;
                default:
                    wtdwv = null;
                    break;
            }
            return wtdwv;
        }

        /**
         * End of an element
         *
         * Found the end of an element. Pop the element from the top of the
         * stack. If recording, stop, and store the recorded element. If a
         * vers:FileMetadata or vers:RecordMetadata element has been finished,
         * throw a SAX XMLParser Error to force the parse to terminate.
         *
         * @param eFound
         * @param value
         * @throws org.xml.sax.SAXException
         */
        @Override
        public void endElement(String eFound, String value, String element) throws SAXException {

            // match a prefix
            if (eFound.startsWith(VEOCONTENTPREFIX)) {
                eFound = eFound.substring(VEOCONTENTPREFIX.length());
            }

            // if recording store element value in appropriate global variable
            switch (eFound) {
                case "vers:Event":
                    event = null;
                    break;
                case "vers:Event/vers:EventDateTime":
                    event.timestamp = value;
                    break;
                case "vers:Event/vers:EventType":
                    event.eventType = value;
                    break;
                case "vers:Event/vers:Initiator":
                    event.initiator = value;
                    break;
                case "vers:Event/vers:Description":
                    event.description = value;
                    break;
                default:
                    break;
            }
        }
    }

    /**
     * Private class to encapsulate reading the VEOContent.xml file from this
     * VEO
     */
    private class VEOContentParser implements XMLConsumer {

        private final XMLParser parser;
        private Path veoFile;                   // file name of VEO
        private String id;                      // identifier of this record element
        private Path veoDir;                    // place to put the VEO content
        private String veoPID;                  // VEO-PID assigned to this VEO
        private ArrayList<InformationObject> ios;  // list of Information Objects
        private InformationObject io;           // current vers:InformationObject being parsed
        private MetadataPackage mp;             // current vers:MetadataPackage being parsed
        private InformationPiece ip;            // current vers:InforamtionPiece being parsed
        private ContentFile cf;                 // current vers:ContentFile being parsed
        private int ipCnt;                      // count of Information Pieces seen in this IO
        private int mpCnt;                      // count of Metadata Packages seen in this IO
        private int cfCnt;                      // count of Content Files seen in this IP
        private int seqNo;                      // sequence number of Content Files in this VEO
        private boolean aglsMP;                 // true if found an AGLS metadata package
        private boolean anzs5478MP;             // true if found an ANZS 5478 metadata package
        private String idValue;                 // value of an Identifier in an ANZS 5478 metadata package
        private String idScheme;                // scheme for value of an Identifier in an ANZS 5478 metadata package
        private String retAndDispAuthority;     // authority that issued RDA

        public VEOContentParser() throws AppFatal {
            parser = new XMLParser(this);
            veoFile = null;
            id = null;
            veoDir = null;
            veoPID = null;
            ios = null;
            io = null;
            mp = null;
            ip = null;
            cf = null;
            ipCnt = 0;
            mpCnt = 0;
            cfCnt = 0;
            seqNo = 0;
            aglsMP = false;
            anzs5478MP = false;
            idValue = null;
            idScheme = null;
            retAndDispAuthority = null;
        }

        /**
         * Parse an XMLContent.xml file to extract the Information Objects
         *
         * @param xmlFile the patch of the XMLContent.xml file
         * @param veoDir place to put the VEO content
         * @param veo the path of the enclosing VEO
         * @return an array of Information Objects from the XMLContent.xml file
         * @throws AppError something happened that kill processing the VEO
         * @throws AppFatal something happened and its not worth continuing
         */
        private ArrayList<InformationObject> parse(Path xmlFile, Path veoDir, Path veo) throws AppError, AppFatal {

            // set up parse
            this.veoFile = veo;
            id = null;
            this.veoDir = veoDir;
            ios = new ArrayList<>();
            io = null;
            mp = null;
            ip = null;
            cf = null;
            ipCnt = 0;
            mpCnt = 0;
            cfCnt = 0;
            seqNo = 0;
            aglsMP = false;
            anzs5478MP = false;
            idValue = null;
            idScheme = null;
            retAndDispAuthority = null;

            // assign a VEO-PID to this VEO
            veoPID = ps.mint();

            // parse
            parser.parse(xmlFile);

            // free everything about processing the VEO document
            return ios;
        }

        /**
         * SAX Events captured
         */
        private final static String VEOCONTENTPREFIX = "vers:VEOContent/";

        /**
         * Start of element
         *
         * This event is called when the parser finds a new element. The element
         * name is pushed onto the stack. The stack keeps the element path from
         * the root of the parse tree to the current element. We then check this
         * path to see if the current element matches one of the elementName we
         * are interested in (the parent and grandparent of this element may be
         * checked on the stack to check the contex). If they match, we start
         * recording the element value.
         *
         * @param eFound
         * @param attributes
         * @throws SAXException
         */
        @Override
        public HandleElement startElement(String eFound, Attributes attributes)
                throws SAXException {
            HandleElement wtdwv;

            // match a prefix
            if (eFound.startsWith(VEOCONTENTPREFIX)) {
                eFound = eFound.substring(VEOCONTENTPREFIX.length());
            }

            // match the path to see if do we do something special?
            switch (eFound) {
                case "vers:InformationObject":
                    io = new InformationObject(ps, 0);
                    ios.add(io);
                    io.veoVersion = "V3";
                    io.veoFileName = veoFile.getFileName().toString();
                    wtdwv = null;
                    break;
                case "vers:InformationObject/vers:InformationObjectType":
                case "vers:InformationObject/vers:InformationObjectDepth":
                    wtdwv = new HandleElement(HandleElement.VALUE_TO_STRING);
                    break;
                case "vers:InformationObject/vers:MetadataPackage":
                    mp = io.addMetadataPackage();
                    mpCnt++;
                    mp.id = mpCnt;
                    aglsMP = false;
                    wtdwv = new HandleElement(HandleElement.ELEMENT_TO_STRING);
                    break;
                case "vers:InformationObject/vers:MetadataPackage/vers:MetadataSchemaIdentifier":
                case "vers:InformationObject/vers:MetadataPackage/vers:MetadataSyntaxIdentifier":
                    wtdwv = new HandleElement(HandleElement.VALUE_TO_STRING);
                    break;
                case "vers:InformationObject/vers:MetadataPackage/rdf:RDF/rdf:Description/dcterms:date":
                case "vers:InformationObject/vers:MetadataPackage/rdf:RDF/rdf:Description/dcterms:created":
                case "vers:InformationObject/vers:MetadataPackage/rdf:RDF/rdf:Description/dcterms:title":
                case "vers:InformationObject/vers:MetadataPackage/rdf:RDF/rdf:Description/dcterms:identifier":
                case "vers:InformationObject/vers:MetadataPackage/rdf:RDF/rdf:Description/dcterms:description":
                case "vers:InformationObject/vers:MetadataPackage/rdf:RDF/rdf:Description/dcterms:relation":
                case "vers:InformationObject/vers:MetadataPackage/rdf:RDF/rdf:Description/dcterms:conformsTo":
                case "vers:InformationObject/vers:MetadataPackage/rdf:RDF/rdf:Description/dcterms:hasFormat":
                case "vers:InformationObject/vers:MetadataPackage/rdf:RDF/rdf:Description/dcterms:hasPart":
                case "vers:InformationObject/vers:MetadataPackage/rdf:RDF/rdf:Description/dcterms:hasVersion":
                case "vers:InformationObject/vers:MetadataPackage/rdf:RDF/rdf:Description/aglsterms:isBasisFor":
                case "vers:InformationObject/vers:MetadataPackage/rdf:RDF/rdf:Description/aglsterms:isBasedOn":
                case "vers:InformationObject/vers:MetadataPackage/rdf:RDF/rdf:Description/dcterms:isFormatOf":
                case "vers:InformationObject/vers:MetadataPackage/rdf:RDF/rdf:Description/dcterms:isPartOf":
                case "vers:InformationObject/vers:MetadataPackage/rdf:RDF/rdf:Description/dcterms:isReferencedBy":
                case "vers:InformationObject/vers:MetadataPackage/rdf:RDF/rdf:Description/dcterms:isReplacedBy":
                case "vers:InformationObject/vers:MetadataPackage/rdf:RDF/rdf:Description/dcterms:isVersionOf":
                case "vers:InformationObject/vers:MetadataPackage/rdf:RDF/rdf:Description/dcterms:replaces":
                case "vers:InformationObject/vers:MetadataPackage/rdf:RDF/rdf:Description/dcterms:requires":
                    if (aglsMP) {
                        wtdwv = new HandleElement(HandleElement.VALUE_TO_STRING);
                    } else {
                        wtdwv = null;
                    }
                    break;
                case "vers:InformationObject/vers:MetadataPackage/rdf:RDF/anzs5478:Record/anzs5478:Identifier":
                    idValue = null;
                    idScheme = null;
                    wtdwv = null;
                    break;
                case "vers:InformationObject/vers:MetadataPackage/rdf:RDF/anzs5478:Record/anzs5478:Identifier/anzs5478:IdentifierString":
                case "vers:InformationObject/vers:MetadataPackage/rdf:RDF/anzs5478:Record/anzs5478:Identifier/anzs5478:IdentifierScheme":
                case "vers:InformationObject/vers:MetadataPackage/rdf:RDF/anzs5478:Record/anzs5478:Name/anzs5478:NameWords":
                case "vers:InformationObject/vers:MetadataPackage/rdf:RDF/anzs5478:Record/anzs5478:DateRange/anzs5478:StartDate":
                case "vers:InformationObject/vers:MetadataPackage/rdf:RDF/anzs5478:Record/anzs5478:Description":
                case "vers:InformationObject/vers:MetadataPackage/rdf:RDF/anzs5478:Record/anzs5478:Coverage/anzs5478:JurisdictionalCoverage":
                case "vers:InformationObject/vers:MetadataPackage/rdf:RDF/anzs5478:Record/anzs5478:Coverage/anzs5478:SpatialCoverage":
                case "vers:InformationObject/vers:MetadataPackage/rdf:RDF/anzs5478:Record/anzs5478:Disposal/anzs5478:RetentionAndDisposalAuthority":
                case "vers:InformationObject/vers:MetadataPackage/rdf:RDF/anzs5478:Record/anzs5478:Disposal/anzs5478:DisposalClass":
                    // todo other types of entity?
                    if (anzs5478MP) {
                        wtdwv = new HandleElement(HandleElement.VALUE_TO_STRING);
                    } else {
                        wtdwv = null;
                    }
                    break;
                case "vers:InformationObject/vers:InformationPiece":
                    ip = io.addInformationPiece();
                    ipCnt++;
                    ip.seqNbr = ipCnt;
                    cfCnt = 0;
                    wtdwv = null;
                    break;
                case "vers:InformationObject/vers:InformationPiece/vers:Label":
                    wtdwv = new HandleElement(HandleElement.VALUE_TO_STRING);
                    break;
                case "vers:InformationObject/vers:InformationPiece/vers:ContentFile":
                    cf = ip.addContentFile();
                    cfCnt++;
                    cf.seqNbr = cfCnt;
                    wtdwv = null;
                    break;
                case "vers:InformationObject/vers:InformationPiece/vers:ContentFile/vers:PathName":
                    wtdwv = new HandleElement(HandleElement.VALUE_TO_STRING);
                    break;
                default:
                    wtdwv = null;
                    break;
            }
            return wtdwv;
        }

        /**
         * End of an element
         *
         * Found the end of an element. Pop the element from the top of the
         * stack. If recording, stop, and store the recorded element. If a
         * vers:FileMetadata or vers:RecordMetadata element has been finished,
         * throw a SAX XMLParser Error to force the parse to terminate.
         *
         * @param eFound
         * @param value
         * @throws org.xml.sax.SAXException
         */
        @Override
        public void endElement(String eFound, String value, String element) throws SAXException {
            String s;
            int i, depth;
            InformationObject parent;

            // match a prefix
            if (eFound.startsWith(VEOCONTENTPREFIX)) {
                eFound = eFound.substring(VEOCONTENTPREFIX.length());
            }

            // if recording store element value in appropriate global variable
            switch (eFound) {
                case "vers:InformationObject":
                    // io.xmlContent = out.toString();
                    // make link between ios
                    break;
                case "vers:InformationObject/vers:InformationObjectType":
                    io.label = value;
                    break;
                case "vers:InformationObject/vers:InformationObjectDepth":
                    depth = 0;
                    try {
                        depth = Integer.parseInt(value);
                    } catch (NumberFormatException nfe) {
                        // complain
                    }
                    io.depth = depth;

                    // reconstruct the IO tree from the depth
                    if (ios.size() < 2 || depth == 0) {
                        io.parent = null;
                    } else {
                        for (i = ios.size() - 1; i >= 0; i--) {
                            parent = ios.get(i);
                            if (parent.depth == depth - 1) {
                                io.parent = parent;
                                parent.children.add(io);
                                break;
                            }
                        }
                    }
                    break;
                case "vers:InformationObject/vers:MetadataPackage":
                    mp.content = element;
                    break;
                case "vers:InformationObject/vers:MetadataPackage/vers:MetadataSchemaIdentifier":
                    mp.schema = value;
                    if (value.equals("http://prov.vic.gov.au/vers/schema/AGLS")) {
                        aglsMP = true;
                    }
                    if (value.equals("http://www.vic.gov.au/blog/wp-content/uploads/2013/11/AGLS-Victoria-2011-V4-Final-2011.pdf")) {
                        anzs5478MP = true;
                    }
                    break;
                case "vers:InformationObject/vers:MetadataPackage/vers:MetadataSyntaxIdentifier":
                    mp.syntax = value;
                    break;
                case "vers:InformationObject/vers:MetadataPackage/rdf:RDF/rdf:Description/dcterms:date":
                    // only use plain date if we haven't seen a more precise date
                    if (io.dateCreated == null) {
                        io.dateCreated = value;
                    }
                    break;
                case "vers:InformationObject/vers:MetadataPackage/rdf:RDF/rdf:Description/dcterms:created":
                    // could have multiple dates, choose first dateCreated found unless we find a longer
                    // (more precise) date
                    if (io.dateCreated == null || io.dateCreated.length() < value.length()) {
                        io.dateCreated = value;
                    }
                    break;
                case "vers:InformationObject/vers:MetadataPackage/rdf:RDF/rdf:Description/dcterms:title":
                    io.title = value;
                    break;
                case "vers:InformationObject/vers:MetadataPackage/rdf:RDF/rdf:Description/dcterms:description":
                    io.descriptions.add(value);
                    break;
                case "vers:InformationObject/vers:MetadataPackage/rdf:RDF/rdf:Description/dcterms:identifier":
                    io.addIdentifier(value, null);
                    break;
                case "vers:InformationObject/vers:MetadataPackage/rdf:RDF/rdf:Description/dcterms:relation":
                    addAGLSRelation("Relation", value);
                    break;
                case "vers:InformationObject/vers:MetadataPackage/rdf:RDF/rdf:Description/dcterms:conformsTo":
                    addAGLSRelation("conformsTo", value);
                    break;
                case "vers:InformationObject/vers:MetadataPackage/rdf:RDF/rdf:Description/dcterms:hasFormat":
                    addAGLSRelation("hasFormat", value);
                    break;
                case "vers:InformationObject/vers:MetadataPackage/rdf:RDF/rdf:Description/dcterms:hasPart":
                    addAGLSRelation("hasPart", value);
                    break;
                case "vers:InformationObject/vers:MetadataPackage/rdf:RDF/rdf:Description/dcterms:hasVersion":
                    addAGLSRelation("hasVersion", value);
                    break;
                case "vers:InformationObject/vers:MetadataPackage/rdf:RDF/rdf:Description/aglsterms:isBasisFor":
                    addAGLSRelation("isBasisFor", value);
                    break;
                case "vers:InformationObject/vers:MetadataPackage/rdf:RDF/rdf:Description/aglsterms:isBasedOn":
                    addAGLSRelation("isBasedOn", value);
                    break;
                case "vers:InformationObject/vers:MetadataPackage/rdf:RDF/rdf:Description/dcterms:isFormatOf":
                    addAGLSRelation("isFormatOf", value);
                    break;
                case "vers:InformationObject/vers:MetadataPackage/rdf:RDF/rdf:Description/dcterms:isPartOf":
                    addAGLSRelation("isPartOf", value);
                    break;
                case "vers:InformationObject/vers:MetadataPackage/rdf:RDF/rdf:Description/dcterms:isReferencedBy":
                    addAGLSRelation("isReferencedBy", value);
                    break;
                case "vers:InformationObject/vers:MetadataPackage/rdf:RDF/rdf:Description/dcterms:isReplacedBy":
                    addAGLSRelation("isReplacedBy", value);
                    break;
                case "vers:InformationObject/vers:MetadataPackage/rdf:RDF/rdf:Description/dcterms:isVersionOf":
                    addAGLSRelation("isVersionOf", value);
                    break;
                case "vers:InformationObject/vers:MetadataPackage/rdf:RDF/rdf:Description/dcterms:replaces":
                    addAGLSRelation("replaces", value);
                    break;
                case "vers:InformationObject/vers:MetadataPackage/rdf:RDF/rdf:Description/dcterms:requires":
                    addAGLSRelation("requires", value);
                    break;
                case "vers:InformationObject/vers:MetadataPackage/rdf:RDF/anzs5478:Record/anzs5478:Identifier/anzs5478:IdentifierString":
                    idValue = value;
                    break;
                case "vers:InformationObject/vers:MetadataPackage/rdf:RDF/anzs5478:Record/anzs5478:Identifier/anzs5478:IdentifierScheme":
                    idScheme = value;
                    break;
                case "vers:InformationObject/vers:MetadataPackage/rdf:RDF/anzs5478:Record/anzs5478:Identifier":
                    io.addIdentifier(idValue, idScheme);
                    idValue = null;
                    idScheme = null;
                    break;
                case "vers:InformationObject/vers:MetadataPackage/rdf:RDF/anzs5478:Record/anzs5478:Name/anzs5478:NameWords":
                    io.title = value;
                    break;
                case "vers:InformationObject/vers:MetadataPackage/rdf:RDF/anzs5478:Record/anzs5478:DateRange/anzs5478:StartDate":
                    // could have multiple dates, choose first dateCreated found unless we find a longer
                    // (more precise) date
                    if (io.dateCreated == null || io.dateCreated.length() < value.length()) {
                        io.dateCreated = value;
                    }
                    break;
                case "vers:InformationObject/vers:MetadataPackage/rdf:RDF/anzs5478:Record/anzs5478:Description":
                    io.descriptions.add(value);
                    break;
                case "vers:InformationObject/vers:MetadataPackage/rdf:RDF/anzs5478:Record/anzs5478:Coverage/anzs5478:JurisdictionalCoverage":
                    io.jurisdictionalCoverage.add(value);
                    break;
                case "vers:InformationObject/vers:MetadataPackage/rdf:RDF/anzs5478:Record/anzs5478:Coverage/anzs5478:SpatialCoverage":
                    io.spatialCoverage.add(value);
                    break;
                case "vers:InformationObject/vers:MetadataPackage/rdf:RDF/anzs5478:Record/anzs5478:Disposal/anzs5478:RetentionAndDisposalAuthority":
                    retAndDispAuthority = value;
                    break;
                case "vers:InformationObject/vers:MetadataPackage/rdf:RDF/anzs5478:Record/anzs5478:Disposal/anzs5478:DisposalClass":
                    io.disposalAuthorisations.add("{ \"authority\": \"" + retAndDispAuthority + "\", \"disposalClass\": \"" + value + "\"}");
                    retAndDispAuthority = null;
                    break;
                case "vers:InformationObject/vers:InformationPiece/vers:Label":
                    ip.label = value;
                    break;
                case "vers:InformationObject/vers:InformationPiece/vers:ContentFile/vers:PathName":
                    String safe = value.replaceAll("\\\\", "/");
                    cf.fileLocation = veoDir.resolve(safe);
                    s = cf.fileLocation.getFileName().toString();
                    i = s.lastIndexOf(".");
                    if (i == -1) {
                        cf.fileExt = "unknown";
                    } else {
                        cf.fileExt = s.substring(i);
                    }
                    cf.sourceFileName = value;
                    cf.seqNbr = seqNo;
                    seqNo++;

                    // get file size
                    try {
                        cf.fileSize = Files.size(cf.fileLocation);
                    } catch (IOException ioe) {
                        LOG.log(Level.INFO, "V2Process.V2VEOParser.endElement(): failed getting size of file: ", ioe.getMessage());
                        cf.fileSize = 0;
                    }
                    break;
                default:
                    break;
            }
        }

        /**
         * Add an AGLS relationship to an IO
         *
         * @param type type of relationship
         * @param target other end of the relationship
         */
        private void addAGLSRelation(String type, String target) {
            int i, j;
            Relationship r;

            // search to see if this relationship type has already been seen
            // if so, add the target to that relationship
            for (i = 0; i < io.relations.size(); i++) {
                r = io.relations.get(i);
                for (j = 0; j < r.types.size(); j++) {
                    if (r.types.get(j).equals(type)) {
                        r.targetIds.add(target);
                        break;
                    }
                }
                if (j < r.types.size()) {
                    break;
                }
            }

            // doesn't already exist, so add a new relationship
            if (i == io.relations.size()) {
                io.relations.add(new Relationship(type, target, null));
            }
        }
    }
}
