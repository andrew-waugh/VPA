/*
 * Copyright Public Record Office Victoria 2018
 * Licensed under the CC-BY license http://creativecommons.org/licenses/by/3.0/au/
 * Author Andrew Waugh
 * Version 1.0 February 2018
 * Version 1.1 25 June 2018 The options for copying, moving, or linking content
 * files were removed as files are now directly ZIPped from the original files.
 * Version 1.2 15 Aug 2018 Significant testing and revision
 */
package VPA;

import VERSCommon.AppFatal;
import VERSCommon.AppError;
import VERSCommon.LTSF;
import VERSCommon.ResultSummary;
import VERSCommon.VEOFatal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

/**
 *
 * VEO Process
 *
 * This class kicks all the VEO processing off
 */
public final class VPA {

    private PIDService ps;          // class to encapsulate the PID Service
    private Packages packages;      // Utility class to create the various packages
    private V2Process v2p;          // class to process the V2 VEOs
    private V3Process v3p;          // class to process the V3 VEOs
    private FileFormat ff;          // class to contain file format info
    private LTSF ltsf;              // valid long term preservation formats

    // logging
    private final static Logger LOG = Logger.getLogger("VPA.VPA");
    
    /**
     * Report on version...
     *
     * <pre>
     * 20180601 1.0 Initial release
     * 20180626 1.1 Bug fixes & improved handling of PID testing
     * 20180813 2.0 Major revision, bug fixes & improvements
     * 20180824 2.1 Updated ids in SAMS, bug fixes
     * 20180831 2.2 Minor fixes
     * 20181031 2.3 More bug fixes, particularly around handling of JSON
     * 20190727 2.4 More bug fixes
     * 20191107 2.5 Improved error messages
     * 20191122 2.6 Added -lite mode & improved diagnostic messages
     * 20191204 2.7 Added -sample & -ignoreAbove mode
     * 20200113 2.8 Now reports file name instead of file path
     * 20200505 2.9 Bug fixes
     * 20200213 2.10 Temporary alteration handling of titles to test AMS
     * 20200217 2.11 Minor bug fix
     * 20200219 2.12 Added parentIPSeqNo to JSON a/c request AMS team
     * 20200224 2.13 Altered PIDs a/c request SAMS team
     * 20200311 2.14 ipLabel is not output if null a/c request AMS team & changed handling of object identifiers
     * 20200320 2.15 Fixed bug in reading non-standard metadata
     * 20200605 2.16 Added migration flag for VEO migration from old DSA
     * 20200716 2.17 Changed to use common LTSF handling for V2 & V3 VEOs
     * 20200306 2.18 Added support for a result summary
     * 20210412 2.19 Added version, and standardised reporting in run
     * 20210709 2.20 Added support for PISA in BAT file
     * </pre>
     */
    static String version() {
        return ("2.20");
    }

    /**
     * Set up for processing VEOs
     *
     * @param outputDir directory in which packages are to be generated
     * @param supportDir directory which contains support information (e.g. VEO
     * schemas)
     * @param rdfIdPrefix prefix to be used to construct RDF
     * @param logLevel logging level (INFO = verbose, FINE = debug)
     * @param useRealHandleService true if the real handle service is to be used
     * @param pidServURL URL of the PID server
     * @param pidUserId user id to log into the PID server
     * @param pidPasswd password of user logging into the PID server
     * @param pidPrefix prefix of the PID
     * @param targetURL target URL of JSON request to PID server
     * @param author author of JSON request to PID server
     * @param light true if just testing the VEO, not processing it
     * @throws AppFatal if an error occurred that precludes further processing
     */
    public VPA(Path outputDir, Path supportDir, String rdfIdPrefix, Level logLevel, boolean useRealHandleService, String pidServURL, String pidUserId, String pidPasswd, String pidPrefix, String targetURL, String author, boolean light) throws AppFatal {
        VPAConstInt(outputDir, supportDir, rdfIdPrefix, logLevel, useRealHandleService, pidServURL, pidUserId, pidPasswd, pidPrefix, targetURL, author, false, light, null);
    }
    
