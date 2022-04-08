/*
 * Copyright Public Record Office Victoria 2018
 * Licensed under the CC-BY license http://creativecommons.org/licenses/by/3.0/au/
 * Author Andrew Waugh
 * Version 1.0 February 2018
 */
package VPA;

/**
 *
 * DA Ingest
 *
 * This class emulates the DA Ingest process and passes VEO files for
 * processing.
 *
 * <li><b>[files|directories]+</b> a list of V2 VEO files, or directories
 * containing such files.</li>
 * </ul>
 * <p>
 * The following command line arguments are optional:
 * <li><b>-v</b> verbose output. By default off.</li>
 * <li><b>-d</b> debug mode. In this mode more logging will be generated. By
 * default off.</li>
 * <li><b>-o &lt;outputDir&gt;</b> the directory in which the VEOs are to be
 * created. If not present, the VEOs will be created in the current
 * directory.</li>
 * <li><b>-r &lt;rdfid&gt;</b> a prefix used to construct the RDF identifiers.
 * If not present the string file:///[pathname] is used.</li>
 * </ul>
 * <p>
 * A minimal example of usage is<br>
 * <pre>
 *     v2tov3 veo1.veo
 * </pre>
 *
 * 20180101 Based on TRIMExport
 */
import VERSCommon.AppFatal;
import VERSCommon.AppError;
import VERSCommon.ResultSummary;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.Charset;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.TimeZone;
import java.util.logging.Level;
import java.util.logging.Logger;

public class DAIngest {

    static String classname = "DAIngest"; // for reporting
    VPA vp;          // class to process the VEOs
    ArrayList<String> files;// files or directories to process
    Runtime r;

    // static final String PID_SERVER_URL = "http://192.168.240.135:80/handle-service/mintingHandle"; // internal
    static final String PID_SERVER_URL = "http://150.207.155.198:80/handle-service/mintingHandle"; // from external
    static final String USER_ID = "c42e0e76-0b8e-11e8-ab30-8b2f7fbf2ffe";
    static final String PASSWORD = "c3ca421a-0b8e-11e8-ab30-9b25d003b8dd";
    static final String PID_PREFIX = "20.500.12189";
    static final String TARGET_URL = "http://www.intersearch.com.au";
    static final String AUTHOR = "VPA";

    // global variables storing information about this export (as a whole)
    Path sourceDirectory;   // directory in which VEOs are found
    Path supportDir;        // directory in which any support files (e.g. V3 schema) is to be found
    Path outputDirectory;   // directory in which VEOS are to be generated
    int exportCount;        // number of exports processed
    boolean debug;          // true if in debug mode
    boolean verbose;        // true if in verbose output mode
    String rdfIdPrefix;     // prefix to be used to generate RDF identifiers
    String userId;          // user performing the conversion
    boolean useRealHandleService; // where to get handles from
    String setMetadata;     // A string containing metadata about the set
    boolean migration;      // true if doing migration from old DSA - back off on testing
    boolean validateOnly;   // true if only testing the VEO, not processing it
    int ignoreAbove;        // if non-zero, ignore any VEOs larger than this size in KB
    double sample;          // if non-zero, this sets a sample rate, however always sample at least one VEO in each directory
    ResultSummary results;  // if non-null this is where a summary of the results go
    boolean help;           // true if printing a cheat list of command line options

    FileWriter statfw;      // file writer for statistics
    BufferedWriter statbw;

    private final static Logger LOG = Logger.getLogger("VPA.DAIngest");

    private final static String USAGE = "VPA [-v] [-d] [-rs] -s <directory> [-o <directory>] [-sample <probability>] [-ignoreAbove <sizeKB>] [-dasmode] [-m] [-h] (files|directories)*";

