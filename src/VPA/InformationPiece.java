/*
 * Copyright Public Record Office Victoria 2018
 * Licensed under the CC-BY license http://creativecommons.org/licenses/by/3.0/au/
 * Author Andrew Waugh
 * Version 1.0 February 2018
 */
package VPA;

import VERSCommon.AppError;
import java.util.ArrayList;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

/**
 * This represents an Information Piece from a VEO. In the case of a V2 VEO, it
 * represents a Document from a Record VEO.
 */
public final class InformationPiece {

    InformationObject parent;   // information object that contains this piece
    String label;               // label (if any) associated with this IO
    int seqNbr;                 // sequence number of information piece within information object
    ArrayList<ContentFile> contentFiles;  // contained content files

    /**
     * Constructor
     *
     * @param parent Information Object containing this IP
     */
    public InformationPiece(InformationObject parent) {
        this.parent = parent;
        label = null;
        seqNbr = 1;
        contentFiles = new ArrayList<>();
    }

    /**
     * Free the information associated with this Information Piece
     */
    public void free() {
        int j;

        parent = null;
        label = null;
        for (j = 0; j < contentFiles.size(); j++) {
            contentFiles.get(j).free();
        }
        contentFiles.clear();
        contentFiles = null;
    }

    /**
     * Add a content file to this information piece
     *
     * @return the added content file
     */
    public ContentFile addContentFile() {
        ContentFile cf;

        cf = new ContentFile(this);
        contentFiles.add(cf);
        return cf;
    }

    /**
     * Represent the IP as a string
     *
     * @return a string representing the IP
     */
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        int i;

        sb.append("    {\n");
        sb.append("    \"ipLabel\":\"" + Json.safe(label) + "\",\n");
        sb.append("    \"ipSeqNo\":" + seqNbr + ",\n");
        if (contentFiles.size() > 0) {
            sb.append("    \"contentFiles\":[\n");
            for (i = 0; i < contentFiles.size(); i++) {
                sb.append(contentFiles.get(i).toString());
                if (i < contentFiles.size() - 1) {
                    sb.append(",\n");
                }
            }
            sb.append("]\n");
        }
        sb.append("}");
        return sb.toString();
    }

    /**
     * Represent the IP as JSON
     *
     * @return a JSONObject representing the IP
     * @throws VERSCommon.AppError if a failure occurred
     */
    public JSONObject toJSON() throws AppError {
        JSONObject j = new JSONObject();
        JSONArray ja;
        int i;

        if (label != null) {
            j.put("ipLabel", label);
        }
        j.put("ipSeqNo", seqNbr);
        if (contentFiles.size() > 0) {
            ja = new JSONArray();
            j.put("contentFiles", ja);
            for (i = 0; i < contentFiles.size(); i++) {
                ja.add(contentFiles.get(i).toJSON());
            }
        }
        return j;
    }
}
