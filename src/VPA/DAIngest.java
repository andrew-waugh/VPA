/*
 * Copyright Public Record Office Victoria 2018
 * Licensed under the CC-BY license http://creativecommons.org/licenses/by/3.0/au/
 * Author Andrew Waugh
 * Version 1.0 February 2018
 */
package VPA;

/**
 *************************************************************
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
import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
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
    static final String AUTHOR  = "VPA";

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

    FileWriter statfw;      // file writer for statistics
    BufferedWriter statbw;

    private final static Logger LOG = Logger.getLogger("VPA.DAIngest");

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
        userId = System.getProperty("user.name");
        if (userId == null) {
            userId = "Unknown user";
        }
        useRealHandleService = false;
        r = Runtime.getRuntime();

        setMetadata = " \"set\": {\"ingestSetId\":\"12345\", \"agency\":\"3455\", \"series\":\"421\", \"consignment\":\"P0\", \"accessStatus\":\"open\"}";

        // process command line arguments
        configure(args);

        // set up processor
        vp = new VPA(outputDirectory, supportDir, rdfIdPrefix, LOG.getLevel(), useRealHandleService, PID_SERVER_URL, USER_ID, PASSWORD, PID_PREFIX, TARGET_URL, AUTHOR);

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
        String usage = "VPA [-v] [-d] -s <directory> [-o <directory>] [-h] (files|directories)*";

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

                    // '-o' specifies base directory
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

                    default:
                        // if unrecognised arguement, print help string and exit
                        if (args[i].charAt(0) == '-') {
                            throw new AppFatal("Unrecognised argument '" + args[i] + "' Usage: " + usage);
                        }

                        // if doesn't start with '-' assume a file or directory name
                        files.add(args[i]);
                        i++;
                        break;
                }
            }
        } catch (ArrayIndexOutOfBoundsException ae) {
            throw new AppFatal("Missing argument. Usage: " + usage);
        }

        // check to see if at least one file or directory is specified
        if (files.isEmpty()) {
            throw new AppFatal("You must specify at least one file or directory to process");
        }
        if (supportDir == null) {
            throw new AppFatal("You must specify a support directory to process");
        }

        // LOG generic things
        if (debug) {
            LOG.log(Level.INFO, "Verbose/Debug mode is selected");
        } else if (verbose) {
            LOG.log(Level.INFO, "Verbose output is selected");
        }
        LOG.log(Level.INFO, "Using real handle service: ''{0}''", new Object[]{useRealHandleService ? "true" : "false"});
        LOG.log(Level.INFO, "RDF Identifier prefix is ''{0}''", new Object[]{rdfIdPrefix});
        LOG.log(Level.INFO, "Source directory is ''{0}''", new Object[]{sourceDirectory.toString()});
        LOG.log(Level.INFO, "Output directory is ''{0}''", new Object[]{outputDirectory.toString()});
        LOG.log(Level.INFO, "User id to be logged: ''{0}''", new Object[]{userId});
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
        for (i = 0; i < files.size(); i++) {
            file = files.get(i);
            if (file == null) {
                continue;
            }
            processFile(Paths.get(file));
        }
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
                processFile(Paths.get(file));
            }
        }
    }

    /**
     * Process an individual directory or file. If a directory, recursively
     * process all of the files (or directories) in it.
     *
     * @param f the file or directory to process
     */
    public void processFile(Path f) {
        DirectoryStream<Path> ds;

        // check that file or directory exists
        if (!Files.exists(f)) {
            if (verbose) {
                LOG.log(Level.WARNING, "***File ''{0}'' does not exist", new Object[]{f.toString()});
            }
            return;
        }

        // if file is a directory, go through directory and test all the files
        if (Files.isDirectory(f)) {
            if (verbose) {
                LOG.log(Level.INFO, "***Processing directory ''{0}''", new Object[]{f.toString()});
            }
            try {
                ds = Files.newDirectoryStream(f);
                for (Path p : ds) {
                    processFile(p);
                }
                ds.close();
            } catch (IOException e) {
                LOG.log(Level.WARNING, "Failed to process directory ''{0}'': {1}", new Object[]{f.toString(), e.getMessage()});
            }
            return;
        }

        if (Files.isRegularFile(f)) {
            process(f);
        } else {
            LOG.log(Level.INFO, "***Ignoring directory ''{0}''", new Object[]{f.toString()});
        }
    }

    /**
     * Process a file containing a VEO and report on the results
     *
     * @param veo the file containing the VEO
     */
    public void process(Path veo) {
        VEOResult res;
        Instant start, end;
        long gap;
        long memuseStart, memuseEnd;
        int i;
        String recordName;
        Path veoDir;
        Runtime rt;

        // check parameters
        if (veo == null) {
            return;
        }

        // reset, free memory, and print status
        System.out.print(LocalDateTime.now().toString() + " Processing: '" + veo.toString() + "' ");
        // LOG.LOG(Level.INFO, "{0} Processing ''{1}''", new Object[]{((new Date()).getTime() / 1000), veo.toString()});

        // create a outputDir in the outputDir in which to put the record content
        String s = veo.getFileName().toString();
        if ((i = s.lastIndexOf('.')) != -1) {
            recordName = s.substring(0, i);
        } else {
            recordName = s;
        }
        recordName = recordName.replace('.', '-');
        veoDir = outputDirectory.resolve(recordName);
        if (!deleteDirectory(veoDir)) {
            System.out.println("VEO directory '" + veoDir.toString() + "' already exists & couldn't be deleted");
            return;
        }
        try {
            Files.createDirectory(veoDir);
        } catch (IOException ioe) {
            System.out.println("Packages.createDirs(): could not create VEO directory '" + veoDir.toString() + "': " + ioe.toString());
            return;
        }

        // process the veo file
        try {
            rt = Runtime.getRuntime();
            rt.gc();
            memuseStart = rt.totalMemory() - rt.freeMemory();
            start = Instant.now();
            res = vp.process(setMetadata, veo, veoDir);
            end = Instant.now();
            gap = ChronoUnit.MILLIS.between(start, end);
            rt.gc();
            memuseEnd = rt.totalMemory() - rt.freeMemory();
            System.out.print("(" + gap + " mS) ");
            System.out.println(memuseEnd);
            if (res != null) {
                System.out.println("");
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

                // LOG.LOG(Level.INFO, "SUCCESS! VEO ''{0}''\n{1}", new Object[]{veo.toString(), res.result});
            } else {
                LOG.log(Level.INFO, "SUCCESS VEO ''{0}''", new Object[]{veo.toString()});
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
     * Main program
     *
     * @param args command line arguments
     */
    public static void main(String args[]) {
        DAIngest tp;

        try {
            tp = new DAIngest(args);
            tp.processVEOs();
            tp.close();
            // tp.stressTest(1000);
        } catch (AppFatal e) {
            System.out.println("Fatal error: " + e.getMessage());
            System.exit(-1);
        }
    }
}