    /**
     * Default constructor
     *
     * @param args arguments passed to program
     * @throws AppFatal if a fatal error occurred
     */
    public DAIngest(String args[]) throws AppFatal {

        // Set up logging
        System.setProperty("java.util.logging.SimpleFormatter.format", "%4$s: %5$s%n");
        LOG.setLevel(Level.WARNING);

        // set up default global variables
        files = new ArrayList<>();

        sourceDirectory = Paths.get(".");
        outputDirectory = Paths.get(".");
        supportDir = null;
        exportCount = 0;
        debug = false;
        verbose = false;
        rdfIdPrefix = null;
        validateOnly = true;
        migration = false;
        ignoreAbove = 0;
        sample = 1.0;
        userId = System.getProperty("user.name");
        if (userId == null) {
            userId = "Unknown user";
        }
        useRealHandleService = false;
        results = null;
        r = Runtime.getRuntime();
        help = false;

        setMetadata = " {\"ingestSetId\":\"12345\", \"agency\":\"3455\", \"series\":\"421\", \"consignment\":\"P0\", \"accessStatus\":\"open\"}";

        // process command line arguments
        configure(args);

        // report on run
        TimeZone tz;
        SimpleDateFormat sdf;
        System.out.println("******************************************************************************");
        System.out.println("*                                                                            *");
        System.out.println("*                     V E O   T E S T I N G   T O O L                        *");
        System.out.println("*                                                                            *");
        System.out.println("*                                Version " + VPA.version() + "                                *");
        System.out.println("*              Copyright 2021 Public Record Office Victoria                  *");
        System.out.println("*                                                                            *");
        System.out.println("******************************************************************************");
        System.out.println("");

        System.out.println("Test run: ");
        tz = TimeZone.getTimeZone("GMT+10:00");
        sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss+10:00");
        sdf.setTimeZone(tz);
        System.out.println(sdf.format(new java.util.Date()));
        if (help) {
            // VPA [-v] [-d] [-rs] -s <directory> [-o <directory>] [-sample <probability>] [-ignoreAbove <sizeKB>] [-dasmode] [-m] [-h] [-r <rdfPrefix>] (files|directories)*
            System.out.println("Command line arguments:");
            System.out.println(" Mandatory:");
            System.out.println("  <veos or directories>: a list of V2 or V3 VEO files or directories containing VEOs");
            System.out.println("  -s <directory>: path to a support directory (containing e.g. DTDs and XML schemas)");
            System.out.println("");
            System.out.println(" Optional:");
            System.out.println("  -o <directory>: the directory in which the results are generated (default is current working directory)");
            System.out.println("  -rs: generate a summary of the errors and warnings at the end of the run");
            System.out.println("  -dasmode: Generate the packages for the AMS, DAS, or SAMS");
            System.out.println("");
            System.out.println(" Optional and rarely needed:");
            System.out.println("  -m: migration mode. Do not perform all the validation tests on V2 VEOs");
            System.out.println("  -h: use the real handle (PID) service. Do NOT use this option unless you really need to");
            System.out.println("  -sample <probability>: sample the VEOs rather than test them all");
            System.out.println("  -ignoreAbove <sizeKB>: ignore any VEOs bigger than the specified size");
            System.out.println("  -r <rdfPrefix>: specify the RDF prefix (i.e. http://blah/");
            System.out.println("");
            System.out.println("  -v: verbose mode: give more details about processing");
            System.out.println("  -d: debug mode: may give even more details about processing");
            System.out.println("  -help: print this listing");
            System.out.println("");
        }

        // check to see if at least one file or directory is specified
        if (files.isEmpty()) {
            throw new AppFatal("You must specify at least one VEO file or directory to process");
        }
        if (supportDir == null) {
            throw new AppFatal("You must specify a support directory");
        }

        System.out.println("Configuration:");
        System.out.println(" Source directory is '" + sourceDirectory.toString() + "'");
        System.out.println(" Output directory is '" + outputDirectory.toString() + "'");
        if (migration) {
            System.out.println(" Migration mode - does not carry out some of the V2 validation");
        }
        if (validateOnly) {
            System.out.println(" Validating mode - just validates and does not generate the packages for AWS, DAS, or SAMS");
        } else {
            System.out.println(" DAS mode - emulates the DAS and generates the packages for AWS, DAS, or SAMS");
        }
        System.out.println(" Producing a summary of results?: '" + (results == null ? "no" : "yes") + "'");
        System.out.println(" User id to be logged: '" + userId + "'");
        System.out.println(" Using real handle service: '" + (useRealHandleService ? "true" : "false") + "'");
        System.out.println(" RDF Identifier prefix is '" + rdfIdPrefix + "'");
        if (sample < 1) {
            System.out.println(" Sampling VEOs at a rate of " + sample + "");
        }
        if (ignoreAbove > 0) {
            System.out.println(" Ignoring VEOs greater than " + (ignoreAbove * 1024) + " bytes (" + ignoreAbove + " KB)");
        }
        if (debug) {
            System.out.println(" Verbose/Debug mode is selected");
        } else if (verbose) {
            System.out.println(" Verbose output is selected");
        }
        System.out.println("");

        // set up processor
        vp = new VPA(outputDirectory, supportDir, rdfIdPrefix, LOG.getLevel(), useRealHandleService, PID_SERVER_URL, USER_ID, PASSWORD, PID_PREFIX, TARGET_URL, AUTHOR, migration, validateOnly, results);

        // open statistics
        try {
            statfw = new FileWriter(outputDirectory.resolve("stats.csv").toFile());
            statbw = new BufferedWriter(statfw);
            statbw.write("Record");
            statbw.write(",");
            statbw.write("Size");
            statbw.write(",");
            statbw.write("Time");
            statbw.write(",");
            statbw.write("Mem Alloc");
            statbw.write("\n");
        } catch (IOException ioe) {
            throw new AppFatal("Failure opening statistics file: " + ioe.getMessage());
        }

    }

