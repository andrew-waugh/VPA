/*
 * Copyright Public Record Office Victoria 2018
 * Licensed under the CC-BY license http://creativecommons.org/licenses/by/3.0/au/
 * Author Andrew Waugh
 * Version 1.0 February 2018
 */
package VPA;

import java.nio.file.Path;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * VEO Process
 *
 * This class kicks all the VEO processing off
 */
public class VPA {

    private final PIDService ps;          // class to encapsulate the PID Service
    private final Packages packages;      // Utility class to create the various packages
    private final V2Process v2p;          // class to process the V2 VEOs
    private final V3Process v3p;          // class to process the V3 VEOs
    private final FileFormat ff;          // class to contain file format info

    // logging
    private final static Logger LOG = Logger.getLogger("VPA.VPA");

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
     * @throws AppFatal if an error occurred that precludes further processing
     */
    public VPA(Path outputDir, Path supportDir, String rdfIdPrefix, Level logLevel, boolean useRealHandleService, String pidServURL, String pidUserId, String pidPasswd) throws AppFatal {

        // default logging
        LOG.getParent().setLevel(logLevel);
        LOG.setLevel(null);

        // set up PID Service
        ps = new PIDService(useRealHandleService, pidServURL, pidUserId, pidPasswd);

        // set up output packaging
        ff = new FileFormat(supportDir);
        packages = new Packages(ff);

        // set up V2 and V3 processors
        v2p = new V2Process(ps, rdfIdPrefix, supportDir, packages, logLevel);
        v3p = new V3Process(ps, outputDir, supportDir, packages, logLevel);
    }

    public void free() {
        ff.free();
    }

    /**
     * Process a file containing a VEO. A file ending in a '.veo' is assumed to
     * be a V2 VEO, and a file ending in '.zip' a V3 VEO (note that there are
     * lots of ZIP files that are not VEOs; these will be picked up when
     * parsing).
     *
     * An AppFatal error will be thrown if the VPA itself fails (typically a API
     * call failed when it should always succeed). An AppError will be thrown if
     * VEO processing failed for reasons other than an error in the VEO (e.g.
     * passed a null path to process). For any other issues, a VEOResult will be
     * returned containing details about the results of the processing.
     *
     * @param setMetadata metadata to be added to the VEO from the set (in JSON)
     * @param veo the file containing the VEO
     * @param packageDir directory in which to create the packages
     * @return the result of processing the VEO
     * @throws AppFatal if a system error occurred
     * @throws AppError processing failed, but further VEOs can be submitted
     */
    public VEOResult process(String setMetadata, Path veo, Path packageDir) throws AppFatal, AppError {
        int i;
        String recordName;      // name of this record element (from the file, without the final '.xml')
        VEOResult res;          // result of processing the VEO

        // check parameters
        if (veo == null) {
            throw new AppError("Passed null VEO file to be processed");
        }

        // get the record name from the file name minus the file extension ('.veo' or '.zip')
        String s = veo.getFileName().toString();
        if ((i = s.lastIndexOf('.')) != -1) {
            recordName = s.substring(0, i);
        } else {
            recordName = s;
        }

        // create the package directories
        packages.createDirs(packageDir);

        // ensure that the set metadata is a straight collection of JSON properties
        // (i.e. strip off any leading '"set": {' or '{', and any trailing '}')
        setMetadata = setMetadata.trim();
        if (setMetadata.startsWith("\"set\"")) {
            setMetadata = setMetadata.substring("\"set\"".length()).trim();
        }
        if (setMetadata.startsWith(":")) {
            setMetadata = setMetadata.substring(":".length()).trim();
        }
        if (setMetadata.startsWith("{")) {
            setMetadata = setMetadata.substring("{".length()).trim();
        }
        if (setMetadata.endsWith("}")) {
            setMetadata = setMetadata.substring(0, setMetadata.length() - "}".length()).trim();
        }

        // process the veo file
        try {
            if (veo.toString().toLowerCase().endsWith(".veo")) {
                res = v2p.process(setMetadata, veo, recordName, packageDir);
            } else if (veo.toString().toLowerCase().endsWith(".zip")) {
                res = v3p.process(setMetadata, veo, recordName, packageDir);
            } else {
                throw new AppError("Error processing '" + veo.toString() + "' as file must end in '.zip' (V3) or '.veo' (V2)");
            }
        } finally {
            System.gc();
        }
        return res;
    }
}
