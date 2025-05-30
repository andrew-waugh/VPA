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
import VERSCommon.LTSF;
import VERSCommon.ResultSummary;
import VERSCommon.VEOError;
import java.io.BufferedOutputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.StringWriter;
import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.xml.sax.*;

public class V2Process {

    private final Packages packages;      // Utility class to create the various packages
    private final VEOCheck veoc;          // V2 VEO validation class
    private final V2VEOParser parser;     // parser to process the .veo (XML) file
    private final PIDService ps;          // Class to encapsulate the PID service
    private final String rdfIdPrefix;     // prefix to be used to generate RDF identifiers
    private final FileFormat ff;          // mappings between Mime types and file formats
    private String veoFormatDesc;         // VEO Format Description from currently parsed VEO
    private String version;               // version from currently parsed VEO
    private final ArrayList<String> sigBlock; // signature blocks from currently parsed VEO
    private String uniqueID;              // unique ID of this VEO (the first signature)
    private final ArrayList<String> lockSigBlock; // lock signature blocks from currently parsed VEO
    private String signedObject;          // signedObject from currently parsed VEO
    private final boolean light;          // if true, only test VEO, don't process it

    private final static Logger LOG = Logger.getLogger("VPA.V2Process");

    /**
     * Constructor. Set up for processing V2 VEOs
     *
     * @param ps the encapsulation of the PID service
     * @param ff mapping between MIME types and file formats
     * @param rdfIdPrefix RDF ID prefix to be used in generating RDF
     * @param supportDir directory where the versV2.dtd file is located
     * @param ltsf list of long term sustainable formats
     * @param packages methods that generate the packages
     * @param logLevel logging level (INFO = verbose, FINE = debug)
     * @param migration true if migrating from old DSA - back off on some of the
     * validation
     * @param light true if only test the VEO, don't process it
     * @param results if non-null, produce a result summary
     * @throws AppFatal if a fatal error occurred
     */
    public V2Process(PIDService ps, FileFormat ff, String rdfIdPrefix, Path supportDir, LTSF ltsf, Packages packages, Level logLevel, boolean migration, boolean light, ResultSummary results) throws AppFatal {
        Path dtd;

        LOG.setLevel(null);
        this.ff = ff;
        this.ps = ps;
        this.rdfIdPrefix = rdfIdPrefix;
        this.packages = packages;
        this.light = light;
        dtd = supportDir.resolve("vers2.dtd");

        // set up headless validation
        veoc = new VEOCheck(dtd, logLevel, ltsf, true, migration, results);

        // set up parser
        parser = new V2VEOParser();

        veoFormatDesc = null;
        version = null;
        sigBlock = new ArrayList<>();
        uniqueID = null;
        lockSigBlock = new ArrayList<>();
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
     * @param setMetadata metadata about the set as a whole (may be null)
     * @param veo	the file to parse
     * @param recordName the name of the Information Object to be produced
     * @param packageDir the directory where the VEO is to be processed
     * @param pids VEO and IO PIDS from original processing. This must be null
     * for the initial ingest
     * @return a result structure showing the results of processing the VEO
     * @throws AppFatal if a system error occurred
     * @throws AppError processing failed, but further VEOs can be submitted
     */
    public VEOResult process(JSONObject setMetadata, Path veo, String recordName, Path packageDir, JSONObject pids) throws AppFatal, AppError {
        StringWriter out;           // writer to capture abbreviated VEO
        ArrayList<InformationObject> ios; // IOs read from VEO
        InformationObject io;       // current information object
        ArrayList<Event> events;    // list of events read
        String veoPID;              // PID assigned to VEO
        Instant started;            // instant processing started
        int i;
        Path p;

        // check parameters (veo and packageDir checked for null in VPA)
        if (recordName == null) {
            throw new AppError("V2Process.process(): Passed null recordName");
        }
        started = Instant.now();
        LOG.log(Level.INFO, "Processing ''{0}''", new Object[]{veo.toAbsolutePath().toString()});

        // create a subdirectory to hold all the document data from the VEO
        try {
            Files.createDirectory(packageDir.resolve("docdata"));
        } catch (IOException ioe) {
            throw new AppError("V2Process.process: Failed to create directory: " + packageDir.resolve("docdata") + ": " + ioe.getMessage());
        }

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
            uniqueID = null;
            lockSigBlock.clear();
            signedObject = null;
            parser.parse(veo, packageDir, io, events);

            // create abbreviated VEO, and validate it
            out = new StringWriter();
            p = packageDir.resolve("abreviatedVEO.xml");
            createAbbrVEO(p);
            if (!veoc.vpaTestVEO(veo, p, out)) {
                return new VEOResult(recordName, VEOResult.V2_VEO, false, out.toString(), null, started);
            }

            // are we just testing the VEO?
            if (light) {
                return new VEOResult(recordName, VEOResult.V2_VEO, true, out.toString(), null, started);
            }

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

            // create AMS and SAMS outputs
            packages.createAMSPackage(setMetadata, uniqueID, ios, events);
            packages.createSAMSPackage(ios);
            packages.createDASPackage(veo, veoPID, ios);
        } catch (AppFatal af) {
            throw af;
        } catch (VEOError | AppError e) {
            return new VEOResult(recordName, VEOResult.V2_VEO, false, e.getMessage(), null, started);
        } finally {
            for (i = 0; i < ios.size(); i++) {
                ios.get(i).free();
            }
            ios.clear();
            for (i = 0; i < events.size(); i++) {
                events.get(i).free();
            }
            events.clear();
            veoFormatDesc = null;
            version = null;
            sigBlock.clear();
            uniqueID = null;
            lockSigBlock.clear();
            signedObject = null;
        }
        return new VEOResult(recordName, VEOResult.V2_VEO, true, out.toString(), packageDir, started);
    }