    /**
     * Finalise...
     */
    public void close() {
        try {
            statbw.close();
            statfw.close();
        } catch (IOException ioe) {
            // ignore
        }
        vp.free();
    }

    /**
     * Configure
     *
     * This method gets the options for this run of the manifest generator from
     * the command line. See the comment at the start of this file for the
     * command line arguments.
     *
     * @param args[] the command line arguments
     * @param VEOFatal if a fatal error occurred
     */
    private void configure(String args[]) throws AppFatal {
        int i;

        // process command line arguments
        i = 0;
        try {
            while (i < args.length) {
                switch (args[i]) {

                    // verbose?
                    case "-v":
                        verbose = true;
                        LOG.setLevel(Level.INFO);
                        // rootLog.setLevel(Level.INFO);
                        i++;
                        break;

                    // debug?
                    case "-d":
                        debug = true;
                        LOG.setLevel(Level.FINE);
                        // rootLog.setLevel(Level.FINE);
                        i++;
                        break;

                    // produce a result summary?
                    case "-rs":
                        results = new ResultSummary();
                        i++;
                        break;

                    // use real handle service
                    case "-h":
                        useRealHandleService = true;
                        i++;
                        break;

                    // '-o' specifies base directory
                    case "-o":
                        i++;
                        outputDirectory = checkFile("output directory", args[i], true);
                        i++;
                        break;

                    // '-s' specifies support directory
                    case "-s":
                        i++;
                        supportDir = checkFile("support directory", args[i], true);
                        i++;
                        break;

                    // '-r' specifies the RDF prefix (i.e. http://blah//)
                    case "-r":
                        i++;
                        rdfIdPrefix = args[i];
                        i++;
                        break;

                    // '-dasmode' specifies the original mode of operation - simulating the DAS environment
                    case "-dasmode":
                        i++;
                        validateOnly = false;
                        break;

                    // '-m' specifies migrating from old DSA, so back off on some of the tests
                    case "-m":
                        i++;
                        migration = true;
                        break;

                    // '-help' print command line options
                    case "-help":
                        i++;
                        help = true;
                        break;

                    // '-sample' specifies the probability that a VEO will be
                    // tested. One VEO from each directory will be tested.
                    case "-sample":
                        i++;
                        sample = Double.parseDouble(args[i]);
                        if (sample > 1.0 || sample < 0.0) {
                            throw new AppFatal("-sample must be in the range 0.0 to 1.0 (inclusive)");
                        }
                        i++;
                        break;

                    // '-ignoreAbove' specifies the maximum size (in KB) above
                    // which the VEO will be ignored (to speed up testing)
                    case "-ignoreAbove":
                        i++;
                        ignoreAbove = Integer.parseInt(args[i]);
                        if (ignoreAbove < 0) {
                            throw new AppFatal("-ignoreAbove must be positive");
                        }
                        i++;
                        break;

                    default:
                        // if unrecognised arguement, print help string and exit
                        if (args[i].charAt(0) == '-') {
                            throw new AppFatal("Unrecognised argument '" + args[i] + "' Usage: " + USAGE);
                        }

                        // if doesn't start with '-' assume a file or directory name
                        files.add(args[i]);
                        i++;
                        break;
                }
            }
        } catch (ArrayIndexOutOfBoundsException ae) {
            throw new AppFatal("Missing argument. Usage: " + USAGE);
        } catch (NumberFormatException | NullPointerException nfe) {
            throw new AppFatal("Number argument invalid: " + nfe.getMessage() + " Usage: " + USAGE);
        }
    }

