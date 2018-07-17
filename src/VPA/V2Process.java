/*
 * Copyright Public Record Office Victoria 2018
 * Licensed under the CC-BY license http://creativecommons.org/licenses/by/3.0/au/
 * Author Andrew Waugh
 * Version 1.0 February 2018
 */
package VPA;

/**
 * V2Process
 *
 * This class processes a V2 VEO, validates it, and generates the required
 * packages for the DAS, AMS, & SAMS. An instance of the class is constructed
 * once and then may be called multiple times to process individual V2 VEOs.
 */
import VERSCommon.AppFatal;
import VERSCommon.AppError;
import VERSCommon.HandleElement;
import VERSCommon.XMLParser;
import VERSCommon.XMLConsumer;
import VEOCheck.VEOCheck;
import VERSCommon.VEOError;
import java.io.BufferedOutputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.StringWriter;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.xml.sax.*;

public class V2Process {

    private final Packages packages;      // Utility class to create the various packages
    private final VEOCheck veoc;          // V2 VEO validation class
    private final V2VEOParser parser;     // parser to process the .veo (XML) file
    private final PIDService ps;          // Class to encapsulate the PID service
    private final String rdfIdPrefix;     // prefix to be used to generate RDF identifiers
    private String veoFormatDesc;         // VEO Format Description from currently parsed VEO
    private String version;               // version from currently parsed VEO
    private final ArrayList<String> sigBlock;   // signature blocks from currently parsed VEO
    private final ArrayList<String> lockSigBlock; // lock signature blocks from currently parsed VEO
    private String signedObject;          // signedObject from currently parsed VEO

    private final static Logger LOG = Logger.getLogger("VPA.V2Process");

    /**
     * Constructor. Set up for processing V2 VEOs
     *
     * @param ps the encapsulation of the PID service
     * @param rdfIdPrefix RDF ID prefix to be used in generating RDF
     * @param supportDir directory where the versV2.dtd file is located
     * @param packages methods that generate the packages
     * @param logLevel logging level (INFO = verbose, FINE = debug)
     * @throws AppFatal if a fatal error occurred
     */
    public V2Process(PIDService ps, String rdfIdPrefix, Path supportDir, Packages packages, Level logLevel) throws AppFatal {
        Path dtd;

        LOG.setLevel(null);
        this.ps = ps;
        this.rdfIdPrefix = rdfIdPrefix;
        this.packages = packages;
        dtd = supportDir.resolve("versV2.dtd");

        // set up headless validation
        veoc = new VEOCheck(dtd, logLevel);

        // set up parser
        parser = new V2VEOParser();

        veoFormatDesc = null;
        version = null;
        sigBlock = new ArrayList<>();
        lockSigBlock = new ArrayList<>();
        signedObject = null;
    }

    /**
     * Free all memory allocated in processing VEO.
     */
    private void free(ArrayList<InformationObject> ios, ArrayList<Event> events) {
        int i;

        for (i = 0; i < ios.size(); i++) {
            ios.get(i).free();
        }
        ios = null;
        for (i = 0; i < events.size(); i++) {
            events.get(i).free();
        }
        events = null;
        veoFormatDesc = null;
        version = null;
        sigBlock.clear();
        lockSigBlock.clear();
        signedObject = null;
    }

    /**
     * Process a V2 VEO. This involves 1) parsing the VEO (an XML file), 2)
     * validating it, 3) extracting the binary files from the VEO, 4) building
     * packages of information for the DAS, AMS, and SAMS.
     *
     * An AppFatal error will be thrown if the VPA itself fails (typically a API
     * call failed when it should always succeed). An AppError will be thrown if
     * VEO processing failed for reasons other than an error in the VEO (e.g.
     * passed a null path to process). For any other issues, a VEOResult will be
     * returned containing details about the results of the processing.
     *
     * @param setMetadata metadata about the set as a whole
     * @param veo	the file to parse
     * @param recordName the name of the Information Object to be produced
     * @param packageDir the directory where the VEO is to be processed
     * @return a result structure showing the results of processing the VEO
     * @throws AppFatal if a system error occurred
     * @throws AppError processing failed, but further VEOs can be submitted
     */
    public VEOResult process(String setMetadata, Path veo, String recordName, Path packageDir) throws AppFatal, AppError {
        StringWriter out;
        ArrayList<InformationObject> ios;
        VEOResult res;
        InformationObject io;   // current information object
        ArrayList<Event> events; // list of events read
        String veoPID;          // PID assigned to VEO
        int i;

        // check parameters
        if (veo == null) {
            throw new AppError("V2Process.process: Passed null VEO file to be processed");
        }
        if (recordName == null) {
            throw new AppError("V2Process.process: Passed null recordName");
        }
        LOG.log(Level.INFO, "Processing ''{0}''", new Object[]{veo.toAbsolutePath().toString()});

        res = new VEOResult(VEOResult.V2_VEO);
        res.packages = packageDir;

        // construct blank Information Object
        ios = new ArrayList<>();
        events = new ArrayList<>();

        try {
            io = new InformationObject(ps, 0);
            ios.add(io);

            io.veoFileName = veo.getFileName().toString();

            // parse
            veoFormatDesc = null;
            version = null;
            sigBlock.clear();
            lockSigBlock.clear();
            signedObject = null;
            parser.parse(veo, packageDir, io, events);

            // create abbreviated VEO, and validate it
            out = new StringWriter();
            Path p = Paths.get(packageDir.toString(), "abreviatedVEO.xml");
            createAbbrVEO(p);
            veoc.vpaTestVEO(veo, p, out);

            // add ingest event
            events.add(Event.Ingest());

            // test to see if this VEO has already had custody accepted (i.e.
            // migration) or this is a new VEO
            for (i = 0; i < events.size(); i++) {
                if (events.get(i).eventType.trim().toLowerCase().equals("custody accepted")) {
                    break;
                }
            }
            if (i == events.size()) {
                events.add(Event.CustodyAccepted());
            } else {
                events.add(Event.Migrated());
            }

            // assign the PIDs
            veoPID = ps.mint();
            for (i = 0; i < ios.size(); i++) {
                ios.get(i).assignPIDs(veoPID);
            }

            // create AMS and SAMS outputs
            // packages.createValidationPackage(io, Paths.get(veoDir.toString(), "veo.xml"));
            packages.createAMSPackage(setMetadata, ios, events);
            packages.createSAMSPackage(ios);
            packages.createDASPackage(veo, veoPID);

            // validate VEO
        } catch (AppFatal af) {
            out = null;
            res.free();
            veoPID = null;
            io = null;
            free(ios, events);
            throw af;
        } catch (VEOError | AppError e) {
            out = null;
            finishResult(res, false, e.getMessage());
            veoPID = null;
            io = null;
            free(ios, events);
            return res;
        }

        finishResult(res, true, out.toString());
        out = null;
        veoPID = null;
        io = null;
        free(ios, events);
        return res;
    }

