/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package VPA;

import org.json.simple.JSONObject;

/**
 * This class wraps an identifier for an Information Object
 */
public class Identifier {

    String agencyId;
    String seriesId;
    String fileId;
    String itemId;
    public String idScheme; // the scheme that allocated the identifier

    public Identifier(String itemId, String idScheme) {
        free();
        this.itemId = itemId;
        if (idScheme == null) {
            this.idScheme = "Unknown";
        } else {
            this.idScheme = idScheme;
        }
    }

    public Identifier() {
        free();
        this.idScheme = "Unknown";
    }

    /**
     * Free the storage assigned to this identifier
     */
    public final void free() {
        agencyId = null;
        seriesId = null;
        fileId = null;
        itemId = null;
        idScheme = null;
    }

    /**
     * Turn an identifier (agencyId, seriesId, fileId, and (optional) recordID
     * into a string.
     *
     * @return a string representation of the JSON
     */
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        boolean output;

        sb.append("{");
        sb.append("\"value\":{");
        output = false;
        if (agencyId != null) {
            sb.append("\"agencyId\":\"" + agencyId + "\"");
            output = true;
        }
        if (seriesId != null) {
            if (output) {
                sb.append(", ");
            }
            sb.append("\"seriesId\":\"" + seriesId + "\"");
            output = true;
        }
        if (fileId != null) {
            if (output) {
                sb.append(", ");
            }
            sb.append("\"fileId\":\"" + fileId + "\"");
            output = true;
        }
        if (itemId != null) {
            if (output) {
                sb.append(", ");
            }
            sb.append("\"recordId\":\"" + itemId + "\"");
        }
        sb.append("}, ");
        sb.append("\"scheme\":\"" + Json.safe(idScheme) + "\"");
        sb.append("}");
        return sb.toString();
    }

    /**
     * Turn an identifier (agencyId, seriesId, fileId, and (optional) recordID
     * into a JSON representation.
     *
     * @return a JSONObject representation of the identifier
     */
    public JSONObject toJSON() {
        JSONObject j1, j2, j3;

        j3 = new JSONObject();
        if (agencyId != null) {
            j3.put("agencyId", agencyId);
        }
        if (seriesId != null) {
            j3.put("seriesId", seriesId);
        }
        if (fileId != null) {
            j3.put("fileId", fileId);
        }
        if (itemId != null) {
            j3.put("itemId", itemId);
        }
        j2 = new JSONObject();
        j2.put("value", j3);
        j2.put("scheme", idScheme);
        j1 = new JSONObject();
        j1.put("identifier", j2);
        return j1;
    }
}
