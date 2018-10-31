/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package VPA;

import VERSCommon.AppError;
import java.util.ArrayList;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

/**
 * This class represents a Disposal Authorisation
 */
public class Disposal {

    ArrayList<String> rdas; // list of RDAs applicable
    String disposalClass;   // disposal class (can be null)

    public Disposal() {
        free();
        rdas = new ArrayList<>();
    }

    /**
     * Free the storage assigned to this disposal authorisation
     */
    public final void free() {
        int i;

        if (rdas != null) {
            for (i = 0; i < rdas.size(); i++) {
                rdas.set(i, null);
            }
            rdas.clear();
            rdas = null;
        }
        disposalClass = null;
    }

    /**
     * Turn a disposal authorisation into a string
     *
     * @return a string representation of the disposal authorisation
     */
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        int i;

        sb.append("\"disposalAuthority\":{");
        if (rdas != null) {
            sb.append("\"rdas\":[");
            for (i = 0; i < rdas.size(); i++) {
                sb.append("{\"rda\":\"" + rdas.get(i) + "\n}");
                if (i == rdas.size()) {
                    sb.append(",\n}");
                }
            }
            sb.append("],\n");
        }
        sb.append("\"disposalClass\":\"" + disposalClass + "\"}");
        return sb.toString();
    }

    /**
     * Turn a disposal authorisation into a JSON object
     *
     * @return a JSONObject representation of the disposal authorisation
     * @throws VERSCommon.AppError in case of failure
     */
    public JSONObject toJSON() throws AppError {
        JSONObject j1, j2;
        JSONArray ja;
        int i;

        j1 = new JSONObject();
        if (rdas != null) {
            ja = new JSONArray();
            for (i = 0; i < rdas.size(); i++) {
                j2 = new JSONObject();
                j2.put("rda", rdas.get(i));
                ja.add(j2);
            }
            j1.put("rdas", ja);
        }
        if (disposalClass != null) {
            j1.put("disposalClass", disposalClass);
        }
        return j1;
    }
}