    /**
     * Produce a abbreviated version of V2 VEO for validation. The main
     * alteration is the removal of all DocumentData to allow easier processing
     * by DOM
     *
     * @param p path of the V2 VEO to be abbreviated
     * @throws AppError
     * @throws AppFatal
     */
    public void createAbbrVEO(Path p) throws AppError, AppFatal {
        FileOutputStream fos;
        BufferedOutputStream bos;
        OutputStreamWriter osw;
        int i;

        // create the abbreviated VEO file
        try {
            fos = new FileOutputStream(p.toFile());
        } catch (FileNotFoundException fnfe) {
            throw new AppError("Couldn't create abbreviated VEO file as: " + fnfe.getMessage() + " (V2Process.createAbbrVEO())");
        }
        bos = new BufferedOutputStream(fos);
        osw = null;
        try {
            osw = new OutputStreamWriter(bos, "UTF-8");
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
        } catch (UnsupportedEncodingException uee) {
            throw new AppFatal("Panic: " + uee.getMessage() + " (V2Process.createdAbbrVEO())");
        } catch (IOException ioe) {
            throw new AppError("Failed writing to the abbreviated VEO file as: " + ioe.getMessage() + " (V2Process.createAbbrVEO())");
        } finally {
            try {
                if (osw != null) {
                    osw.close();
                }
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
     * Private class to encapsulate reading a V2 VEO file.
     *
     * DANGER, DANGER, WILL ROBINSON! Be careful maintaining this code. The
     * parser does not check the VEO against the DTD before or during the parse
     * (validation of the VEO is done *after* parsing). This means that elements
     * may appear out of order (or be missing entirely). If you allocate an
     * object when you see one element, and then use the object when you later
     * see another, you need to be careful that the object actually has been
     * allocated before you use it. The hierarchy of elements can be depended on
     * if you check the hierarchy when matching an element (i.e. if you allocate
     * when seeing element 'x', then use it when seeing 'x/a', this will always
     * work).
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
        private String documentSource;      // contents of vers:DocumentSource from current document
        private int encNo;                  // encoding number
        private ContentFile encoding;       // current encoding being parsed
        private HashMap<String, ContentFile> encodingsToFind; // list of encodingsToFind we are looking for
        private ArrayList<Event> events;    // list of events from VEO
        private Event event;                // current vers:Event being parsed
        private Path veoDir;                // directory in which to put content
        private Identifier id;              // identifier being read
        private boolean light;              // don't extract document data
        private String cpDomain;                // a context path domain in a metadata package
        private String cpValue;                 // a context path value in a metadata package

        public V2VEOParser() throws AppFatal {
            clear();
            parser = new XMLParser(this);
        }

        /**
         * Set globals to a standard state
         */
        private void clear() {
            seqNo = 1;
            finalVersion = true;
            relation = null;
            io = null;
            docNo = 0;
            document = null;
            encNo = 0;
            encoding = null;
            if (encodingsToFind != null) {
                encodingsToFind.clear();
            }
            encodingsToFind = null;
            events = null;
            event = null;
            veoDir = null;
            if (id != null) {
                id.free();
            }
            id = null;
        }

        /**
         * Parse the veoFile. It returns a single Information Object (as a V2
         * VEO only represents a single IO) and a list of events.
         */
        public void parse(Path veoFile, Path veoDir, InformationObject io, ArrayList<Event> events) throws AppError, AppFatal {

            // set up parse
            clear();
            emptyVEO = true;
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
                clear();
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
                case "vers:VERSEncapsulatedObject/vers:SignatureBlock/vers:Signature":
                    if (uniqueID == null) {
                        return new HandleElement(HandleElement.VALUE_TO_STRING);
                    }
                    break;
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
                case "vers:RecordMetadata/naa:ManagementHistory/vers:ManagementEvent": //tc
                case "vers:FileMetadata/naa:ManagementHistory/vers:ManagementEvent": // tc
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
                    documentSource = null;
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
                    encoding.versId = versid;

                    // if we still have no valid source file name, use the
                    // versId
                    if (encoding.sourceFileName == null) {
                        encoding.sourceFileName = encoding.versId + "." + encoding.fileExt;
                    }

                    // if light, don't include the DocumentData
                    if (light) {
                        he = null;
                        break;
                    }

                    // only include this DocumentData if it is in the final revision
                    // or it was referred to from the final revision
                    // NOTE if this DocumentData is in an older (modified version)
                    // we will have already seen the final Encoding element as
                    // the RevisedVEO element occurs earlier in the VEO. We recorded it
                    // then, so we get the recorded information.
                    if (!finalVersion) {
                        encoding = encodingsToFind.get(versid);
                        if (encoding == null) {
                            he = null;
                            break;
                        }
                        encodingsToFind.remove(versid);
                    }

                    // in modified VEOs, the vers:DocumentData element may not contain the element,
                    // but refer to another vers:DocumentData element that has the element
                    if (attributes != null) {
                        ref = attributes.getValue("vers:forContentsSeeElement");
                        if (ref != null) {
                            encoding.fileLocation = null;
                            encoding.rootFileLocn = null;
                            encoding.refDoc = ref;
                            encodingsToFind.put(ref, encoding);
                            he = null;
                            break;
                        }
                        ref = attributes.getValue("vers:forContentsSeeOriginalDocumentAndEncoding");
                        if (ref != null) {
                            encoding.fileLocation = null;
                            encoding.rootFileLocn = null;
                            encoding.refDoc = ref;
                            encodingsToFind.put(ref, encoding);
                            he = null;
                            break;
                        }
                    }

                    // put all the content files in a docdata directory in the unpacked directory
                    encoding.rootFileLocn = veoDir.resolve("docdata");
                    if (encoding.fileExt != null) {
                        encoding.fileLocation = Paths.get((versid + "." + encoding.fileExt));
                    } else {
                        encoding.fileLocation = Paths.get(versid);
                    }
                    encoding.seqNbr = seqNo;
                    seqNo++;
                    he = new HandleElement(HandleElement.VALUE_TO_FILE, encoding.base64, encoding.rootFileLocn.resolve(encoding.fileLocation));
                    break;
                case "vers:RecordMetadata/vers:VEOIdentifier": //tc
                case "vers:FileMetadata/vers:VEOIdentifier": // tc
                    if (finalVersion) {
                        id = new Identifier();
                    }
                    break;
                case "vers:RecordMetadata/naa:Relation": //tc
                case "vers:FileMetadata/naa:Relation": //tc
                    if (finalVersion) {
                        relation = new Relationship();
                    }
                    break;
                case "vers:RecordMetadata/naa:Relation/naa:RelatedItemId": //tc
                case "vers:FileMetadata/naa:Relation/naa:RelatedItemId": //tc
                    if (finalVersion) {
                        he = new HandleElement(HandleElement.VALUE_TO_STRING);
                        id = new Identifier();
                    }
                    break;
                case "vers:RecordMetadata/naa:Title/naa:TitleWords": //tc
                case "vers:FileMetadata/naa:Title/naa:TitleWords": //tc
                case "vers:RecordMetadata/naa:Description": //tc
                case "vers:FileMetadata/naa:Description": //tc
                case "vers:RecordMetadata/naa:Relation/naa:RelatedItemId/vers:VEOIdentifier/vers:AgencyIdentifier/vers:Text": //tc
                case "vers:FileMetadata/naa:Relation/naa:RelatedItemId/vers:VEOIdentifier/vers:AgencyIdentifier/vers:Text": //tc
                case "vers:RecordMetadata/naa:Relation/naa:RelatedItemId/vers:VEOIdentifier/vers:SeriesIdentifier/vers:Text": //tc
                case "vers:FileMetadata/naa:Relation/naa:RelatedItemId/vers:VEOIdentifier/vers:SeriesIdentifier/vers:Text": //tc
                case "vers:RecordMetadata/naa:Relation/naa:RelatedItemId/vers:VEOIdentifier/vers:FileIdentifier/vers:Text": //tv
                case "vers:FileMetadata/naa:Relation/naa:RelatedItemId/vers:VEOIdentifier/vers:FileIdentifier/vers:Text": //tc
                case "vers:RecordMetadata/naa:Relation/naa:RelatedItemId/vers:VEOIdentifier/vers:VERSRecordIdentifier/vers:Text": //tc
                case "vers:FileMetadata/naa:Relation/naa:RelatedItemId/vers:VEOIdentifier/vers:VERSRecordIdentifier/vers:Text": //tc
                case "vers:RecordMetadata/naa:Relation/naa:RelationType": //tc
                case "vers:FileMetadata/naa:Relation/naa:RelationType": // tc
                case "vers:RecordMetadata/naa:Relation/naa:RelationDescription": //tc
                case "vers:FileMetadata/naa:Relation/naa:RelationDescription": //tc
                case "vers:RecordMetadata/naa:Date/naa:DateTimeCreated": //tc
                case "vers:FileMetadata/vers:Date/naa:DateTimeCreated": //tc
                case "vers:RecordMetadata/naa:Date/naa:DateTimeTransacted": //tc
                case "vers:FileMetadata/vers:Date/naa:DateTimeTransacted": //tc
                case "vers:RecordMetadata/naa:Date/naa:DateTimeRegistered": //tc
                case "vers:FileMetadata/vers:Date/naa:DateTimeRegistered": //tc
                case "vers:FileMetadata/vers:Date/vers:DateTimeClosed": //tc
                case "vers:RecordMetadata/naa:Coverage/naa:Jurisdiction": //tc
                case "vers:FileMetadata/naa:Coverage/naa:Jurisdiction": //tc
                case "vers:RecordMetadata/naa:Coverage/naa:Jurisdication": // spelling error in standard!
                case "vers:FileMetadata/naa:Coverage/naa:Jurisdication": // tc
                case "vers:RecordMetadata/naa:Coverage/naa:PlaceName": //tc
                case "vers:FileMetadata/naa:Coverage/naa:PlaceName": //tc
                case "vers:RecordMetadata/naa:Disposal/naa:DisposalAuthorisation": //tc
                case "vers:FileMetadata/naa:Disposal/naa:DisposalAuthorisation": //tc
                case "vers:RecordMetadata/naa:ManagementHistory/vers:ManagementEvent/naa:EventDateTime": //tc
                case "vers:FileMetadata/naa:ManagementHistory/vers:ManagementEvent/naa:EventDateTime": //tc
                case "vers:RecordMetadata/naa:ManagementHistory/vers:ManagementEvent/naa:EventType": //tc
                case "vers:FileMetadata/naa:ManagementHistory/vers:ManagementEvent/naa:EventType"://tc
                case "vers:RecordMetadata/naa:ManagementHistory/vers:ManagementEvent/naa:EventDescription": //tc
                case "vers:FileMetadata/naa:ManagementHistory/vers:ManagementEvent/naa:EventDescription": //tc
                case "vers:RecordMetadata/vers:VEOIdentifier/vers:AgencyIdentifier/vers:Text": //tc
                case "vers:FileMetadata/vers:VEOIdentifier/vers:AgencyIdentifier/vers:Text": //tc
                case "vers:RecordMetadata/vers:VEOIdentifier/vers:SeriesIdentifier/vers:Text": //tc
                case "vers:FileMetadata/vers:VEOIdentifier/vers:SeriesIdentifier/vers:Text": //tc
                case "vers:RecordMetadata/vers:VEOIdentifier/vers:FileIdentifier/vers:Text": //tc
                case "vers:FileMetadata/vers:VEOIdentifier/vers:FileIdentifier/vers:Text": //tc
                case "vers:RecordMetadata/vers:VEOIdentifier/vers:VERSRecordIdentifier/vers:Text": //tc
                case "vers:FileMetadata/vers:VEOIdentifier/vers:VERSRecordIdentifier/vers:Text":
                case "vers:Document/vers:DocumentMetadata/vers:DocumentTitle/vers:Text": //tc
                case "vers:Document/vers:DocumentMetadata/vers:DocumentSource/vers:Text": //tc
                case "vers:FileMetadata/versterms:contextPath/versterms:contextPathDomain":
                case "vers:FileMetadata/vers:ContextPath/vers:ContextPathDomain":
                case "vers:FileMetadata/versterms:contextPath/versterms:contextPathValue":
                case "vers:FileMetadata/vers:ContextPath/vers:ContextPathValue":
                    if (finalVersion) {
                        he = new HandleElement(HandleElement.VALUE_TO_STRING);
                    } else {
                        he = null;
                    }
                    break;
                case "vers:FileMetadata/vers:ContextPath":
                case "vers:FileMetadata/versterms:contextPath":
                    cpDomain = null;
                    cpValue = null;
                    he = null;
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
                case "vers:VERSEncapsulatedObject/vers:SignatureBlock/vers:Signature":
                    if (uniqueID == null) {
                        uniqueID = value;
                    }
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
                        io.titles.add(value);
                        // io.label = value; Removed a/c request from AMS team
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
                        relation.targetIds.add(id);
                        id = null;
                    }
                    break;
                case "vers:RecordMetadata/naa:Relation/naa:RelatedItemId/vers:VEOIdentifier/vers:AgencyIdentifier/vers:Text":
                case "vers:FileMetadata/naa:Relation/naa:RelatedItemId/vers:VEOIdentifier/vers:AgencyIdentifier/vers:Text":
                    if (finalVersion) {
                        id.agencyId = value;
                    }
                    break;
                case "vers:RecordMetadata/naa:Relation/naa:RelatedItemId/vers:VEOIdentifier/vers:SeriesIdentifier/vers:Text":
                case "vers:FileMetadata/naa:Relation/naa:RelatedItemId/vers:VEOIdentifier/vers:SeriesIdentifier/vers:Text":
                    if (finalVersion) {
                        id.seriesId = value;
                    }
                    break;
                case "vers:RecordMetadata/naa:Relation/naa:RelatedItemId/vers:VEOIdentifier/vers:FileIdentifier/vers:Text":
                case "vers:FileMetadata/naa:Relation/naa:RelatedItemId/vers:VEOIdentifier/vers:FileIdentifier/vers:Text":
                    if (finalVersion) {
                        id.fileId = value;
                    }
                    break;
                case "vers:RecordMetadata/naa:Relation/naa:RelatedItemId/vers:VEOIdentifier/vers:VERSRecordIdentifier/vers:Text":
                case "vers:FileMetadata/naa:Relation/naa:RelatedItemId/vers:VEOIdentifier/vers:VERSRecordIdentifier/vers:Text":
                    if (finalVersion) {
                        id.itemId = value;
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
                case "vers:FileMetadata/vers:Date/naa:DateTimeCreated":
                    if (finalVersion) {
                        io.dateCreated = value;
                        io.dates.add(new Date("DateTimeCreated", value));
                    }
                    break;
                case "vers:RecordMetadata/naa:Date/naa:DateTimeTransacted":
                case "vers:FileMetadata/vers:Date/naa:DateTimeTransacted":
                    if (finalVersion) {
                        io.dates.add(new Date("DateTimeTransacted", value));
                    }
                    break;
                case "vers:RecordMetadata/naa:Date/naa:DateTimeRegistered":
                case "vers:FileMetadata/vers:Date/naa:DateTimeRegistered":
                    if (finalVersion) {
                        io.dateRegistered = value;
                        io.dates.add(new Date("DateTimeRegistered", value));
                    }
                    break;
                case "vers:FileMetadata/vers:Date/vers:DateTimeClosed":
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
                        io.disposalAuthority.rdas.add(value);
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
                        event.descriptions.add(value);
                    }
                    break;
                case "vers:RecordMetadata/vers:VEOIdentifier/vers:AgencyIdentifier/vers:Text":
                case "vers:FileMetadata/vers:VEOIdentifier/vers:AgencyIdentifier/vers:Text":
                    if (finalVersion) {
                        id.agencyId = value;
                    }
                    break;
                case "vers:RecordMetadata/vers:VEOIdentifier/vers:SeriesIdentifier/vers:Text":
                case "vers:FileMetadata/vers:VEOIdentifier/vers:SeriesIdentifier/vers:Text":
                    if (finalVersion) {
                        id.seriesId = value;
                    }
                    break;
                case "vers:RecordMetadata/vers:VEOIdentifier/vers:FileIdentifier/vers:Text":
                case "vers:FileMetadata/vers:VEOIdentifier/vers:FileIdentifier/vers:Text":
                    if (finalVersion) {
                        id.fileId = value;
                    }
                    break;
                case "vers:RecordMetadata/vers:VEOIdentifier/vers:VERSRecordIdentifier/vers:Text":
                    if (finalVersion) {
                        id.itemId = value;
                    }
                    break;
                case "vers:FileMetadata/versterms:contextPath":
                case "vers:FileMetadata/vers:ContextPath":
                    if (finalVersion) {
                        io.addContextPath(cpDomain, cpValue);
                        cpDomain = null;
                        cpValue = null;
                    }
                    break;
                case "vers:FileMetadata/versterms:contextPath/versterms:contextPathDomain":
                case "vers:FileMetadata/vers:ContextPath/vers:ContextPathDomain":
                    if (finalVersion) {
                        cpDomain = value;
                    }
                    break;
                case "vers:FileMetadata/versterms:contextPath/versterms:contextPathValue":
                case "vers:FileMetadata/vers:ContextPath/vers:ContextPathValue":
                    if (finalVersion) {
                        cpValue = value;
                    }
                    break;
                case "vers:RecordMetadata/vers:VEOIdentifier":
                case "vers:FileMetadata/vers:VEOIdentifier":
                    if (finalVersion) {
                        io.addIdentifier(id);
                        id = null;
                    }
                    break;
                case "vers:Document/vers:DocumentMetadata/vers:DocumentTitle/vers:Text":
                    if (finalVersion) {
                        document.label = value;
                    }
                    break;
                // see the case for vers:Document/vers:Encoding
                case "vers:Document/vers:DocumentMetadata/vers:DocumentSource/vers:Text": //tc
                    if (finalVersion) {
                        documentSource = value;
                    }
                    break;

                case "vers:Document/vers:Encoding/vers:EncodingMetadata":
                    if (finalVersion) {
                        int i1;

                        // if no valid file extension was found in
                        // vers:RenderingKeywords (or the RenderingKeywords
                        // element was missing), attempt to obtain a file
                        // extension from vers:SourceFileIdentifier (if one was
                        // present).
                        if (encoding.fileExt == null && encoding.sourceFileName != null) {
                            i1 = encoding.sourceFileName.lastIndexOf(".");
                            if (i1 != -1 && i1 < encoding.sourceFileName.length()) {
                                encoding.fileExt = encoding.sourceFileName.substring(i1 + 1);
                                if (encoding.fileExt.equals("") || encoding.fileExt.trim().equals(" ")) {
                                    encoding.fileExt = null;
                                }
                            }
                        }

                        // if no valid source file name was found in
                        // vers:SourceFileIdentifier (or the SourceFileIdentifier
                        // element was missing), attempt to obtain one from
                        // vers:DocumentSource, but only if the contents of
                        // vers:DocumentSource looks like a file reference (i.e.
                        // has a file extension.
                        if (encoding.sourceFileName == null) {
                            if (documentSource != null && !documentSource.equals("") && !documentSource.trim().equals(" ")) {
                                String safe = documentSource.replaceAll("\\\\", "/");

                                Path p;
                                String filename;
                                try {
                                    p = Paths.get(safe);
                                    filename = p.getFileName().toString();
                                    if (filename.lastIndexOf(".") != -1) {
                                        encoding.sourceFileName = filename;
                                    }
                                } catch (InvalidPathException ipe) {
                                    try {
                                        URL url = new URL(safe);
                                        p = Paths.get(url.getPath());
                                        filename = p.getFileName().toString();
                                        if (filename.lastIndexOf(".") != -1) {
                                            encoding.sourceFileName = filename;
                                        }
                                    } catch (MalformedURLException | InvalidPathException e) {
                                        encoding.sourceFileName = null;
                                    }
                                }
                            }
                        }

                        // sanity check that the source file name has the correct
                        // file extension. If it does NOT, add the correct file
                        // extension to the end.
                        if (encoding.fileExt != null && encoding.sourceFileName != null) {
                            String s = encoding.sourceFileName;
                            i1 = s.lastIndexOf(".");
                            if (i1 == -1 || i1 == s.length()) {
                                encoding.sourceFileName = s + "." + encoding.fileExt;
                            } else {
                                String pExt = s.substring(i1 + 1).trim();
                                if (!pExt.toLowerCase().equals(encoding.fileExt.toLowerCase())) {
                                    encoding.sourceFileName = s + "." + encoding.fileExt;
                                }
                            }
                        }
                    }
                    break;
                // see the case for vers:Document/vers:Encoding
                case "vers:Document":
                    if (finalVersion) {
                        documentSource = null;
                    }
                    break;
                case "vers:Document/vers:Encoding/vers:EncodingMetadata/vers:FileRendering/vers:RenderingKeywords":
                    String[] s1;

                    if (!finalVersion) {
                        break;
                    }

                    // get the list of formats. We strip the leading and trailing
                    // quotes (if present), and split on either a space or a ';'
                    // to handle problem RenderingKeywords
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

                    // the final format is assumed to be the file type except if
                    // is .b64 or .B64. The format may
                    // be a file extension or a MIME type. If a file extension
                    // it should be prefixed by a '.', and not have a '.' if a
                    // MIME type, but we cannot depend on this. So, we first
                    // trim the format of white space and this becomes the
                    // candidate file extension. We then strip the leading '.'
                    // if present. The candidate format is then looked up in a
                    // list of recognised MIME types, and if it was found replace
                    // the candidate file extension. The result of all this is
                    // the file extension. NOTE that the stored file extension
                    // is without the leading '.'
                    encoding.fileExt = null;
                    if (s1.length > 0) {
                        String fileExt = s1[s1.length - 1].trim();
                        if (fileExt.startsWith(".")) {
                            fileExt = fileExt.substring(1);
                            if (fileExt.contains("b64") || fileExt.contains("B64")) {
                                break;
                            }
                        }
                        if ((s = ff.mimeType2FileExt(fileExt)) != null) {
                            fileExt = s;
                        }
                        encoding.fileExt = fileExt;
                    }
                    break;
                case "vers:Document/vers:Encoding/vers:EncodingMetadata/vers:SourceFileIdentifier":
                    if (finalVersion) {
                        Path p;

                        // convert Windows file separators to UNIX style
                        String safe = value.replaceAll("\\\\", "/");

                        // we are trying to find the source file name from the
                        // vers:SourceFileIdentifier. First, try to see if the
                        // vers:SourceFileIdentifier is a file system path; if
                        // so the name is the final component. If it isn't a
                        // file system path, see if it is a URL; if so extract
                        // the path component and the name is the final
                        // component. Otherwise, just use the whole
                        // vers:SourceFileIdentifier
                        try {
                            p = Paths.get(safe);
                            encoding.sourceFileName = p.getFileName().toString();
                        } catch (InvalidPathException ipe) {
                            try {
                                URL url = new URL(safe);
                                p = Paths.get(url.getPath());
                                encoding.sourceFileName = p.getFileName().toString();
                            } catch (MalformedURLException | InvalidPathException e) {
                                encoding.sourceFileName = null;
                            }
                        }
                        if (encoding.sourceFileName != null && (encoding.sourceFileName.equals("") || encoding.sourceFileName.trim().equals(" "))) {
                            encoding.sourceFileName = null;
                        }
                    }
                    break;
                case "vers:VERSEncapsulatedObject/vers:SignedObject/vers:ObjectContent/vers:ModifiedVEO/vers:OriginalVEO/vers:SignedObject/vers:ObjectContent/vers:Record/vers:Document/vers:Encoding/vers:DocumentData":
                case "vers:Document/vers:Encoding/vers:DocumentData":
                    if (encoding != null && encoding.fileLocation != null) {
                        try {
                            encoding.fileSize = Files.size(encoding.rootFileLocn.resolve(encoding.fileLocation));
                        } catch (IOException ioe) {
                            LOG.log(Level.INFO, "V2Process.V2VEOParser.endElement(): failed getting size of file: {0}", ioe.getMessage());
                            encoding.fileSize = 0;
                        }

                        // complain if vers:DocumentData is empty
                        if (encoding.fileSize == 0) {
                            throw new SAXException("Empty vers:DocumentData element");
                        }
                    }
                    break;
                default:
                    break;
            }
        }
    }
}