        /**
     * Set up for processing VEOs
     *
     * @param outputDir directory in which packages are to be generated
     * @param supportDir directory which contains support information (e.g. VEO
     * schemas)
     * @param rdfIdPrefix prefix to be used to construct RDF
     * @param logLevel logging level (INFO = verbose, FINE = debug)
     * @param useRealHandleService true if the real handle service is to be used
     * @param pidServURL URL of the PID server
     * @param pidUserId user id to log into the PID server
     * @param pidPasswd password of user logging into the PID server
     * @param pidPrefix prefix of the PID
     * @param targetURL target URL of JSON request to PID server
     * @param author author of JSON request to PID server
     * @param migration true if migrating from old DSA - back off on some of the
     * validation
     * @param light true if just testing the VEO, not processing it
     * @throws AppFatal if an error occurred that precludes further processing
     */
    public VPA(Path outputDir, Path supportDir, String rdfIdPrefix, Level logLevel, boolean useRealHandleService, String pidServURL, String pidUserId, String pidPasswd, String pidPrefix, String targetURL, String author, boolean migration, boolean light) throws AppFatal {
        VPAConstInt(outputDir, supportDir, rdfIdPrefix, logLevel, useRealHandleService, pidServURL, pidUserId, pidPasswd, pidPrefix, targetURL, author, migration, light, null);
    }
    
    /**
     * Set up for processing VEOs. This option adds the ability to produce a
     * result summary.
     *
     * @param outputDir directory in which packages are to be generated
     * @param supportDir directory which contains support information (e.g. VEO
     * schemas)
     * @param rdfIdPrefix prefix to be used to construct RDF
     * @param logLevel logging level (INFO = verbose, FINE = debug)
     * @param useRealHandleService true if the real handle service is to be used
     * @param pidServURL URL of the PID server
     * @param pidUserId user id to log into the PID server
     * @param pidPasswd password of user logging into the PID server
     * @param pidPrefix prefix of the PID
     * @param targetURL target URL of JSON request to PID server
     * @param author author of JSON request to PID server
     * @param migration true if migrating from old DSA - back off on some of the
     * validation
     * @param light true if just testing the VEO, not processing it
     * @param results if non-null, produce a result summary
     * @throws AppFatal if an error occurred that precludes further processing
     */
    public VPA(Path outputDir, Path supportDir, String rdfIdPrefix, Level logLevel, boolean useRealHandleService, String pidServURL, String pidUserId, String pidPasswd, String pidPrefix, String targetURL, String author, boolean migration, boolean light, ResultSummary results) throws AppFatal {
        VPAConstInt(outputDir, supportDir, rdfIdPrefix, logLevel, useRealHandleService, pidServURL, pidUserId, pidPasswd, pidPrefix, targetURL, author, migration, light, results);
    }