    /**
     * Finish result
     */
    private void finishResult(VEOResult res, boolean success, String desc) {
        res.success = success;
        res.result = desc;
        res.timeProcEnded = Instant.now();
    }

    /**
     * Produce a abbreviated version of V2 VEO for validation. The main
     * alteration is the removal of all DocumentData to allow easier processing
     * by DOM
     *
     * @param p
     * @throws AppError
     */
    public void createAbbrVEO(Path p) throws AppError {
        FileOutputStream fos;
        BufferedOutputStream bos;
        OutputStreamWriter osw;
        int i;

        // create the abbreviated VEO file
        try {
            fos = new FileOutputStream(p.toFile());
        } catch (FileNotFoundException fnfe) {
            throw new AppError("Packages.createValidationPackage(): Couldn't create abbreviated VEO file as: " + fnfe.getMessage());
        }
        bos = new BufferedOutputStream(fos);
        osw = new OutputStreamWriter(bos);
        try {
            osw.write("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"no\" ?>\n");
            osw.write("<!DOCTYPE vers:VERSEncapsulatedObject SYSTEM \"vers.dtd\">\n");
            osw.write("<vers:VERSEncapsulatedObject\n");
            osw.write("  xmlns:vers=\"http://www.prov.vic.gov.au/gservice/standard/pros99007.htm\"\n");
            osw.write("  xmlns:naa=\"http://www.naa.gov.au/recordkeeping/control/rkms/contents.html\">\n");
            if (veoFormatDesc != null) {
                osw.write(veoFormatDesc);
                osw.write("\n");
            }
            if (version != null) {
                osw.write(version);
                osw.write("\n");
            }
            for (i = 0; i < sigBlock.size(); i++) {
                osw.write(sigBlock.get(i));
                osw.write("\n");
            }
            for (i = 0; i < lockSigBlock.size(); i++) {
                osw.write(lockSigBlock.get(i));
                osw.write("\n");
            }
            if (signedObject != null) {
                osw.write(signedObject);
                osw.write("\n");
            }
            osw.write("</vers:VERSEncapsulatedObject>");
        } catch (IOException ioe) {
            throw new AppError("Packages.createValidationPackage(): Failed writing to the abbreviated VEO file as: " + ioe.getMessage());
        } finally {
            try {
                osw.close();
            } catch (IOException ioe) {
                // ignore
            }
            try {
                bos.close();
            } catch (IOException ioe) {
                // ignore
            }
            try {
                fos.close();
            } catch (IOException ioe) {
                // ignore
            }
        }
    }

    /**
     * Create an InformationObject (in XML) from data parsed from the V2 VEO
     *
     * @param io the InformationObject
     * @param rdfIdPrefix prefix for RDF
     * @param recordName record name
     * @throws AppFatal if a programming error occurred
     */
    private void createIO(InformationObject io, String rdfIdPrefix, String recordName) throws AppFatal {
        StringBuilder sb;
        URI uri;
        int i, j;

        // create RDF URI prefix for AGLS metadata
        try {
            if (rdfIdPrefix == null) {
                uri = new URI("file", null, "/" + recordName, null);
            } else {
                uri = new URI(rdfIdPrefix, null, "/" + recordName, null);
            }
        } catch (URISyntaxException use) {
            throw new AppFatal("V2Process.createIO(): Failed building URI when generating RDF: " + use.getMessage());
        }

        sb = new StringBuilder();
        sb.append(" <vers:InformationObject>\n");
        if (io.label != null) {
            sb.append("  <vers:InformationObjectType>");
            sb.append(io.label);
            sb.append("</vers:InformationObjectType>\n");
        }
        sb.append("  <vers:InformationObjectDepth>1</vers:InformationObjectDepth>\n");

        // add a AGLS metadata package
        sb.append("  <vers:MetadataPackage xmlns:rdf=\"http://www.w3.org/1999/02/22-rdf-syntax-ns#\">\n");
        sb.append("   <vers:MetadataSchemaIdentifier>http://prov.vic.gov.au/vers/schema/AGLS</vers:MetadataSchemaIdentifier>\n");
        sb.append("   <vers:MetadataSyntaxIdentifier>http://www.w3.org/1999/02/22-rdf-syntax-ns</vers:MetadataSyntaxIdentifier>\n");
        sb.append("<rdf:RDF xmlns:dcterms=\"http://purl.org/dc/terms/\" xmlns:aglsterms=\"http://www.agls.gov.au/agls/terms/\" xmlns:versterms=\"http://www.prov.vic.gov.au/vers/terms/\">\n");
        sb.append("<rdf:Description rdf:about=\"");
        sb.append(uri.toASCIIString());
        sb.append("\">\n");
        // sb.append(io.aglsMetadata.toString());
        sb.append(" </rdf:Description>\n");
        sb.append("</rdf:RDF>\n");
        sb.append("  </vers:MetadataPackage>\n");

        // add the VERS metadata package
        sb.append("  <vers:MetadataPackage>\n");
        sb.append("   <vers:MetadataSchemaIdentifier>http://prov.vic.gov.au/vers/schema/VERS</vers:MetadataSchemaIdentifier>\n");
        sb.append("   <vers:MetadataSyntaxIdentifier>https://www.w3.org/TR/2008/REC-xml-20081126/</vers:MetadataSyntaxIdentifier>\n");
        sb.append(io.metaPackages.get(0).content);
        sb.append("  </vers:MetadataPackage>\n");

        // add documents and encodingsToFind (if any)
        for (i = 0; i < io.infoPieces.size(); i++) {
            InformationPiece ip = io.infoPieces.get(i);
            sb.append("  <vers:InformationPiece>\n");
            if (ip.label != null && !ip.label.equals("")) {
                sb.append("   <vers:Label>");
                sb.append(ip.label);
                sb.append("</vers:Label>\n");
            }
            if (ip.contentFiles != null) {
                for (j = 0; j < ip.contentFiles.size(); j++) {
                    ContentFile e = ip.contentFiles.get(j);
                    sb.append("   <vers:ContentFile>\n");
                    sb.append("    <vers:PathName>");
                    sb.append(e.sourceFileName);
                    sb.append("</vers:PathName>\n");
                    // add CFU element to Information Object
                    sb.append("\n<cfu>");
                    sb.append("<veoPID>");
                    // sb.append(veoPID);
                    sb.append("</veoPID><cfSeqNo>");
                    sb.append(e.seqNbr);
                    sb.append("</cfSeqNo></cfu>\n");
                    sb.append("   </vers:ContentFile>\n");
                }
            }
            sb.append("  </vers:InformationPiece>\n");
        }
        sb.append(" </vers:InformationObject>\n");
        // io.xmlContent = sb.toString();
        uri = null;
        sb = null;
    }