    /**
     * Check a file to see that it exists and is of the correct type (regular
     * file or directory). The program terminates if an error is encountered.
     *
     * @param type a String describing the file to be opened
     * @param name the file name to be opened
     * @param isDirectory true if the file is supposed to be a directory
     * @throws AppFatal if the file does not exist, or is of the correct type
     * @return the File opened
     */
    private Path checkFile(String type, String name, boolean isDirectory) throws AppFatal {
        Path p;

        p = Paths.get(name);

        if (!Files.exists(p)) {
            throw new AppFatal(classname, 6, type + " '" + p.toAbsolutePath().toString() + "' does not exist");
        }
        if (isDirectory && !Files.isDirectory(p)) {
            throw new AppFatal(classname, 7, type + " '" + p.toAbsolutePath().toString() + "' is a file not a directory");
        }
        if (!isDirectory && Files.isDirectory(p)) {
            throw new AppFatal(classname, 8, type + " '" + p.toAbsolutePath().toString() + "' is a directory not a file");
        }
        return p;
    }

    /**
     * Process the list of files or directories passed in on the command line
     */
    public void processVEOs() {
        int i;
        String file;

        // go through the list of files
        System.out.print("Starting");
        if (verbose) {
            System.out.println("");
        } else {
            System.out.println(" (-v may give more details)");
        }
        for (i = 0; i < files.size(); i++) {
            file = files.get(i);
            if (file == null) {
                continue;
            }
            try {
                processFile(Paths.get(file), true);
            } catch (InvalidPathException ipe) {
                LOG.log(Level.WARNING, "***Ignoring file ''{0}'' as the file name was invalid: {1}", new Object[]{file, ipe.getMessage()});
            }
        }
        System.out.println("Finished");
    }

    /**
     * Process the list of files or directories passed in on the command line
     *
     * @param count number of time to repeat
     */
    public void stressTest(int count) {
        int i, j;
        String file;

        // go through the list of files
        for (j = 0; j < count; j++) {
            for (i = 0; i < files.size(); i++) {
                file = files.get(i);
                if (file == null) {
                    continue;
                }
                processFile(Paths.get(file), true);
            }
        }
    }

    /**
     * Process an individual directory or file. If a directory, recursively
     * process all of the files (or directories) in it.
     *
     * @param f the file or directory to process
     * @param first this is the first entry in the directory
     */
    public void processFile(Path f, boolean first) {
        DirectoryStream<Path> ds;

        // check that file or directory exists
        if (!Files.exists(f)) {
            if (verbose) {
                LOG.log(Level.WARNING, "***File ''{0}'' does not exist", new Object[]{f.normalize().toString()});
            }
            return;
        }

        // if file is a directory, go through directory and test all the files
        if (Files.isDirectory(f)) {
            if (verbose) {
                LOG.log(Level.INFO, "***Processing directory ''{0}''", new Object[]{f.normalize().toString()});
            }
            try {
                boolean b = true;
                ds = Files.newDirectoryStream(f);
                for (Path p : ds) {
                    processFile(p, b);
                    b = false;
                }
                ds.close();
            } catch (IOException e) {
                LOG.log(Level.WARNING, "Failed to process directory ''{0}'': {1}", new Object[]{f.normalize().toString(), e.getMessage()});
            }
            return;
        }

        if (Files.isRegularFile(f)) {
            // possibly ignore if sampling 
            if (!first && Math.random() > sample) {
                LOG.log(Level.INFO, "***Ignoring file ''{0}'' due to sampling", new Object[]{f.normalize().toString()});
                return;
            }
            try {
                if (ignoreAbove > 0 && Files.size(f) > ignoreAbove * 1024) {
                    LOG.log(Level.INFO, "***Ignoring file ''{0}'' as it is too big", new Object[]{f.normalize().toString()});
                    return;
                }
            } catch (IOException ioe) {
                LOG.log(Level.WARNING, "***Ignoring file ''{0}'' we couldn't get the size", new Object[]{f.normalize().toString()});
            }
            if (process(f) && !validateOnly) {
                reprocess(f);
            }
        } else {
            LOG.log(Level.INFO, "***Ignoring directory ''{0}''", new Object[]{f.normalize().toString()});
        }
    }

