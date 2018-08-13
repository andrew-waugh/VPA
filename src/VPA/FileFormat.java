/*
 * Copyright Public Record Office Victoria 2018
 * Licensed under the CC-BY license http://creativecommons.org/licenses/by/3.0/au/
 * Author Andrew Waugh
 * Version 1.0 February 2018
 */
package VPA;

import VERSCommon.AppFatal;
import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Path;
import java.util.HashMap;

/**
 * This class maps between file extensions and MIME types. The mappings are
 * obtained from the 'mime.types.txt' file from apache. This file is located in
 * the VPA support directory. Note that in mapping from a MIME type to a file
 * extension, the first file extension given is used.
 */
public final class FileFormat {

    HashMap<String, String> toMimeType;
    HashMap<String, String> fromMimeType;

    public FileFormat(Path supportDir) throws AppFatal {
        toMimeType = new HashMap<>();
        fromMimeType = new HashMap<>();
        readFormatFile(supportDir);
    }

    /**
     * Free the data associated with this instance
     */
    public void free() {
        toMimeType.clear();
        toMimeType = null;
        fromMimeType.clear();
        fromMimeType = null;
    }

    /**
     * Read the file 'mime.types.txt' which contains a list of mime types and
     * their extensions. This file was obtained from the apache distribution,
     * and should be periodically reloaded. The file contains a sequence of
     * lines, each consisting of a mime type followed by a tab and then a set of
     * file extensions (if known). Each file extension is separated by a space.
     * File extensions do not have a leading '.'. Lines starting with '#' are
     * comments and are ignored.
     *
     * @param supportDir the directory in which the file is to be found
     * @throws AppFatal if the file could not be read
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

        f = supportDir.resolve("mime.types.txt");

        // open mime.types.txt for reading
        fr = null;
        br = null;
        try {
            fr = new FileReader(f.toString());
            br = new BufferedReader(fr);

            // go through mime.types.txt line by line
            while ((s = br.readLine()) != null) {
                s = s.trim();

                // ignore lines that do begin with a '#' - these are comment lines - and empty lines
                if (s.length() == 0 || s.charAt(0) == '#') {
                    continue;
                }

                // split line at tabs
                tokens = s.split("\t");

                // ignore lines with a null or empty MIME type
                if (tokens[0] != null) {
                    mimeType = tokens[0].trim();
                    if (mimeType.equals("") || mimeType.equals(" ")) {
                        continue;
                    }
                } else {
                    continue;
                }

                // get list of file extensions associated with MIME type
                for (i = 1; i < tokens.length; i++) {
                    if (tokens[i] != null) {
                        fileExt = tokens[i].trim().split(" ");
                        if (fileExt.length > 0) {
                            fromMimeType.put(mimeType.toLowerCase(), fileExt[0].toLowerCase());
                        }
                        for (j = 0; j < fileExt.length; j++) {
                            if (fileExt[j] == null || fileExt[j].equals("") || fileExt.equals(" ")) {
                                continue;
                            }
                            toMimeType.put(fileExt[j].toLowerCase(), mimeType.toLowerCase());
                        }
                        for (j = 0; j < fileExt.length; j++) {
                            fileExt[j] = null;
                        }
                    }
                }
                for (i = 0; i < tokens.length; i++) {
                    tokens[i] = null;
                }
            }
        } catch (FileNotFoundException e) {
            throw new AppFatal("Failed to open '" + f.toAbsolutePath().toString() + "' because " + e.getMessage() + "(VPA.FileFormat.readFormatFile())");
        } catch (IOException ioe) {
            throw new AppFatal("Failed to open '" + f.toAbsolutePath().toString() + "' because " + ioe.getMessage() + "(VPA.FileFormat.readFormatFile())");
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

    /**
     * Translate from a Mime type to a file extension. It is passed a MIME type
     * and returns a file extension (without the leading '.'). If passed null or
     * an unrecognised MIME type, it returns null.
     *
     * @param mimeType a String containing the MIME type
     * @return a String containing the matching file extension
     */
    public String mimeType2FileExt(String mimeType) {
        if (mimeType == null) {
            return null;
        }
        return fromMimeType.get(mimeType.toLowerCase());
    }
}