    /**
     * Private class to encapsulate reading a V2 VEO file.
     */
    private class V2VEOParser implements XMLConsumer {

        private final XMLParser parser;
        private int seqNo;                  // sequence number of content file in VEO
        private boolean emptyVEO;           // This VEO doesn't contain any content
        private boolean finalVersion;       // true if we are parsing the final version of the VEO
        private Relationship relation;      // a relation with another record
        private InformationObject io;       // information object read from VEO
        private int docNo;                  // document number in VEO
        private InformationPiece document;  // current document being parsed
        private int encNo;                  // encoding number
        private ContentFile encoding;       // current encoding being parsed
        private HashMap<String, ContentFile> encodingsToFind; // list of encodingsToFind we are looking for
        private ArrayList<Event> events;    // list of events from VEO
        private Event event;                // current vers:Event being parsed
        private Path veoDir;                // directory in which to put content
        private String agencyId;            // agency id from VEO id
        private String seriesId;            // series id from VEO id
        private String fileId;              // file id from VEO id
        private String recordId;            // record id from VEO id

        public V2VEOParser() throws AppFatal {
            clear();
            parser = new XMLParser(this);
        }

        /**
         * Set globals to a standard state
         */
        private void clear() {
            emptyVEO = true;
            finalVersion = true;
            relation = null;
            io = null;
            docNo = 0;
            document = null;
            encNo = 0;
            encoding = null;
            encodingsToFind = null;
            events = null;
            event = null;
            veoDir = null;
            seqNo = 1;
            agencyId = null;
            seriesId = null;
            fileId = null;
            recordId = null;
        }

        /**
         * Parse the veoFile. It returns a single Information Object (as a V2
         * VEO only represents a single IO) and a list of events.
         */
        public void parse(Path veoFile, Path veoDir, InformationObject io, ArrayList<Event> events) throws AppError, AppFatal {

            // set up parse
            clear();
            encodingsToFind = new HashMap<>();
            this.io = io;
            this.events = events;
            this.veoDir = veoDir;

            // parse
            try {
                parser.parse(veoFile);
            } catch (AppError | AppFatal e) {
                throw e;
            } finally { // free everything
                relation = null;
                document = null;
                encoding = null;
                encodingsToFind.clear();
                encodingsToFind = null;
                event = null;
                agencyId = null;
                seriesId = null;
                fileId = null;
                recordId = null;
            }

            // didn't find any contents (valid XML, but no content)
            if (emptyVEO) {
                throw new AppError("VEO File '" + veoFile.toString() + "' did not contain a Record element (or anything else)\n");
            }
        }

        /**
         * SAX Events captured
         */
        private final static String RECORDVEOPREFIX = "vers:VERSEncapsulatedObject/vers:SignedObject/vers:ObjectContent/vers:Record/";
        private final static String FILEVEOPREFIX = "vers:VERSEncapsulatedObject/vers:SignedObject/vers:ObjectContent/vers:File/";
        private final static String MODVEOPREFIX = "vers:VERSEncapsulatedObject/vers:SignedObject/vers:ObjectContent/vers:ModifiedVEO/";
        private final static String MODREVSTEP = "vers:RevisedVEO/vers:SignedObject/vers:ObjectContent/";
        private final static String MODORIGSTEP = "vers:OriginalVEO/vers:SignedObject/vers:ObjectContent/";

