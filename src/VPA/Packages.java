/*
 * Copyright Public Record Office Victoria 2018
 * Licensed under the CC-BY license http://creativecommons.org/licenses/by/3.0/au/
 * Author Andrew Waugh
 * Version 1.0 February 2018
 */
package VPA;

import VERSCommon.AppError;
import java.io.BufferedOutputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

/**
 * This class generates the DAS, AMS, & SAMS packages from the information
 * extracted from the VEO.
 */
public final class Packages {

    private final FileFormat ff;    // handles the mappings between file extensions and MIME types
    private Path amsDir, samsDir, dasDir, veoDir; // subdirectories for the various packages

    /**
     * Default constructor
     *
     * @param ff package to handle mapping between file extensions and MIME type
     */
    public Packages(FileFormat ff) {
        this.ff = ff;
    }

    /**
     * Create the directory structure to contain the various packages
     *
     * @param directory the directory in which to create the packages
     * @throws AppError if an error occurs that shouldn't happen
     */
    public void createDirs(Path directory) throws AppError {

        // create directory for the AMS package
        amsDir = directory.resolve("AMS");
        try {
            Files.createDirectory(amsDir);
        } catch (IOException ioe) {
            throw new AppError("Packages.createDirs(): could not create AMS package directory'" + amsDir.toString() + "': " + ioe.toString());
        }

        // create directory for the SAMS package
        samsDir = directory.resolve("SAMS");
        try {
            Files.createDirectory(samsDir);
        } catch (IOException ioe) {
            throw new AppError("Packages.createDirs(): could not create SAMS package directory'" + samsDir.toString() + "': " + ioe.toString());
        }

        // create directory for the DA package
        dasDir = directory.resolve("DAS");
        try {
            Files.createDirectory(dasDir);
        } catch (IOException ioe) {
            throw new AppError("Packages.createDirs(): could not create DAS package directory'" + dasDir.toString() + "': " + ioe.toString());
        }

        veoDir = directory;
    }

    /**
     * Create an AMS Package. This contains IO.xml files created from the VEO
     *
     * @param setMetadata JSON metadata about set as a whole
     * @param ios list of information object files
     * @param events list of events (to be added to first IO)
     * @throws AppError
     */
    public void createAMSPackage(JSONObject setMetadata, ArrayList<InformationObject> ios, ArrayList<Event> events) throws AppError {
        FileOutputStream fos;
        BufferedOutputStream bos;
        OutputStreamWriter osw;
        InformationObject io;
        Path p;
        int i, j;
        JSONObject j1;
        JSONArray ja1;
        StringBuilder sb;
        String s;

        // make IO files
        for (i = 0; i < ios.size(); i++) {
            io = ios.get(i);

            // build JSON
            j1 = new JSONObject();
            j1.put("set", setMetadata);
            j1.put("io", io.toJSON());
            if (events.size() > 0) {
                ja1 = new JSONArray();
                for (j = 0; j < events.size(); j++) {
                    ja1.add(events.get(j).toJSON());
                }
                j1.put("events", ja1);
            }
            s = Json.prettyPrintJSON(j1.toJSONString());

            // write to file
            p = amsDir.resolve("IO-" + i + ".json");
            try {
                fos = new FileOutputStream(p.toFile());
            } catch (FileNotFoundException fnfe) {
                throw new AppError("Packages.createAMSPackage(): Couldn't create JSON IO file '" + p.toString() + "' because: " + fnfe.getMessage());
            }
            bos = new BufferedOutputStream(fos);
            osw = new OutputStreamWriter(bos);
            try {
                osw.write(s);
            } catch (IOException ioe) {
                throw new AppError("Packages.createAMSPackage(): Couldn't write JSON IO file '" + p.toString() + "' because: " + ioe.getMessage());
            } finally {
                try {
                    osw.close();
                } catch (IOException ioe) {
                    /* ignore */
                }
                try {
                    bos.close();
                } catch (IOException ioe) {
                    /* ignore */
                }
                try {
                    fos.close();
                } catch (IOException ioe) {
                    /* ignore */
                }
            }
        }
    }