    /**
     * Process a file containing a VEO and report on the results
     *
     * @param veo the file containing the VEO
     * @return true if success
     */
    public boolean process(Path veo) {
        VEOResult res;
        Instant start, end;
        long gap;
        long memuseStart, memuseEnd;
        int i;
        String recordName;
        Path veoDir;
        Runtime rt;
        boolean suceeded;

        // check parameters
        if (veo == null) {
            return false;
        }

        // reset, free memory, and print status
        // System.out.print(LocalDateTime.now().toString() + " Processing: '" + veo.normalize().toString() + "' ");
        // LOG.LOG(Level.INFO, "{0} Processing ''{1}''", new Object[]{((new Date()).getTime() / 1000), veo.toString()});
        // create a outputDir in the outputDir in which to put the record content
        String s = veo.getFileName().toString();
        if ((i = s.lastIndexOf('.')) != -1) {
            recordName = s.substring(0, i);
        } else {
            recordName = s;
        }
        recordName = recordName.replace('.', '-').trim();
        veoDir = outputDirectory.resolve(recordName);
        if (!deleteDirectory(veoDir)) {
            System.out.println("VEO directory '" + veoDir.normalize().toString() + "' already exists & couldn't be deleted");
            return false;
        }
        try {
            Files.createDirectory(veoDir);
        } catch (IOException ioe) {
            System.out.println("Packages.createDirs(): could not create VEO directory '" + veoDir.normalize().toString() + "': " + ioe.toString());
            return false;
        }

        // process the veo file
        try {
            if (verbose || !validateOnly) {
                System.out.println("******************************************************************************");
            }
            rt = Runtime.getRuntime();
            rt.gc();
            memuseStart = rt.totalMemory() - rt.freeMemory();
            start = Instant.now();
            res = vp.process(setMetadata, veo, veoDir, null);
            end = Instant.now();
            gap = ChronoUnit.MILLIS.between(start, end);
            rt.gc();
            memuseEnd = rt.totalMemory() - rt.freeMemory();
            // System.out.print("(" + gap + " mS) ");
            // System.out.println(memuseEnd);
            if (res != null) {
                suceeded = res.success;
                if (!validateOnly || !suceeded || verbose) {
                    // System.out.println
                    if (suceeded) {
                        System.out.print("SUCCESS");
                    } else {
                        if (!verbose && validateOnly) {
                            System.out.println("******************************************************************************");
                        }
                        System.out.print("FAILED");
                    }
                    if (!verbose) {
                        System.out.print(" processing: '" + veo.normalize().toString() + "'");
                    }
                    if (res.veoType == VEOResult.V2_VEO) {
                        System.out.print(" (V2 VEO");
                    } else if (res.veoType == VEOResult.V3_VEO) {
                        System.out.print(" (V3 VEO");
                    } else {
                        System.out.print(" (UNKNOWN VEO");
                    }
                    System.out.print(" ");
                    if (res.timeProcStart != null) {
                        System.out.print(res.timeProcStart);
                    }
                    System.out.print(" to ");
                    if (res.timeProcEnded != null) {
                        System.out.print(res.timeProcEnded);
                    }
                    System.out.println(")");
                    if (res.result != null) {
                        System.out.print(res.result);
                    } else {
                        System.out.println("Nil result");
                    }

                    try {
                        statbw.write(recordName);
                        statbw.write(",");
                        statbw.write(Long.toString(Files.size(veo) / 1024));
                        statbw.write(",");
                        statbw.write(Long.toString(gap));
                        statbw.write(",");
                        statbw.write(Long.toString(memuseEnd - memuseStart));
                        statbw.write(",");
                        statbw.write(Long.toString(memuseEnd));
                        statbw.write("\n");
                        statbw.flush();
                    } catch (IOException ioe) {
                        // ignore
                    }
                }
                // LOG.LOG(Level.INFO, "SUCCESS! VEO ''{0}''\n{1}", new Object[]{veo.toString(), res.result});
            } else {
                System.out.println("FAILED - VEO processing returned null");
                suceeded = false;
            }
            exportCount++;
            res = null;
        } catch (AppError e) {
            System.out.println("FAILED");
            System.out.println(e.getMessage());
            suceeded = false;
            // LOG.LOG(Level.WARNING, "Processing VEO ''{0}'' failed because:\n{1}", new Object[]{veo.toString(), e.getMessage()});
        } catch (AppFatal e) {
            System.out.println("UNKNOWN RESULT - VPA Failed");
            System.out.println(e.getMessage());
            suceeded = false;
            // LOG.LOG(Level.SEVERE, "System error:\n{0}", new Object[]{e.getMessage()});
        } finally {
            System.gc();

            if (validateOnly) {
                if (!deleteDirectory(veoDir)) {
                    System.out.println("VEO directory '" + veoDir.normalize().toString() + "' couldn't be deleted");
                }
            }
        }
        return suceeded;
    }

