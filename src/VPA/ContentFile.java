/*
 * Copyright Public Record Office Victoria 2018
 * Licensed under the CC-BY license http://creativecommons.org/licenses/by/3.0/au/
 * Author Andrew Waugh
 * Version 1.0 February 2018
 */
package VPA;

import java.nio.file.Path;

/**
 * This class represents a piece of content (i.e. a file) within a VEO
 */
public class ContentFile {

    InformationPiece parent;    // containing information piece
    int seqNbr;                 // sequence number of Content File in VEO
    String sourceFileName;      // original file name from VEO (V2 only)
    String fileExt;             // file type based on data in VEO (V2 only)
    boolean base64;             // true if binary file is encoded in Base64 (V2 only)
    Path fileLocation;          // location of unpacked binary file (null if refDoc is present)
    String refDoc;              // ID of a referenced document in an onioned V2 VEO
    long fileSize;              // Size of file in bytes
    String uri;                 // URI for SAMS (null if refDoc is present)

    public ContentFile(InformationPiece parent) {
        free();
        this.parent = parent;
    }

    final public void free() {
        parent = null;
        seqNbr = 0;
        sourceFileName = null;
        fileExt = null;
        base64 = false;
        fileLocation = null;
        refDoc = null;
        fileSize = 0;
        uri = null;
    }

    /**
     * Represent the content file as JSON
     *
     * @return a string representing the content file
     */
    public String toJSON() {
        StringBuilder sb = new StringBuilder();

        sb.append("      {\n");
        sb.append("      \"cfSeqNo\":" + seqNbr + ",\n");
        sb.append("      \"sourceFileName\":\"" + Json.safe(sourceFileName) + "\",\n");
        sb.append("      \"sourceFileExtension\":\"" + Json.safe(fileExt) + "\",\n");
        if (fileLocation != null) {
            sb.append("      \"fileLocation\":\"" + Json.safe(fileLocation.toString()) + "\",\n");
        }
        sb.append("      \"fileSizeBytes\":" + fileSize + ",\n");
        sb.append("      \"samsURI\":\"" + Json.safe(uri) + "\"");
        sb.append("}");
        return sb.toString();
    }
}