    /**
     * Create SAMS Package. A SAMS package consists of the binary files from the
     * VEO and a CSV file containing the mapping between the IO-PIDs/CFUs and
     * the binary files.
     *
     * @param ios the list of Information Objects from the VEO
     * @throws AppError
     */
    public void createSAMSPackage(ArrayList<InformationObject> ios) throws AppError {
        FileOutputStream fos;
        BufferedOutputStream bos;
        OutputStreamWriter osw;
        InformationObject io;
        Path p;
        int i, j, k;

        // output the PID table
        p = samsDir.resolve("SAMS.csv");
        try {
            fos = new FileOutputStream(p.toFile());
        } catch (FileNotFoundException fnfe) {
            throw new AppError("Packages.createSAMSPackage(): Couldn't create '" + p.toString() + "' as: " + fnfe.getMessage());
        }
        bos = new BufferedOutputStream(fos);
        osw = new OutputStreamWriter(bos);
        try {
            osw.write("VEO PID,Seq Nbr,RI PID,SAMS URI,File name rel to SAMS dir,MIME type\r\n");
            
            // go through each Information Object...
            for (i = 0; i < ios.size(); i++) {
                io = ios.get(i);

                // go through each Information Piece in the IO...
                for (j = 0; j < io.infoPieces.size(); j++) {
                    InformationPiece ip = io.infoPieces.get(j);
                    if (ip.contentFiles != null) {

                        // go through each Content File in the IP...
                        for (k = 0; k < ip.contentFiles.size(); k++) {
                            ContentFile cf = ip.contentFiles.get(k);

                            // move the file specified by the content file to the SAMS directory
                            p = moveFile(cf.rootFileLocn, cf.fileLocation);
                            osw.write(io.veoPID + "," + cf.seqNbr + ","+ io.ioPID + "," + cf.getSAMSuri() + ",\"" + p.toString().replace('\\', '/') + "\",\"" + ff.fileExt2MimeType(cf.fileExt) + "\"\r\n");
                        }
                    }
                }
            }
        } catch (IOException ioe) {
            throw new AppError("Packages.createSAMSPackage(): Failed writing '" + p.toString() + "' as: " + ioe.getMessage());
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
     * Move a file from its current location to the SAMS package. The file
     * root/file is the source location; and the file is the destination
     * location relative to the SAMS directory.
     * Note that multiple VEO content files can refer to the same
     * physical file in a content file. In this case, we move the file once to
     * the SAMS directory and just return the path of the SAMS file in
     * subsequent calls for the same file.
     *
     * @param source the Path of the file to be moved
     * @return the Path of the relocated file relative to the SAMS directory
     */
    private Path moveFile(Path root, Path file) throws AppError {
        int i;
        Path source, p1, p2;

        // sanity check
        if (root == null || file == null || file.getNameCount() < 1) {
            return null;
        }

        // go through path of the source and see that a similar path is in the SAMS directory
        source = root.resolve(file);
        p1 = file;
        p2 = samsDir;
        try {
            for (i = 0; i < p1.getNameCount(); i++) {
                p2 = p2.resolve(p1.getName(i));
                if (i != p1.getNameCount() - 1) { // directory
                    if (!Files.exists(p2)) {
                        Files.createDirectory(p2);
                    }
                } else if (Files.exists(source)) { // already moved if multiple VEO content files refer to same file
                    Files.move(source, p2);
                } else if (!Files.exists(p2)) {
                    throw new AppError("Packages.moveFile(): source file '" + source.toString() + "' has been moved, but doesn't appear in SAMS package '" + p2.toString() + "'");
                }
            }
        } catch (IOException ioe) {
            throw new AppError("Packages.moveFile(): Couldn't create/move '" + p2.toString() + "' as: " + ioe.getMessage());
        }
        return samsDir.relativize(p2);
    }
    
    /**
     * Construct a CFU to identify a binary file in the SAMS
     *
     * @param ioPID the Information Object PID
     * @param IPseq the sequence number of the Information Piece in the IO
     * @param CFseq the sequence number of the Content File in the IP
     * @return a string representing the CFU
     */
    public String createCFU(String ioPID, int IPseq, int CFseq) {
        StringBuilder sb = new StringBuilder();
        int k;

        sb.append("https://content.prov.vic.gov.au/rest/records/");
        for (k = 0; k < ioPID.length(); k = k + 4) {
            if (k + 4 > ioPID.length()) {
                sb.append(ioPID.substring(k));
                sb.append("/");
            } else {
                sb.append(ioPID.substring(k, k + 4));
                sb.append("/");
            }
        }
        sb.append("ip/");
        sb.append(IPseq);
        sb.append("/cf/");
        sb.append(CFseq);
        return (sb.toString());
    }

    /**
     * Create the DAS package. This contains the original VEO, and the VEO-PID
     *
     * @param veo the original VEO
     * @param veoPID the persistent identifier for the VEO
     * @throws AppError
     */
    public void createDASPackage(Path veo, String veoPID) throws AppError {
        FileOutputStream fos;
        BufferedOutputStream bos;
        OutputStreamWriter osw;
        Path p;

        // copy original VEO to DA package
        try {
            Files.copy(veo, dasDir.resolve(veo.getFileName()));
        } catch (IOException ioe) {
            throw new AppError("Packages.createDASPackage(): Failed copying original VEO to DAS package: " + ioe.getMessage());
        }

        // output the PID table
        p = dasDir.resolve("PID.cvs");
        try {
            fos = new FileOutputStream(p.toFile());
        } catch (FileNotFoundException fnfe) {
            throw new AppError("Packages.createDASPackage(): Couldn't create PID file as: " + fnfe.getMessage());
        }
        bos = new BufferedOutputStream(fos);
        osw = new OutputStreamWriter(bos);
        try {
            osw.write(veoPID + "\t" + veo.getFileName().toString() + "\n");
        } catch (IOException ioe) {
            throw new AppError("Packages.createDASPackage(): Failed writing to the PID file as: " + ioe.getMessage());
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
}