        /**
         * Start of element
         *
         * This event is called when the parser finds a new element. The method
         * is passed the path of this element (the string of element tags) from
         * the root of the document, and the collection of attributes associated
         * with it. We then check to see if this element is one that we are
         * interested in. If so we do something...
         *
         * The most important decision is whether we want to record the value of
         * this element.
         *
         * @param eFound path of this element from the root of the document
         * @param attributes associated with this element
         * @throws SAXException if something goes wrong...
         */
        @Override
        public HandleElement startElement(String eFound, Attributes attributes)
                throws SAXException {
            int i;
            String ext;
            HandleElement he;

            // record the top level elements of a V2 VEO so that we can output them
            // again in the abbreviated VEO
            switch (eFound) {
                case "vers:VERSEncapsulatedObject/vers:VEOFormatDescription":
                case "vers:VERSEncapsulatedObject/vers:Version":
                case "vers:VERSEncapsulatedObject/vers:SignatureBlock":
                case "vers:VERSEncapsulatedObject/vers:LockSignatureBlock":
                case "vers:VERSEncapsulatedObject/vers:SignedObject":
                    return new HandleElement(HandleElement.ELEMENT_TO_STRING);
                default:
            }

            // else match a File VEO or a Record VEO
            if (eFound.startsWith(RECORDVEOPREFIX)) {
                emptyVEO = false;
                eFound = eFound.substring(RECORDVEOPREFIX.length());
                io.veoVersion = "V2";
                io.v2VeoType = "RecordVEO";
            } else if (eFound.startsWith(FILEVEOPREFIX)) {
                emptyVEO = false;
                eFound = eFound.substring(FILEVEOPREFIX.length());
                io.veoVersion = "V2";
                io.v2VeoType = "FileVEO";
            } else if (eFound.startsWith(MODVEOPREFIX)) {
                eFound = eFound.substring(MODVEOPREFIX.length());
                if (eFound.startsWith(MODREVSTEP)) {
                    eFound = eFound.substring(MODREVSTEP.length());
                    if (eFound.startsWith("vers:Record/")) {
                        eFound = eFound.substring("vers:Record/".length());
                        emptyVEO = false;
                        io.veoVersion = "V2";
                        io.v2VeoType = "RecordVEO";
                    } else if (eFound.startsWith("vers:File/")) {
                        eFound = eFound.substring("vers:File/".length());
                        emptyVEO = false;
                        io.veoVersion = "V2";
                        io.v2VeoType = "FileVEO";
                    }
                } else if (eFound.startsWith(MODORIGSTEP)) {
                    finalVersion = false;
                    eFound = eFound.substring(MODORIGSTEP.length());
                    while (eFound.startsWith("vers:ModifiedVEO/")) {
                        eFound = eFound.substring("vers:ModifiedVEO/".length());
                        if (eFound.startsWith(MODREVSTEP)) {
                            eFound = eFound.substring(MODREVSTEP.length());
                        } else if (eFound.startsWith(MODORIGSTEP)) {
                            eFound = eFound.substring(MODORIGSTEP.length());
                        }
                    }
                    if (eFound.startsWith("vers:Record/")) {
                        eFound = eFound.substring("vers:Record/".length());
                    } else if (eFound.startsWith("vers:File/")) {
                        eFound = eFound.substring("vers:File/".length());
                    }
                }
            }
            // match the path to see if do we do something special within a signed object
            he = null;
            switch (eFound) {
                case "vers:RevisedVEO":
                case "vers:OriginalVEO":
                    docNo = 0;
                    break;
                case "vers:RecordMetadata/naa:ManagementHistory/vers:ManagementEvent":
                case "vers:FileMetadata/naa:ManagementHistory/vers:ManagementEvent":
                    if (finalVersion) {
                        event = new Event();
                    }
                    he = null;
                    break;
                case "vers:Document":
                    docNo++;
                    encNo = 0;
                    if (finalVersion) {
                        document = io.addInformationPiece();
                    }
                    he = null;
                    break;
                case "vers:Document/vers:Encoding":
                    encNo++;
                    if (finalVersion) {
                        encoding = document.addContentFile();
                        encoding.seqNbr = encNo;
                    }
                    he = null;
                    break;
                case "vers:Document/vers:Encoding/vers:DocumentData":
                    String versid;
                    String ref;

                    // a vers:DocumentData element should contain a vers:id attribute
                    versid = null;
                    if (attributes != null) {
                        versid = attributes.getValue("vers:id");
                    }

                    // except if it is an original V1 VEO
                    if (versid == null) {
                        versid = "Revision-1-Document-" + docNo + "-Encoding-" + encNo + "-DocumentData";
                    }

                    // only include this DocumentData if it is in the final revision
                    // or it was referred to from the final revision
                    if (!finalVersion) {
                        encoding = encodingsToFind.get(versid);
                        if (encoding == null) {
                            he = null;
                            break;
                        }
                        encodingsToFind.remove(versid);
                    }

                    // in onion VEOs, the vers:DocumentData element may not contain the element,
                    // but refer to another vers:DocumentData element that has the element
                    if (attributes != null) {
                        ref = attributes.getValue("vers:forContentsSeeElement");
                        if (ref != null) {
                            encoding.fileLocation = null;
                            encoding.refDoc = ref;
                            encodingsToFind.put(ref, encoding);
                            he = null;
                            break;
                        }
                        ref = attributes.getValue("vers:forContentsSeeOriginalDocumentAndEncoding");
                        if (ref != null) {
                            encoding.fileLocation = null;
                            encoding.refDoc = ref;
                            encodingsToFind.put(ref, encoding);
                            he = null;
                            break;
                        }
                    }

                    // work out the file type from the file extension in the SourceFileIdentifier element (if present)
                    ext = encoding.sourceFileName;
                    if (ext != null) {
                        i = ext.lastIndexOf(".");
                        if (i != -1) {
                            ext = ext.substring(i);
                        } else {
                            ext = "";
                        }
                    } else {
                        ext = "";
                    }

                    encoding.fileLocation = veoDir.resolve((versid + ext));
                    encoding.seqNbr = seqNo;
                    seqNo++;
                    he = new HandleElement(HandleElement.VALUE_TO_FILE, encoding.base64, encoding.fileLocation);
                    break;
                case "vers:RecordMetadata/naa:FileIdentifier":
                case "vers:RecordMetadata/naa:RecordIdentifier":
                case "vers:RecordMetadata/vers:VEOIdentifier":
                case "vers:FileMetadata/vers:VEOIdentifier":
                    if (finalVersion) {
                        agencyId = null;
                        seriesId = null;
                        fileId = null;
                        recordId = null;
                    }
                    break;
                case "vers:RecordMetadata/naa:Relation/naa:RelatedItemId":
                case "vers:FileMetadata/naa:Relation/naa:RelatedItemId":
                    if (finalVersion) {
                        he = new HandleElement(HandleElement.VALUE_TO_STRING);
                        agencyId = null;
                        seriesId = null;
                        fileId = null;
                        recordId = null;
                    } else {
                        he = null;
                    }
                    break;
                case "vers:RecordMetadata/naa:Title/naa:TitleWords":
                case "vers:FileMetadata/naa:Title/naa:TitleWords":
                case "vers:RecordMetadata/naa:Description":
                case "vers:FileMetadata/naa:Description":
                case "vers:RecordMetadata/naa:Relation/naa:RelatedItemId/vers:VEOIdentifier/vers:AgencyIdentifier/vers:Text":
                case "vers:FileMetadata/naa:Relation/naa:RelatedItemId/vers:VEOIdentifier/vers:AgencyIdentifier/vers:Text":
                case "vers:RecordMetadata/naa:Relation/naa:RelatedItemId/vers:VEOIdentifier/vers:SeriesIdentifier/vers:Text":
                case "vers:FileMetadata/naa:Relation/naa:RelatedItemId/vers:VEOIdentifier/vers:SeriesIdentifier/vers:Text":
                case "vers:RecordMetadata/naa:Relation/naa:RelatedItemId/vers:VEOIdentifier/vers:FileIdentifier/vers:Text":
                case "vers:FileMetadata/naa:Relation/naa:RelatedItemId/vers:VEOIdentifier/vers:FileIdentifier/vers:Text":
                case "vers:RecordMetadata/naa:Relation/naa:RelatedItemId/vers:VEOIdentifier/vers:VERSRecordIdentifier/vers:Text":
                case "vers:FileMetadata/naa:Relation/naa:RelatedItemId/vers:VEOIdentifier/vers:VERSRecordIdentifier/vers:Text":
                case "vers:RecordMetadata/naa:Relation/naa:RelationType":
                case "vers:FileMetadata/naa:Relation/naa:RelationType":
                case "vers:RecordMetadata/naa:Relation/naa:RelationDescription":
                case "vers:FileMetadata/naa:Relation/naa:RelationDescription":
                case "vers:RecordMetadata/naa:Date/naa:DateTimeCreated":
                case "vers:FileMetadata/naa:Date/naa:DateTimeCreated":
                case "vers:RecordMetadata/naa:Date/naa:DateTimeTransacted":
                case "vers:FileMetadata/naa:Date/naa:DateTimeTransacted":
                case "vers:RecordMetadata/naa:Date/naa:DateTimeRegistered":
                case "vers:FileMetadata/naa:Date/naa:DateTimeRegistered":
                case "vers:FileMetadata/naa:Date/vers:DateTimeClosed":
                case "vers:RecordMetadata/naa:Coverage/naa:Jurisdiction":
                case "vers:FileMetadata/naa:Coverage/naa:Jurisdiction":
                case "vers:RecordMetadata/naa:Coverage/naa:Jurisdication": // spelling error in standard!
                case "vers:FileMetadata/naa:Coverage/naa:Jurisdication":
                case "vers:RecordMetadata/naa:Coverage/naa:PlaceName":
                case "vers:FileMetadata/naa:Coverage/naa:PlaceName":
                case "vers:RecordMetadata/naa:Disposal/naa:DisposalAuthorisation":
                case "vers:FileMetadata/naa:Disposal/naa:DisposalAuthorisation":
                case "vers:RecordMetadata/naa:ManagementHistory/vers:ManagementEvent/naa:EventDateTime":
                case "vers:FileMetadata/naa:ManagementHistory/vers:ManagementEvent/naa:EventDateTime":
                case "vers:RecordMetadata/naa:ManagementHistory/vers:ManagementEvent/naa:EventType":
                case "vers:FileMetadata/naa:ManagementHistory/vers:ManagementEvent/naa:EventType":
                case "vers:RecordMetadata/naa:ManagementHistory/vers:ManagementEvent/naa:EventDescription":
                case "vers:FileMetadata/naa:ManagementHistory/vers:ManagementEvent/naa:EventDescription":
                case "vers:RecordMetadata/naa:RecordIdentifier/vers:VEOIdentifier/vers:AgencyIdentifier/vers:Text":
                case "vers:FileMetadata/naa:FileIdentifier/vers:VEOIdentifier/vers:AgencyIdentifier/vers:Text":
                case "vers:RecordMetadata/naa:RecordIdentifier/vers:VEOIdentifier/vers:SeriesIdentifier/vers:Text":
                case "vers:FileMetadata/naa:FileIdentifier/vers:VEOIdentifier/vers:SeriesIdentifier/vers:Text":
                case "vers:RecordMetadata/naa:RecordIdentifier/vers:VEOIdentifier/vers:FileIdentifier/vers:Text":
                case "vers:FileMetadata/naa:FileIdentifier/vers:VEOIdentifier/vers:FileIdentifier/vers:Text":
                case "vers:RecordMetadata/naa:RecordIdentifier/vers:VEOIdentifier/vers:VERSRecordIdentifier/vers:Text":
                case "vers:RecordMetadata/vers:VEOIdentifier/vers:AgencyIdentifier/vers:Text":
                case "vers:FileMetadata/vers:VEOIdentifier/vers:AgencyIdentifier/vers:Text":
                case "vers:RecordMetadata/vers:VEOIdentifier/vers:SeriesIdentifier/vers:Text":
                case "vers:FileMetadata/vers:VEOIdentifier/vers:SeriesIdentifier/vers:Text":
                case "vers:RecordMetadata/vers:VEOIdentifier/vers:FileIdentifier/vers:Text":
                case "vers:FileMetadata/vers:VEOIdentifier/vers:FileIdentifier/vers:Text":
                case "vers:RecordMetadata/vers:VEOIdentifier/vers:VERSRecordIdentifier/vers:Text":
                case "vers:FileMetadata/vers:VEOIdentifier/vers:VERSRecordIdentifier/vers:Text":
                case "vers:Document/vers:DocumentMetadata/vers:DocumentTitle/vers:Text":
                    if (finalVersion) {
                        he = new HandleElement(HandleElement.VALUE_TO_STRING);
                    } else {
                        he = null;
                    }
                    break;
                case "vers:Document/vers:Encoding/vers:EncodingMetadata/vers:FileRendering/vers:RenderingKeywords":
                case "vers:Document/vers:Encoding/vers:EncodingMetadata/vers:SourceFileIdentifier":
                    he = new HandleElement(HandleElement.VALUE_TO_STRING);
                    break;
                default:
                    he = null;
                    break;
            }
            return he;
        }

