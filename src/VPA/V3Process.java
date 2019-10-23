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
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.ArrayList;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;

public final class V3Process {

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
     * calling method passes in the StringBuilder to use. This handler is used
     * to capture the output of the VERS3 processing software.
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
     * @param pids VEO and IO PIDS from original processing. This must be null
     * for the initial ingest
     * @return a VEOResult containing the results of the processing
     * @throws AppFatal if a system error occurred
     * @throws AppError if an unexpected error occurred processing this VEO
     */
    public VEOResult process(JSONObject setMetadata, Path veo, String recordName, Path packageDir, JSONObject pids) throws AppFatal, AppError {
        ArrayList<Event> events;
        ArrayList<InformationObject> ios;
        InformationObject io;
        Event e;
        Path xmlFile;
        String veoPID;
        Path veoDir;
        int i;
        Instant started;
        boolean success;

        // check parameters
        if (veo == null) {
            throw new AppError("V3Process.process(): Passed null VEO file to be processed");
        }
        if (recordName == null) {
            throw new AppError("V3Process.process(): Passed null recordName");
        }
        if (packageDir == null) {
            throw new AppError("V3Process.process(): Passed null package directory");
        }
        LOG.log(Level.INFO, "Processing ''{0}''", new Object[]{veo.toAbsolutePath().toString()});
        started = Instant.now();

        // reset log1
        log1.setLength(0);

        ios = null;
        events = null;
        success = true;
        try {
            // unpack & test the VEO
            veoDir = va.testVEO(veo.toString(), packageDir);
            success = va.isErrorFree();

            // if VEO tested ok, do the rest of the processing...
            if (success) {

                // parse the VEOHistory.xml file & add the ingest and custody accepted events
                xmlFile = veoDir.resolve("VEOHistory.xml");
                events = vhp.parse(xmlFile);
                events.add(Event.Ingest());
                events.add(Event.CustodyAccepted());

                // parse the VEOContent.xml file and get the Record Items (IOs)
                xmlFile = veoDir.resolve("VEOContent.xml");
                ios = vcp.parse(xmlFile, veoDir, veo);

                // assign the PIDs to the VEO and the Record Items
                if (pids == null) {
                    veoPID = ps.mint();
                    for (i = 0; i < ios.size(); i++) {
                        ios.get(i).assignPIDs(veoPID);
                    }
                } else {
                    veoPID = (String) pids.get("veoPID");
                    JSONArray ja = (JSONArray) pids.get("ioPIDs");
                    for (i = 0; i < ios.size(); i++) {
                        ios.get(i).assignPIDs(veoPID, (String) ja.get(i));
                    }
                }

                // create AMS, DAS, and SAMS packages
                packages.createAMSPackage(setMetadata, ios, events);
                packages.createSAMSPackage(ios);
                packages.createDASPackage(veo, veoPID, ios);
            }
        } catch (AppFatal ae) {
            throw ae;
        } catch (AppError | VEOError ve) {
            log1.append(ve.getMessage());
            success = false;
        } finally {

            // free everything
            if (ios != null) {
                for (i = 0; i < ios.size(); i++) {
                    io = ios.get(i);
                    io.free();
                }
                ios.clear();
            }
            if (events != null) {
                for (i = 0; i < events.size(); i++) {
                    e = events.get(i);
                    e.free();
                }
                events.clear();
            }
        }
        return new VEOResult(recordName, VEOResult.V3_VEO, success, log1.toString(), packageDir, started);
    }

    /**
     * Private class to encapsulate reading the VEOHistory.xml file from this
     * VEO
     */
    private class VEOHistoryParser implements XMLConsumer {

        private final XMLParser parser;
        private ArrayList<Event> events;    // list of events
        private Event event;                // current vers:Event being parsed

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

            // parse and build list of events
            parser.parse(xmlFile);
            return events;
        }

        /**
         * SAX Events captured
         */
        private final static String VEO_HISTORY_PREFIX = "vers:VEOHistory/";