    /**
     * Set up for processing VEOs
     *
     * @param outputDir directory in which packages are to be generated
     * @param supportDir directory which contains support information (e.g. VEO
     * schemas)
     * @param rdfIdPrefix prefix to be used to construct RDF
     * @param logLevel logging level (INFO = verbose, FINE = debug)
     * @param useRealHandleService true if the real handle service is to be used
     * @param pidServURL URL of the PID server
     * @param pidUserId user id to log into the PID server
     * @param pidPasswd password of user logging into the PID server
     * @param pidPrefix prefix of the PID
     * @param targetURL target URL of JSON request to PID server
     * @param author author of JSON request to PID server
     * @param migration true if migrating from old DSA - back off on some of the
     * validation
     * @param light true if just testing the VEO, not processing it
     * @param results if non-null, produce a result summary
     * @throws AppFatal if an error occurred that precludes further processing
     */
    private void VPAConstInt(Path outputDir, Path supportDir, String rdfIdPrefix, Level logLevel, boolean useRealHandleService, String pidServURL, String pidUserId, String pidPasswd, String pidPrefix, String targetURL, String author, boolean migration, boolean light, ResultSummary results) throws AppFatal {

        // sanity checking
        if (outputDir == null) {
            throw new AppFatal("Passed null outputDir (VPA())");
        }
        if (!Files.exists(outputDir)) {
            throw new AppFatal("Output directory '" + outputDir.toString() + "' does not exist (VPA())");
        }
        if (!Files.isDirectory(outputDir)) {
            throw new AppFatal("Output directory '" + outputDir.toString() + "' is not a directory (VPA())");
        }
        if (supportDir == null) {
            throw new AppFatal("Passed null support directory (VPA())");
        }
        if (!Files.exists(supportDir)) {
            throw new AppFatal("Support directory '" + supportDir.toString() + "' does not exist (VPA())");
        }
        if (!Files.isDirectory(supportDir)) {
            throw new AppFatal("Support directory '" + supportDir.toString() + "' is not a directory (VPA())");
        }
        /* rdfIdPrefix is not used
        if (rdfIdPrefix == null) {
            throw new AppFatal("Passed null rdfIdPrefix (VPA())");
        }
         */
        if (useRealHandleService) {
            if (pidServURL == null) {
                throw new AppFatal("Passed null URL for the persistent id server (VPA())");
            }
            if (pidUserId == null) {
                throw new AppFatal("Passed null user id for the persistent id server (VPA())");
            }
            if (pidPasswd == null) {
                throw new AppFatal("Passed null user password for the persistent id server (VPA())");
            }
            if (pidPrefix == null) {
                throw new AppFatal("Passed null prefix for the persistent ids (VPA())");
            }
            if (targetURL == null) {
                throw new AppFatal("Passed null target URL for the persistent ids (VPA())");
            }
            if (author == null) {
                throw new AppFatal("Passed null author for the persistent ids (VPA())");
            }
        }

        // default logging
        LOG.getParent().setLevel(logLevel);
        LOG.setLevel(null);

        // set up PID Service
        ps = new PIDService(useRealHandleService, pidServURL, pidUserId, pidPasswd, pidPrefix, targetURL, author);

        // set up output packaging
        ff = new FileFormat(supportDir);
        packages = new Packages(ff);
        
        // get valid long term sustainable formats
        try {
            ltsf = new LTSF(supportDir.resolve("validLTSF.txt"));
        } catch (VEOFatal vf) {
            throw new AppFatal(vf.getMessage());
        }

        // set up V2 and V3 processors
        migration = true; // remove this in production!!! Set for now to force migration during migration of V2 VEOs to new DAS.
        v2p = new V2Process(ps, ff, rdfIdPrefix, supportDir, ltsf, packages, logLevel, migration, light, results);
        v3p = new V3Process(ps, outputDir, supportDir, ltsf, packages, logLevel, light, results);
    }

    /**
     * Destroy this instance of the VPA
     */
    public void free() {
        // ps.free();
        ff.free();
        // packages.free();
        // v2p.free();
        // v3p.free();
    }

    /**
     * Process VEO
     *
     * Process a file containing a VEO. This method is only used for the
     * *initial* processing on ingest
     *
     * @param setMetadata metadata about the set as a whole
     * @param veo	the file to parse
     * @param veoOutputDir directory that will contain result of processing VEO
     * @return a VEOResult containing the results of the processing
     * @throws AppFatal if a system error occurred
     * @throws AppError if an unexpected error occurred processing this VEO
     */
    public VEOResult process(String setMetadata, Path veo, Path veoOutputDir) throws AppFatal, AppError {
        return process(setMetadata, veo, veoOutputDir, null);
    }

    /**
     * Reprocess VEO
     *
     * Reprocess a file containing a VEO. This method is only used to reprocess
     * a VEO to provide access to closed content
     *
     * @param setMetadata metadata about the set as a whole
     * @param veo	the file to parse
     * @param veoOutputDir directory in which to place the VEO output
     * @param pids VEO and IO PIDS from original processing.
     * @return a VEOResult containing the results of the processing
     * @throws AppFatal if a system error occurred
     * @throws AppError if an unexpected error occurred processing this VEO
     */
    public VEOResult reprocess(String setMetadata, Path veo, Path veoOutputDir, String pids) throws AppFatal, AppError {

        // check parameters
        if (pids == null) {
            throw new AppError("VPA.reprocess(): Passed null PIDS JSON object to be processed");
        }
        return process(setMetadata, veo, veoOutputDir, pids);
    }

