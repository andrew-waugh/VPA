/*
 * Copyright Public Record Office Victoria 2018
 * Licensed under the CC-BY license http://creativecommons.org/licenses/by/3.0/au/
 * Author Andrew Waugh
 * Version 1.0 February 2018
 */
package VPA;

import VERSCommon.AppFatal;
import VERSCommon.VEOFatal;
import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;

/**
 * This package wraps file format handling. It's primary function is to map
 * between Mime types and file extensions. The basis of the handling is the
 * 'mime.types.txt' file.
 */
public class FileFormat {

    HashMap<String, String> toMimeType;

    public FileFormat(Path supportDir) throws AppFatal {
        toMimeType = new HashMap<>();
        readFormatFile(supportDir);
    }
    
    public void free() {
        toMimeType.clear();
        toMimeType = null;
    }

    /**
     * Read the file 'mime.types.txt' which contains a list of mime types and
     * their extensions. This file was obtained from the apache distribution,
     * and should be periodically reloaded. The file contains a sequence of
     * lines, each consisting of a mime type followed by a tab and then a set of
     * file extensions (if known). Each file extension is separated by a space.
     * Lines starting with '#' are comments and are ignored.
     *
     * @param supportDir the directory in which the file is to be found
     * @throws VEOFatal if the file could not be read
     */
    private void readFormatFile(Path supportDir) throws AppFatal {
        Path f;
        FileReader fr;
        BufferedReader br;
        String s;
        String[] tokens;
        String[] fileExt;
        String mimeType;
        int i, j;

        f = Paths.get(supportDir.toString(), "mime.types.txt");

        // open mime.types.txt for reading
        fr = null;
        br = null;
        try {
            fr = new FileReader(f.toString());
            br = new BufferedReader(fr);

            // go through mime.types.txt line by line
            // ignore lines that do begin with a '#' - these are comment lines
            while ((s = br.readLine()) != null) {
                s = s.trim();
                if (s.length() == 0 || s.charAt(0) == '#') {
                    continue;
                }
                tokens = s.split("\t");
                if (tokens[0] != null) {
                    mimeType = tokens[0].trim();
                    if (mimeType.equals("") || mimeType.equals(" ")) {
                        continue;
                    }
                } else {
                    continue;
                }
                fileExt = null;
                for (i = 1; i < tokens.length; i++) {
                    if (tokens[i] != null) {
                        fileExt = tokens[i].trim().split(" ");
                        for (j = 0; j < fileExt.length; j++) {
                            if (fileExt[j] == null || fileExt[j].equals("") || fileExt.equals(" ")) {
                                continue;
                            }
                            toMimeType.put(fileExt[j].toLowerCase(), mimeType.toLowerCase());
                        }
                    }
                }
            }
        } catch (FileNotFoundException e) {
            throw new AppFatal("FileFormat.readFormatFile(): Failed to open '" + f.toAbsolutePath().toString() + "' as " + e.getMessage());
        } catch (IOException ioe) {
            throw new AppFatal("FileFormat.readFormatFile(): Failed to open '" + f.toAbsolutePath().toString() + "' as " + ioe.getMessage());
        } finally {
            if (br != null) {
                try {
                    br.close();
                } catch (IOException e) {
                    /* ignore */ }
            }
            if (fr != null) {
                try {
                    fr.close();
                } catch (IOException e) {
                    /* ignore */ }
            }
        }
    }

    /**
     * Translate from a file extension to a Mime type. It is passed a file
     * extension (without the leading '.') and returns the Mime type. If passed
     * null or an unrecognised file extension, the Mime type "application/octet"
     * is returned.
     *
     * @param fileExt a String containing the file extension
     * @return a String containing the matching Mime type
     */
    public String fileExt2MimeType(String fileExt) {
        String s;

        if (fileExt == null) {
            return "application/octet";
        }
        s = toMimeType.get(fileExt.toLowerCase());
        if (s == null) {
            s = "application/octet";
        }
        return s;
    }
}