        /**
         * Start of element
         *
         * This event is called when the parser finds a new element.
         *
         * @param eFound element path identifying the found element
         * @param attributes any attributes in the element
         * @throws SAXException any failure (stop parsing)
         * @return what to do with the value
         */
        @Override
        public HandleElement startElement(String eFound, Attributes attributes)
                throws SAXException {
            HandleElement wtdwv;

            // match a prefix
            if (eFound.startsWith(VEO_HISTORY_PREFIX)) {
                eFound = eFound.substring(VEO_HISTORY_PREFIX.length());
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
                case "vers:Event/vers:Error":
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
         * Found the end of an element. Process the value if we're interested.
         *
         * @param eFound element path identifying the found element
         * @param value value of element (null if not captured)
         * @throws SAXException if processing needs to be terminated
         */
        @Override
        public void endElement(String eFound, String value, String element) throws SAXException {

            // match a prefix
            if (eFound.startsWith(VEO_HISTORY_PREFIX)) {
                eFound = eFound.substring(VEO_HISTORY_PREFIX.length());
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
                    event.initiators.add(value);
                    break;
                case "vers:Event/vers:Description":
                    event.descriptions.add(value);
                    break;
                case "vers:Event/vers:Error":
                    event.errors.add(value);
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
        private Path veoDir;                    // place to put the VEO content
        private ArrayList<InformationObject> ios;  // list of Information Objects
        private InformationObject io;           // current vers:InformationObject being parsed
        private MetadataPackage mp;             // current vers:MetadataPackage being parsed
        private InformationPiece ip;            // current vers:InforamtionPiece being parsed
        private ContentFile cf;                 // current vers:ContentFile being parsed
        private int ipCnt;                      // count of Information Pieces seen in this IO
        private int mpCnt;                      // count of Metadata Packages seen in this IO
        private int cfSeqNoInVEO;               // sequence number of Content Files in this VEO
        private boolean aglsMP;                 // true if found an AGLS metadata package
        private boolean anzs5478MP;             // true if found an ANZS 5478 metadata package
        private String idValue;                 // value of an Identifier in an ANZS 5478 metadata package
        private String idScheme;                // scheme for value of an Identifier in an ANZS 5478 metadata package

        public VEOContentParser() throws AppFatal {
            parser = new XMLParser(this);
            free();
        }

        /**
         * Set up...
         */
        private void free() {
            veoFile = null;
            veoDir = null;
            ios = null;
            io = null;
            mp = null;
            ip = null;
            cf = null;
            ipCnt = 0;
            mpCnt = 0;
            cfSeqNoInVEO = 0;
            aglsMP = false;
            anzs5478MP = false;
            idValue = null;
            idScheme = null;
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

            // set up new parse
            free();
            this.veoFile = veo;
            this.veoDir = veoDir;
            ios = new ArrayList<>();

            // parse
            parser.parse(xmlFile);
            return ios;
        }

        /**
         * SAX Events captured
         */
        private final static String VEO_CONTENT_PREFIX = "vers:VEOContent/";

        /**
         * Start of element
         *
         * This event is called when the parser finds a new element.
         *
         * @param eFound element path identifying the found element
         * @param attributes any attributes in the element
         * @throws SAXException any failure (stop parsing)
         * @return what to do with the value
         */
        @Override
        public HandleElement startElement(String eFound, Attributes attributes)
                throws SAXException {
            HandleElement wtdwv;

            // match a prefix
            if (eFound.startsWith(VEO_CONTENT_PREFIX)) {
                eFound = eFound.substring(VEO_CONTENT_PREFIX.length());
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
                    anzs5478MP = false;
                    wtdwv = new HandleElement(HandleElement.ELEMENT_TO_STRING);
                    break;
                case "vers:InformationObject/vers:MetadataPackage/vers:MetadataSchemaIdentifier":
                case "vers:InformationObject/vers:MetadataPackage/vers:MetadataSyntaxIdentifier":
                    wtdwv = new HandleElement(HandleElement.VALUE_TO_STRING);
                    break;
                case "vers:InformationObject/vers:MetadataPackage/rdf:RDF/rdf:Description/dcterms:date":
                case "vers:InformationObject/vers:MetadataPackage/rdf:RDF/rdf:Description/dcterms:created":
                case "vers:InformationObject/vers:MetadataPackage/rdf:RDF/rdf:Description/dcterms:available":
                case "vers:InformationObject/vers:MetadataPackage/rdf:RDF/rdf:Description/dcterms:dateCopyrighted":
                case "vers:InformationObject/vers:MetadataPackage/rdf:RDF/rdf:Description/dcterms:dateLicensed":
                case "vers:InformationObject/vers:MetadataPackage/rdf:RDF/rdf:Description/dcterms:issued":
                case "vers:InformationObject/vers:MetadataPackage/rdf:RDF/rdf:Description/dcterms:modified":
                case "vers:InformationObject/vers:MetadataPackage/rdf:RDF/rdf:Description/dcterms:valid":
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
                case "vers:InformationObject/vers:MetadataPackage/rdf:RDF/rdf:Description/versterms:disposalReference":
                case "vers:InformationObject/vers:MetadataPackage/rdf:RDF/rdf:Description/versterms:disposal-Reference": // oddity in standard
                    if (aglsMP) {
                        wtdwv = new HandleElement(HandleElement.VALUE_TO_STRING);
                    } else {
                        wtdwv = null;
                    }
                    break;
                case "vers:InformationObject/vers:MetadataPackage/rdf:RDF/anzs5478:Record/anzs5478:Identifier":
                    if (anzs5478MP) {
                        idValue = null;
                        idScheme = null;
                    }
                    wtdwv = null;
                    break;
                case "vers:InformationObject/vers:MetadataPackage/rdf:RDF/anzs5478:Record/anzs5478:Identifier/anzs5478:IdentifierString":
                case "vers:InformationObject/vers:MetadataPackage/rdf:RDF/anzs5478:Record/anzs5478:Identifier/anzs5478:IdentifierScheme":
                case "vers:InformationObject/vers:MetadataPackage/rdf:RDF/anzs5478:Record/anzs5478:Name/anzs5478:NameWords":
                case "vers:InformationObject/vers:MetadataPackage/rdf:RDF/anzs5478:Record/anzs5478:DateRange/anzs5478:StartDate":
                case "vers:InformationObject/vers:MetadataPackage/rdf:RDF/anzs5478:Record/anzs5478:DateRange/anzs5478:EndDate":
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
                    wtdwv = null;
                    break;
                case "vers:InformationObject/vers:InformationPiece/vers:Label":
                    wtdwv = new HandleElement(HandleElement.VALUE_TO_STRING);
                    break;
                case "vers:InformationObject/vers:InformationPiece/vers:ContentFile":
                    cf = ip.addContentFile();
                    cf.seqNbr = cfSeqNoInVEO;
                    cfSeqNoInVEO++;
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
         * Found the end of an element. Process the value if we're interested.
         *
         * @param eFound element path identifying the found element
         * @param value value of element (null if not captured)
         * @throws SAXException if processing needs to be terminated
         */
        @Override
        public void endElement(String eFound, String value, String element) throws SAXException {
            String s;
            int i, depth;
            InformationObject parent;

            // match a prefix
            if (eFound.startsWith(VEO_CONTENT_PREFIX)) {
                eFound = eFound.substring(VEO_CONTENT_PREFIX.length());
            }

            // if recording store element value in appropriate global variable
            switch (eFound) {
                case "vers:InformationObject":
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
                    if (value.equals("http://prov.vic.gov.au/vers/schema/ANZS5478")) {
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
                        io.dates.add(new Date("Date", value));
                    }
                    break;
                case "vers:InformationObject/vers:MetadataPackage/rdf:RDF/rdf:Description/dcterms:created":
                    // could have multiple dates, choose first dateCreated found unless we find a longer
                    // (more precise) date
                    if (io.dateCreated == null || io.dateCreated.length() < value.length()) {
                        io.dateCreated = value;
                    }
                    io.dates.add(new Date("DateCreated", value));
                    break;
                case "vers:InformationObject/vers:MetadataPackage/rdf:RDF/rdf:Description/dcterms:available":
                    io.dates.add(new Date("DateAvailable", value));
                    break;
                case "vers:InformationObject/vers:MetadataPackage/rdf:RDF/rdf:Description/dcterms:dateCopyrighted":
                    io.dates.add(new Date("DateCopyrighted", value));
                    break;
                case "vers:InformationObject/vers:MetadataPackage/rdf:RDF/rdf:Description/dcterms:dateLicensed":
                    io.dates.add(new Date("DateLicensed", value));
                    break;
                case "vers:InformationObject/vers:MetadataPackage/rdf:RDF/rdf:Description/dcterms:issued":
                    io.dates.add(new Date("DateIssued", value));
                    break;
                case "vers:InformationObject/vers:MetadataPackage/rdf:RDF/rdf:Description/dcterms:modified":
                    io.dates.add(new Date("DateModified", value));
                    break;
                case "vers:InformationObject/vers:MetadataPackage/rdf:RDF/rdf:Description/dcterms:valid":
                    io.dates.add(new Date("DateValid", value));
                    break;
                case "vers:InformationObject/vers:MetadataPackage/rdf:RDF/rdf:Description/dcterms:title":
                    io.titles.add(value);
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
                case "vers:InformationObject/vers:MetadataPackage/rdf:RDF/rdf:Description/versterms:disposalReference":
                case "vers:InformationObject/vers:MetadataPackage/rdf:RDF/rdf:Description/versterms:disposal-Reference": // oddity in standard
                    io.disposalAuthority.rdas.add(value);
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
                    io.titles.add(value);
                    break;
                case "vers:InformationObject/vers:MetadataPackage/rdf:RDF/anzs5478:Record/anzs5478:DateRange/anzs5478:StartDate":
                    // could have multiple dates, choose first dateCreated found unless we find a longer
                    // (more precise) date
                    if (io.dateCreated == null || io.dateCreated.length() < value.length()) {
                        io.dateCreated = value;
                    }
                    io.dates.add(new Date("StartDate", value));
                    break;
                case "vers:InformationObject/vers:MetadataPackage/rdf:RDF/anzs5478:Record/anzs5478:DateRange/anzs5478:EndDate":
                    io.dates.add(new Date("EndDate", value));
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
                    io.disposalAuthority.rdas.add(value);
                    break;
                case "vers:InformationObject/vers:MetadataPackage/rdf:RDF/anzs5478:Record/anzs5478:Disposal/anzs5478:DisposalClass":
                    io.disposalAuthority.disposalClass = value;
                    break;
                case "vers:InformationObject/vers:InformationPiece/vers:Label":
                    ip.label = value;
                    break;
                case "vers:InformationObject/vers:InformationPiece/vers:ContentFile/vers:PathName":
                    String safe = value.replaceAll("\\\\", "/");
                    cf.fileLocation = Paths.get(safe);
                    cf.rootFileLocn = veoDir;
                    s = cf.fileLocation.getFileName().toString();
                    i = s.lastIndexOf(".");
                    if (i == -1) {
                        cf.fileExt = "unknown";
                    } else {
                        cf.fileExt = s.substring(i + 1);
                    }
                    cf.sourceFileName = value;

                    // get file size
                    try {
                        Path p1 = Paths.get(".").resolve(veoDir).resolve(cf.fileLocation);
                        cf.fileSize = Files.size(p1.toAbsolutePath().normalize());
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
            Identifier newId;

            newId = new Identifier(target, null);

            // search to see if this relationship type has already been seen
            // if so, add the target to that relationship
            for (i = 0; i < io.relations.size(); i++) {
                r = io.relations.get(i);
                for (j = 0; j < r.types.size(); j++) {
                    if (r.types.get(j).equals(type)) {
                        r.targetIds.add(newId);
                        break;
                    }
                }
                if (j < r.types.size()) {
                    break;
                }
            }

            // doesn't already exist, so add a new relationship
            if (i == io.relations.size()) {
                io.relations.add(new Relationship(type, newId, null));
            }
        }
    }
}