        /**
         * End of an element
         *
         * Found the end of an element. Again, check to see if this is an
         * element we are interested. If so, do something (typically with the
         * value of the element).
         *
         * @param eFound path of element tag from root of document
         * @param value value of element
         * @throws SAXException if something goes wrong
         */
        @Override
        public void endElement(String eFound, String value, String element) throws SAXException {
            int i;
            MetadataPackage mp;

            // record the top level elements of a V2 VEO so that we can output them
            // again in the abbreviated VEO
            switch (eFound) {
                case "vers:VERSEncapsulatedObject/vers:VEOFormatDescription":
                    veoFormatDesc = element;
                    return;
                case "vers:VERSEncapsulatedObject/vers:Version":
                    version = element;
                    return;
                case "vers:VERSEncapsulatedObject/vers:SignatureBlock":
                    sigBlock.add(element);
                    return;
                case "vers:VERSEncapsulatedObject/vers:LockSignatureBlock":
                    lockSigBlock.add(element);
                    return;
                case "vers:VERSEncapsulatedObject/vers:SignedObject":
                    signedObject = element;
                    mp = io.addMetadataPackage();
                    mp.id = 1;
                    mp.schema = "http://prov.vic.gov.au/vers/schema/PROS99-007";
                    mp.syntax = "http://www.w3.org/TR/2008/REC-xml-20081126/";
                    mp.content = element;
                    return;
                default:
            }

            // match a prefix
            if (eFound.startsWith(RECORDVEOPREFIX)) {
                eFound = eFound.substring(RECORDVEOPREFIX.length());
            } else if (eFound.startsWith(FILEVEOPREFIX)) {
                eFound = eFound.substring(FILEVEOPREFIX.length());
            } else if (eFound.startsWith(MODVEOPREFIX)) {
                eFound = eFound.substring(MODVEOPREFIX.length());
                if (eFound.startsWith(MODREVSTEP)) {
                    eFound = eFound.substring(MODREVSTEP.length());
                    if (eFound.startsWith("vers:Record/")) {
                        eFound = eFound.substring("vers:Record/".length());
                    } else if (eFound.startsWith("vers:File/")) {
                        eFound = eFound.substring("vers:File/".length());
                    }
                } else if (eFound.startsWith(MODORIGSTEP)) {
                    eFound = eFound.substring(MODORIGSTEP.length());
                    while (eFound.startsWith("vers:ModifiedVEO/")) {
                        eFound = eFound.substring("vers:ModifiedVEO/".length());
                        if (eFound.startsWith(MODREVSTEP)) {
                            eFound = eFound.substring(MODREVSTEP.length());
                        } else if (eFound.startsWith(MODORIGSTEP)) {
                            eFound = eFound.substring(MODORIGSTEP.length());
                        }
                    }
                    if (eFound.startsWith("vers:Record/")) {
                        eFound = eFound.substring("vers:Record/".length());
                    } else if (eFound.startsWith("vers:File/")) {
                        eFound = eFound.substring("vers:File/".length());
                    }
                }
            }

            // if recording store element value in appropriate global variable
            switch (eFound) {
                case "vers:RecordMetadata/naa:RightsManagement/naa:SecurityClassification":
                case "vers:FileMetadata/naa:RightsManagement/naa:SecurityClassification":
                    break;
                case "vers:RecordMetadata/naa:Title/naa:TitleWords":
                case "vers:FileMetadata/naa:Title/naa:TitleWords":
                    if (finalVersion) {
                        io.title = value;
                        io.label = value;
                    }
                    break;
                case "vers:RecordMetadata/naa:Description":
                case "vers:FileMetadata/naa:Description":
                    if (finalVersion) {
                        io.descriptions.add(value);
                    }
                    break;
                case "vers:RecordMetadata/naa:Relation":
                case "vers:FileMetadata/naa:Relation":
                    if (finalVersion) {
                        io.relations.add(relation);
                        relation = null;
                    }
                    break;
                case "vers:RecordMetadata/naa:Relation/naa:RelatedItemId":
                case "vers:FileMetadata/naa:Relation/naa:RelatedItemId":
                    if (finalVersion) {
                        if (value == null) {
                            value = id2JSON();
                        }
                        relation.targetIds.add(value);
                    }
                    break;
                case "vers:RecordMetadata/naa:Relation/naa:RelatedItemId/vers:VEOIdentifier/vers:AgencyIdentifier/vers:Text":
                case "vers:FileMetadata/naa:Relation/naa:RelatedItemId/vers:VEOIdentifier/vers:AgencyIdentifier/vers:Text":
                    if (finalVersion) {
                        agencyId = value;
                    }
                    break;
                case "vers:RecordMetadata/naa:Relation/naa:RelatedItemId/vers:VEOIdentifier/vers:SeriesIdentifier/vers:Text":
                case "vers:FileMetadata/naa:Relation/naa:RelatedItemId/vers:VEOIdentifier/vers:SeriesIdentifier/vers:Text":
                    if (finalVersion) {
                        seriesId = value;
                    }
                    break;
                case "vers:RecordMetadata/naa:Relation/naa:RelatedItemId/vers:VEOIdentifier/vers:FileIdentifier/vers:Text":
                case "vers:FileMetadata/naa:Relation/naa:RelatedItemId/vers:VEOIdentifier/vers:FileIdentifier/vers:Text":
                    if (finalVersion) {
                        fileId = value;
                    }
                    break;
                case "vers:RecordMetadata/naa:Relation/naa:RelatedItemId/vers:VEOIdentifier/vers:VERSRecordIdentifier/vers:Text":
                case "vers:FileMetadata/naa:Relation/naa:RelatedItemId/vers:VEOIdentifier/vers:VERSRecordIdentifier/vers:Text":
                    if (finalVersion) {
                        recordId = value;
                    }
                    break;
                case "vers:RecordMetadata/naa:Relation/naa:RelationType":
                case "vers:FileMetadata/naa:Relation/naa:RelationType":
                    if (finalVersion) {
                        relation.types.add(value);
                    }
                    break;
                case "vers:RecordMetadata/naa:Relation/naa:RelationDescription":
                case "vers:FileMetadata/naa:Relation/naa:RelationDescription":
                    if (finalVersion) {
                        relation.descriptions.add(value);
                    }
                    break;
                case "vers:RecordMetadata/naa:Date/naa:DateTimeCreated":
                case "vers:FileMetadata/naa:Date/naa:DateTimeCreated":
                    if (finalVersion) {
                        io.dateCreated = value;
                        io.dates.add(new Date("DateTimeCreated", value));
                    }
                    break;
                case "vers:RecordMetadata/naa:Date/naa:DateTimeTransacted":
                case "vers:FileMetadata/naa:Date/naa:DateTimeTransacted":
                    if (finalVersion) {
                        io.dates.add(new Date("DateTimeTransacted", value));
                    }
                    break;
                case "vers:RecordMetadata/naa:Date/naa:DateTimeRegistered":
                case "vers:FileMetadata/naa:Date/naa:DateTimeRegistered":
                    if (finalVersion) {
                        io.dateRegistered = value;
                        io.dates.add(new Date("DateTimeRegistered", value));
                    }
                    break;
                case "vers:FileMetadata/naa:Date/vers:DateTimeClosed":
                    if (finalVersion) {
                        io.dates.add(new Date("DateTimeClosed", value));
                    }
                    break;
                case "vers:RecordMetadata/naa:Coverage/naa:Jurisdiction":
                case "vers:FileMetadata/naa:Coverage/naa:Jurisdiction":
                case "vers:RecordMetadata/naa:Coverage/naa:Jurisdication": // spelling error in standard!
                case "vers:FileMetadata/naa:Coverage/naa:Jurisdication":
                    if (finalVersion) {
                        io.jurisdictionalCoverage.add(value);
                    }
                    break;
                case "vers:RecordMetadata/naa:Coverage/naa:PlaceName":
                case "vers:FileMetadata/naa:Coverage/naa:PlaceName":
                    if (finalVersion) {
                        io.spatialCoverage.add(value);
                    }
                    break;
                case "vers:RecordMetadata/naa:Disposal/naa:DisposalAuthorisation":
                case "vers:FileMetadata/naa:Disposal/naa:DisposalAuthorisation":
                    if (finalVersion) {
                        io.disposalAuthorisations.add(value);
                    }
                    break;
                case "vers:RecordMetadata/naa:ManagementHistory/vers:ManagementEvent":
                case "vers:FileMetadata/naa:ManagementHistory/vers:ManagementEvent":
                    if (finalVersion) {
                        events.add(event);
                    }
                    break;
                case "vers:RecordMetadata/naa:ManagementHistory/vers:ManagementEvent/naa:EventDateTime":
                case "vers:FileMetadata/naa:ManagementHistory/vers:ManagementEvent/naa:EventDateTime":
                    if (finalVersion) {
                        event.timestamp = value;
                    }
                    break;
                case "vers:RecordMetadata/naa:ManagementHistory/vers:ManagementEvent/naa:EventType":
                case "vers:FileMetadata/naa:ManagementHistory/vers:ManagementEvent/naa:EventType":
                    if (finalVersion) {
                        event.eventType = value;
                    }
                    break;
                case "vers:RecordMetadata/naa:ManagementHistory/vers:ManagementEvent/naa:EventDescription":
                case "vers:FileMetadata/naa:ManagementHistory/vers:ManagementEvent/naa:EventDescription":
                    if (finalVersion) {
                        event.description = value;
                    }
                    break;
                case "vers:RecordMetadata/naa:RecordIdentifier/vers:VEOIdentifier/vers:AgencyIdentifier/vers:Text":
                case "vers:FileMetadata/naa:FileIdentifier/vers:VEOIdentifier/vers:AgencyIdentifier/vers:Text":
                    if (finalVersion) {
                        agencyId = value;
                    }
                    break;
                case "vers:RecordMetadata/naa:RecordIdentifier/vers:VEOIdentifier/vers:SeriesIdentifier/vers:Text":
                case "vers:FileMetadata/naa:FileIdentifier/vers:VEOIdentifier/vers:SeriesIdentifier/vers:Text":
                    if (finalVersion) {
                        seriesId = value;
                    }
                    break;
                case "vers:RecordMetadata/naa:RecordIdentifier/vers:VEOIdentifier/vers:FileIdentifier/vers:Text":
                case "vers:FileMetadata/naa:FileIdentifier/vers:VEOIdentifier/vers:FileIdentifier/vers:Text":
                    if (finalVersion) {
                        fileId = value;
                    }
                    break;
                case "vers:RecordMetadata/naa:RecordIdentifier/vers:VEOIdentifier/vers:VERSRecordIdentifier/vers:Text":
                    if (finalVersion) {
                        recordId = value;
                    }
                    break;
                case "vers:RecordMetadata/naa:FileIdentifier":
                case "vers:RecordMetadata/naa:RecordIdentifier":
                    if (finalVersion) {
                        io.addIdentifier(id2JSON(), null);
                    }
                    break;
                case "vers:RecordMetadata/vers:VEOIdentifier/vers:AgencyIdentifier/vers:Text":
                case "vers:FileMetadata/vers:VEOIdentifier/vers:AgencyIdentifier/vers:Text":
                    if (finalVersion) {
                        agencyId = value;
                    }
                    break;
                case "vers:RecordMetadata/vers:VEOIdentifier/vers:SeriesIdentifier/vers:Text":
                case "vers:FileMetadata/vers:VEOIdentifier/vers:SeriesIdentifier/vers:Text":
                    if (finalVersion) {
                        seriesId = value;
                    }
                    break;
                case "vers:RecordMetadata/vers:VEOIdentifier/vers:FileIdentifier/vers:Text":
                case "vers:FileMetadata/vers:VEOIdentifier/vers:FileIdentifier/vers:Text":
                    if (finalVersion) {
                        fileId = value;
                    }
                    break;
                case "vers:RecordMetadata/vers:VEOIdentifier/vers:VERSRecordIdentifier/vers:Text":
                    if (finalVersion) {
                        recordId = value;
                    }
                    break;
                case "vers:RecordMetadata/vers:VEOIdentifier":
                case "vers:FileMetadata/vers:VEOIdentifier":
                    if (finalVersion) {
                        io.addIdentifier(id2JSON(), null);
                    }
                    break;
                case "vers:Document/vers:DocumentMetadata/vers:DocumentTitle/vers:Text":
                    if (finalVersion) {
                        document.label = value;
                    }
                    break;
                case "vers:Document/vers:Encoding/vers:EncodingMetadata/vers:FileRendering/vers:RenderingKeywords":
                    // get the list of formats. We strip the leading and trailing
                    // quotes (if present), and split on either a space or a ';'
                    // to handle problem RenderingKeywords
                    String[] s1;

                    if (!finalVersion) {
                        break;
                    }

                    String s = value.trim();
                    if (s.charAt(0) == '\'') {
                        s = s.substring(1);
                    }
                    if (s.charAt(s.length() - 1) == '\'') {
                        s = s.substring(0, s.length() - 1);
                    }
                    s1 = s.split("[; ]");

                    // look for base64
                    encoding.base64 = false;
                    for (i = 0; i < s1.length; i++) {
                        if (s1[i].contains("b64")
                                || s1[i].contains("B64")) {
                            encoding.base64 = true;
                        }
                    }

                    // the final format is assumed to be the file type. Convert
                    // known MIME format to normal Windows file extensions to go
                    // on the end of the file name. If no '.' at start,
                    // add one.
                    String fileExt = s1[s1.length - 1].trim();
                    switch (fileExt) {
                        case "text/plain":
                        case ".text/plain":
                            fileExt = ".txt";
                            break;
                        case "text/html":
                        case ".text/html":
                            fileExt = ".html";
                            break;
                        case "text/xml":
                        case ".text/xml":
                            fileExt = ".xml";
                            break;
                        case "text/css":
                        case ".text/css":
                            fileExt = ".css";
                            break;
                        case "text/csv":
                        case ".text/csv":
                            fileExt = ".csv";
                            break;
                        case "image/tiff":
                        case ".image/tiff":
                            fileExt = ".tif";
                            break;
                        case "image/jpeg":
                        case ".image/jpeg":
                            fileExt = ".jpg";
                            break;
                        case "image/jp2":
                        case ".image/jp2":
                            fileExt = ".jp2";
                            break;
                        case "application/pdf":
                        case ".application/pdf":
                            fileExt = ".pdf";
                            break;
                        case "application/warc":
                        case ".application/warc":
                            fileExt = ".warc";
                            break;
                        case "application/msword":
                        case ".application/msword":
                            fileExt = ".doc";
                            break;
                        case "application/vnd.ms-excel":
                        case ".application/vnd.ms-excel":
                            fileExt = ".xls";
                            break;
                        case "application/vnd.ms-powerpoint":
                        case ".application/vnd.ms-powerpoint":
                            fileExt = ".ppt";
                            break;
                        case "application/vnd.openxmlformats-officedocument.wordprocessingml.document":
                        case ".application/vnd.openxmlformats-officedocument.wordprocessingml.document":
                            fileExt = ".docx";
                            break;
                        case "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet":
                        case ".application/vnd.openxmlformats-officedocument.spreadsheetml.sheet":
                            fileExt = ".xlsx";
                            break;
                        case "application/vnd.openxmlformats-officedocument.presentationml.presentation":
                        case ".application/vnd.openxmlformats-officedocument.presentationml.presentation":
                            fileExt = ".pptx";
                            break;
                        case "audio/mpeg":
                        case ".audio/mpeg":
                        case "audio/mpeg4-generic":
                        case ".audio/mpeg4-generic":
                            fileExt = ".mpg";
                            break;
                        case "video/mpeg":
                        case ".video/mpeg":
                        case "video/mp4":
                        case ".video/mp4":
                            fileExt = ".mp4";
                            break;
                        case "message/rfc822":
                        case ".message/rfc822":
                            fileExt = ".eml";
                            break;
                        default:
                            if (fileExt.charAt(0) != '.') {
                                fileExt = "." + fileExt;
                            }
                            break;
                    }
                    encoding.fileExt = fileExt;
                    break;
                case "vers:Document/vers:Encoding/vers:EncodingMetadata/vers:SourceFileIdentifier":
                    if (finalVersion) {
                        encoding.sourceFileName = value;
                    }
                    break;
                case "vers:VERSEncapsulatedObject/vers:SignedObject/vers:ObjectContent/vers:ModifiedVEO/vers:OriginalVEO/vers:SignedObject/vers:ObjectContent/vers:Record/vers:Document/vers:Encoding/vers:DocumentData":
                case "vers:Document/vers:Encoding/vers:DocumentData":
                    if (encoding != null && encoding.fileLocation != null) {
                        try {
                            encoding.fileSize = Files.size(encoding.fileLocation);
                        } catch (IOException ioe) {
                            LOG.log(Level.INFO, "V2Process.V2VEOParser.endElement(): failed getting size of file: {0}", ioe.getMessage());
                            encoding.fileSize = 0;
                        }
                    }
                    break;
                default:
                    break;
            }
        }

        private String id2JSON() {
            String s;
            boolean output;

            s = "{";
            output = false;
            if (agencyId != null) {
                s += "\"agencyId\":\"" + agencyId + "\"";
                output = true;
            }
            if (seriesId != null) {
                if (output) {
                    s += ", ";
                }
                s += "\"seriesId\":\"" + seriesId + "\"";
                output = true;
            }
            if (fileId != null) {
                if (output) {
                    s += ", ";
                }
                s += "\"fileId\":\"" + fileId + "\"";
                output = true;
            }
            if (recordId != null) {
                if (output) {
                    s += ", ";
                }
                s += "\"recordId\":\"" + recordId + "\"";
                output = true;
            }
            s += "}";
            return s;
        }
    }
}