    /**
     * Process a file containing a VEO and report on the results
     *
     * @param veo the file containing the VEO
     */
    public void reprocess(Path veo) {
        VEOResult res;
        Instant start, end;
        long gap;
        long memuseStart, memuseEnd;
        int i;
        String recordName;
        Path veoDir, pidFile;
        Runtime rt;
        FileInputStream fis;
        InputStreamReader isr;
        BufferedReader br;
        StringBuilder sb;
        String line;
        String pids;

        // check parameters
        if (veo == null) {
            return;
        }

        // reset, free memory, and print status
        System.out.print("REPROCESSING: '" + veo.toString() + "' ");
        // LOG.LOG(Level.INFO, "{0} Processing ''{1}''", new Object[]{((new Date()).getTime() / 1000), veo.toString()});

        // create a outputDir in the outputDir in which to put the record content
        String s = veo.getFileName().toString();
        if ((i = s.lastIndexOf('.')) != -1) {
            recordName = s.substring(0, i);
        } else {
            recordName = s;
        }
        recordName = recordName.replace('.', '-').trim();
        veoDir = outputDirectory.resolve(recordName);

        // get the PIDs from the DAS package
        pidFile = veoDir.resolve("DAS").resolve("PIDS.json");
        try {
            sb = new StringBuilder();
            fis = new FileInputStream(pidFile.toFile());
            isr = new InputStreamReader(fis, Charset.forName("UTF-8"));
            br = new BufferedReader(isr);
            while ((line = br.readLine()) != null) {
                sb.append(line);
            }
            br.close();
            isr.close();
            fis.close();
        } catch (FileNotFoundException e) {
            System.out.println("");
            System.out.println("PIDS.json file could not be found: " + e.getMessage());
            return;
        } catch (IOException e) {
            System.out.println("");
            System.out.println("Failed reading PIDS.json file: " + e.getMessage());
            return;
        }
        pids = sb.toString();

        veoDir = outputDirectory.resolve(recordName + "-r");
        if (!deleteDirectory(veoDir)) {
            System.out.println("");
            System.out.println("VEO directory '" + veoDir.toString() + "' already exists & couldn't be deleted");
            return;
        }
        try {
            Files.createDirectory(veoDir);
        } catch (IOException ioe) {
            System.out.println("");
            System.out.println("Packages.createDirs(): could not create VEO directory '" + veoDir.toString() + "': " + ioe.toString());
            return;
        }

        // process the veo file
        try {
            rt = Runtime.getRuntime();
            rt.gc();
            memuseStart = rt.totalMemory() - rt.freeMemory();
            start = Instant.now();
            res = vp.reprocess(setMetadata, veo, veoDir, pids);
            end = Instant.now();
            gap = ChronoUnit.MILLIS.between(start, end);
            rt.gc();
            memuseEnd = rt.totalMemory() - rt.freeMemory();
            System.out.print("(" + gap + " mS) ");
            System.out.println(memuseEnd);
            if (res != null) {
                if (res.success) {
                    System.out.print("SUCCESS");
                } else {
                    System.out.print("FAILED");
                }
                if (res.veoType == VEOResult.V2_VEO) {
                    System.out.print(" (V2 VEO");
                } else if (res.veoType == VEOResult.V3_VEO) {
                    System.out.print(" (V3 VEO");
                } else {
                    System.out.print(" (UNKNOWN VEO");
                }
                System.out.print(" ");
                if (res.timeProcStart != null) {
                    System.out.print(res.timeProcStart);
                }
                System.out.print(" to ");
                if (res.timeProcEnded != null) {
                    System.out.print(res.timeProcEnded);
                }
                System.out.println(")");
                if (res.result != null) {
                    System.out.println(res.result);
                }

                try {
                    statbw.write(recordName);
                    statbw.write(",");
                    statbw.write(Long.toString(Files.size(veo) / 1024));
                    statbw.write(",");
                    statbw.write(Long.toString(gap));
                    statbw.write(",");
                    statbw.write(Long.toString(memuseEnd - memuseStart));
                    statbw.write(",");
                    statbw.write(Long.toString(memuseEnd));
                    statbw.write("\n");
                    statbw.flush();
                } catch (IOException ioe) {
                    // ignore
                }
            } else {
                System.out.println("NO result structure returned");
            }
            exportCount++;
            res = null;
        } catch (AppError e) {
            System.out.println("FAILED");
            System.out.println(e.getMessage());
            // LOG.LOG(Level.WARNING, "Processing VEO ''{0}'' failed because:\n{1}", new Object[]{veo.toString(), e.getMessage()});
        } catch (AppFatal e) {
            System.out.println("UNKNOWN RESULT - VPA Failed");
            System.out.println(e.getMessage());
            // LOG.LOG(Level.SEVERE, "System error:\n{0}", new Object[]{e.getMessage()});
        } finally {
            System.gc();
        }

        // compare the old and new SAMS directories
        veoDir = outputDirectory.resolve(recordName);
        pidFile = veoDir.resolve("DAS").resolve("PIDS.json");
        try {
            sb = new StringBuilder();
            fis = new FileInputStream(pidFile.toFile());
            isr = new InputStreamReader(fis, Charset.forName("UTF-8"));
            br = new BufferedReader(isr);
            while ((line = br.readLine()) != null) {
                sb.append(line);
            }
            br.close();
            isr.close();
            fis.close();
        } catch (FileNotFoundException e) {
            System.out.println("PIDS.json file could not be found: " + e.getMessage());
            return;
        } catch (IOException e) {
            System.out.println("Failed reading PIDS.json file: " + e.getMessage());
            return;
        }
        pids = sb.toString();
        veoDir = outputDirectory.resolve(recordName + "-r");
        pidFile = veoDir.resolve("DAS").resolve("PIDS.json");
        try {
            sb = new StringBuilder();
            fis = new FileInputStream(pidFile.toFile());
            isr = new InputStreamReader(fis, Charset.forName("UTF-8"));
            br = new BufferedReader(isr);
            while ((line = br.readLine()) != null) {
                sb.append(line);
            }
            br.close();
            isr.close();
            fis.close();
        } catch (FileNotFoundException e) {
            System.out.println("PIDS.json file could not be found: " + e.getMessage());
            return;
        } catch (IOException e) {
            System.out.println("Failed reading PIDS.json file: " + e.getMessage());
            return;
        }
        if (pids.compareTo(sb.toString()) == 0) {
            System.out.println("PIDS.json identical");
        } else {
            System.out.println("PIDS.json differ");
            System.out.println("First process:");
            System.out.println(pids);
            System.out.println("Reprocess:");
            System.out.println(sb.toString());
        }
    }

