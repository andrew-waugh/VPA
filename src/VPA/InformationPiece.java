/*
 * Copyright Public Record Office Victoria 2018
 * Licensed under the CC-BY license http://creativecommons.org/licenses/by/3.0/au/
 * Author Andrew Waugh
 * Version 1.0 February 2018
 */
package VPA;

import java.util.ArrayList;

/**
 * This represents an Information Piece from a VEO. In the case of a V2 VEO, it
 * represents a File VEO or Record VEO.
 */
public class InformationPiece {

    InformationObject parent;   // information object that contains this piece
    String label;               // label (if any) associated with this IO
    int seqNbr;                     // sequence number of information piece within information object
    ArrayList<ContentFile> contentFiles;  // contained content files

    public InformationPiece(InformationObject parent) {
        this.parent = parent;
        label = null;
        seqNbr = 1;
        contentFiles = new ArrayList<>();
    }

    public ContentFile addContentFile() {
        ContentFile cf;

        cf = new ContentFile(this);
        contentFiles.add(cf);
        return cf;
    }

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
     * Represent the IP as JSON
     *
     * @return a string representing the IP
     */
    public String toJSON() {
        StringBuilder sb = new StringBuilder();
        int i;

        sb.append("    {\n");
        sb.append("    \"ipLabel\":\"" + Json.safe(label) + "\",\n");
        sb.append("    \"ipSeqNo\":" + seqNbr + ",\n");
        if (contentFiles.size() > 0) {
            sb.append("    \"contentFiles\":[\n");
            for (i = 0; i < contentFiles.size(); i++) {
                sb.append(contentFiles.get(i).toJSON());
                if (i < contentFiles.size() - 1) {
                    sb.append(",\n");
                }
            }
            sb.append("]\n");
        }
        sb.append("}");
        return sb.toString();
    }
}