    /**
     * Process a file containing a VEO. A file ending in a '.veo' is assumed to
     * be a V2 VEO, and a file ending in '.zip' a V3 VEO (note that there are
     * lots of ZIP files that are not VEOs; these will be picked up when
     * parsing).
     *
     * An AppFatal error will be thrown if the VPA itself fails (typically a API
     * call failing when it should always succeed). An AppError will be thrown
     * if VEO processing failed for reasons other than an error in the VEO (e.g.
     * passed a null path to process). For any other issues, a VEOResult will be
     * returned containing details about the results of the processing.
     *
     * The veo and veoOutputDir must not be null. The veoOutputDir must exist
     * and be a directory. The setMetadata may be null.
     *
     * The set metadata is a simple list of JSON properties. This are included
     * verbatim in the AMS package in each RecordItem.
     *
     * The pids is a JSON object containing the VEO and IO PIDS associated with
     * the VEO. This argument must be null when the VEO is first processed (i.e.
     * on ingest), and be non-null when the VEO is reprocessed (i.e. to provide
     * access to closed content).
     *
     * @param setMetadata set metadata to be added to the AMS package
     * @param veo the file containing the VEO
     * @param veoOutputDir directory that will contain result of processing VEO
     * @param pids a JSON string containing the VEO and IO PIDS
     * @return the result of processing the VEO
     * @throws AppFatal if a system error occurred
     * @throws AppError processing failed, but further VEOs can be submitted
     */
    public VEOResult process(String setMetadata, Path veo, Path veoOutputDir, String pids) throws AppFatal, AppError {
        int i;
        String recordName;  // name of this record element (from the file, without the final '.xml')
        VEOResult res;      // result of processing the VEO
        JSONParser parser = new JSONParser();
        JSONObject sm;      // set metadata expressed as JSON
        JSONObject pd;      // VEO and IO PIDS expressed as JSON

        // check parameters
        if (veo == null) {
            throw new AppError("Passed null VEO file to be processed (VPA.process())");
        }
        if (veoOutputDir == null) {
            throw new AppError("Passed null package directory (VPA.process())");
        }
        if (!Files.exists(veoOutputDir)) {
            throw new AppError("Package directory '" + veoOutputDir.toString() + "' does not exist (VPA.process())");
        }
        if (!Files.isDirectory(veoOutputDir)) {
            throw new AppError("Package directory '" + veoOutputDir.toString() + "' is not a directory (VPA.process())");
        }

        // parse the set metadata into JSON
        sm = null;
        if (setMetadata != null) {
            try {
                sm = (JSONObject) parser.parse(setMetadata);
            } catch (ParseException pe) {
                throw new AppError("Set metadata '" + setMetadata + "' is not valid JSON: " + pe.toString() + " (VPA.process())");
            }
        }

        // parse the PIDS information into JSON
        pd = null;
        if (pids != null) {
            try {
                pd = (JSONObject) parser.parse(pids);
            } catch (ParseException pe) {
                throw new AppError("Set metadata '" + pids + "' is not valid JSON: " + pe.toString() + " (VPA.process())");
            }
        }

        // get the record name from the file name minus the file extension ('.veo' or '.zip')
        String s = veo.getFileName().toString();
        if ((i = s.lastIndexOf('.')) != -1) {
            recordName = s.substring(0, i);
        } else {
            recordName = s;
        }

        // create the package directories
        packages.createDirs(veoOutputDir);

        // process the veo file
        try {
            if (veo.toString().toLowerCase().endsWith(".veo")) {
                res = v2p.process(sm, veo, recordName, veoOutputDir, pd);
            } else if (veo.toString().toLowerCase().endsWith(".zip")) {
                res = v3p.process(sm, veo, recordName, veoOutputDir, pd);
            } else {
                throw new AppError("Error processing '" + veo.toString() + "' as file must end in '.zip' (V3) or '.veo' (V2)");
            }
            sm = null;
        } finally {
            System.gc();
        }
        return res;
    }
}