    /**
     * Recursively delete a directory
     */
    private boolean deleteDirectory(Path directory) {
        DirectoryStream<Path> ds;
        boolean failed;

        failed = false;
        try {
            if (!Files.exists(directory)) {
                return true;
            }
            ds = Files.newDirectoryStream(directory);
            for (Path p : ds) {
                if (!Files.isDirectory(p)) {
                    try {
                        Files.delete(p);
                    } catch (IOException e) {
                        failed = true;
                    }
                } else {
                    failed |= !deleteDirectory(p);
                }
            }
            ds.close();
            if (!failed) {
                Files.delete(directory);
            }
        } catch (IOException e) {
            failed = true;
        }
        return !failed;
    }

    /**
     * Print a summary of the results on the Writer out.
     *
     * @throws IOException
     */
    public void produceSummaryReport() throws IOException {
        if (results == null) {
            return;
        }
        results.report(new OutputStreamWriter(System.out));
    }

    /**
     * Main program
     *
     * @param args command line arguments
     */
    public static void main(String args[]) {
        DAIngest dai;

        try {
            dai = new DAIngest(args);
            dai.processVEOs();
            dai.produceSummaryReport();
            dai.close();
            // tp.stressTest(1000);
        } catch (AppFatal | IOException e) {
            System.out.println("Fatal error: " + e.getMessage());
            System.exit(-1);
        }
    }
}
