/*
 * Copyright Public Record Office Victoria 2018
 * Licensed under the CC-BY license http://creativecommons.org/licenses/by/3.0/au/
 * Author Andrew Waugh
 * Version 1.0 February 2018
 */
package VPA;

import java.nio.file.Path;
import org.json.simple.JSONObject;

/**
 * This class represents a piece of content (i.e. a file) within a VEO
 */
public final class ContentFile {

    InformationPiece parent;    // containing information piece
    int seqNbr;                 // sequence number of Content File in VEO
    String sourceFileName;      // original file name from VEO (V2 only)
    String fileExt;             // file type based on data in VEO
    boolean base64;             // true if binary file is encoded in Base64 (V2 only)
    Path fileLocation;          // location of unpacked binary file relative to root of the VEO (null if refDoc is present)
    Path rootFileLocn;          // actual location of the root of content file
    String refDoc;              // ID of a referenced document in an onioned V2 VEO
    long fileSize;              // Size of file in bytes

    public ContentFile(InformationPiece parent) {
        free();
        this.parent = parent;
    }

    /**
     * Destroy the data associated with this Content File
     */
    public void free() {
        parent = null;
        seqNbr = 0;
        sourceFileName = null;
        fileExt = null;
        base64 = false;
        fileLocation = null;
        rootFileLocn = null;
        refDoc = null;
        fileSize = 0;
    }
    
    /**
     * Generate a SAMS URI for this Content File. This method returns null if
     * there is no actual content file.
     * 
     * The SAMS URI consists of a prefix (https://content.prov.vic.gov.au/rest/records/),
     * followed by the suffix of the IO PID, divided into groups separated by
     * '/', followed by the sequence number of this content file in the *VEO*
     * (note, not IO), followed by the relative path of the file location.
     * 
     * @return URL used to obtain the content file from SAMS
     */
    public String getSAMSuri() {
        StringBuilder sb = new StringBuilder();
        String s;
        String token[];
        int i;
        
        if (fileLocation == null) {
            return null;
        }
        sb.append("https://content.prov.vic.gov.au/rest/records/");
        s = parent.parent.ioPID;
        token = s.split("/");
        switch (token.length) {
            case 0:
            case 1:
                s = "";
                break;
            default:
                s = token[1];
        }
        for (i=0; i<8; i=i+2) {
            sb.append(s.substring(i,i+2));
            sb.append("/");
        }
        sb.append(s.substring(i));
        sb.append("/sequence/");
        sb.append(seqNbr);
        sb.append("/files/");
        sb.append(fileLocation.toString().replace('\\', '/'));
        return sb.toString();
    }

    /**
     * Output content file as a String
     * @return string representation
     */
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();

        sb.append("      {\n");
        sb.append("      \"cfSeqNo\":" + seqNbr + ",\n");
        sb.append("      \"sourceFileName\":\"" + Json.safe(sourceFileName) + "\",\n");
        sb.append("      \"sourceFileExtension\":\"" + Json.safe(fileExt) + "\",\n");
        if (fileLocation != null) {
            sb.append("      \"fileLocation\":\"" + Json.safe(fileLocation.toString().replace('\\', '/')) + "\",\n");
        }
        sb.append("      \"fileSizeBytes\":" + fileSize + ",\n");
        sb.append("}");
        return sb.toString();
    }

    /**
     * Represent the content file as JSON
     *
     * @return a string representing the content file
     */
    public JSONObject toJSON() {
        JSONObject j = new JSONObject();
        j.put("cfSeqNo", seqNbr);
        j.put("sourceFileName", sourceFileName);
        j.put("sourceFileExtension", fileExt);
        if (fileLocation != null) {
            j.put("fileLocation", fileLocation.toString().replace('\\', '/'));
        }
        j.put("fileSizeBytes", fileSize);
        j.put("contentFileURI", getSAMSuri());
        return j;
    }
}
