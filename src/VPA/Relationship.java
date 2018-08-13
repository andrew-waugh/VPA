/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package VPA;

import java.util.ArrayList;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

/**
 * This class represents a relationship between two Information Objects. Note
 * that a V2 relationship can have multiple types, all of which may have
 * multiple target Information Objects, and multiple descriptions.
 */
public class Relationship {

    ArrayList<String> types;         // type of relationship
    ArrayList<Identifier> targetIds; // identifiers of the related Information Object
    ArrayList<String> descriptions;  // description of relation

    /**
     * Default Constructor
     */
    public Relationship() {
        init();
    }

    /**
     * Construct a simple relationship with of one type, one target, and one
     * description. The targetId and description can be null
     *
     * @param type the type of relationship
     * @param targetId the related entity
     * @param description a description of the relation
     */
    public Relationship(String type, Identifier targetId, String description) {
        init();
        types.add(type);
        if (targetId != null) {
            targetIds.add(targetId);
        }
        if (description != null) {
            descriptions.add(description);
        }
    }

    private void init() {
        types = new ArrayList<>();
        targetIds = new ArrayList<>();
        descriptions = new ArrayList<>();
    }

    /**
     * Free the information associated with the Relationship
     */
    public void free() {
        int i;

        for (i = 0; i < types.size(); i++) {
            types.set(i, null);
        }
        types.clear();
        types = null;
        for (i = 0; i < targetIds.size(); i++) {
            targetIds.set(i, null);
        }
        targetIds.clear();
        targetIds = null;
        for (i = 0; i < descriptions.size(); i++) {
            descriptions.set(i, null);
        }
        descriptions.clear();
        descriptions = null;
    }

    /**
     * Represent the Relationship as JSON
     *
     * @return a string representation
     */
    @Override
    public String toString() {
        StringBuilder sb;
        int i;

        sb = new StringBuilder();
        sb.append("{\n");
        if (types.size() > 0) {
            sb.append("     \"relationshipTypes\":[\n");
            for (i = 0; i < types.size(); i++) {
                sb.append("      {\"type\":\"" + Json.safe(types.get(i)) + "\"}");
                if (i < types.size() - 1) {
                    sb.append(",\n");
                }
                sb.append("],\n");
            }
        }
        if (targetIds.size() > 0) {
            sb.append("     \"relatedRecordItems\":[\n");
            for (i = 0; i < targetIds.size(); i++) {
                sb.append("     {\"targetId\":\"" + targetIds.get(i).toString() + "\"}");
                if (i < targetIds.size() - 1) {
                    sb.append(",\n");
                }
                sb.append("],\n");
            }
        }
        if (descriptions.size() > 0) {
            sb.append("     \"descriptions\":[\n");
            for (i = 0; i < descriptions.size(); i++) {
                sb.append("     {\"description\":\"" + Json.safe(descriptions.get(i)) + "\"}");
                if (i < descriptions.size() - 1) {
                    sb.append(",\n");
                }
                sb.append("],\n");
            }
        }
        sb.append("}");
        return sb.toString();
    }

        /**
     * Represent the Relationship as JSON
     *
     * @return a JSONObject representation
     */
    public JSONObject toJSON() {
        JSONObject j1, j2;
        JSONArray ja;
        int i;

        j1 = new JSONObject();
        if (types.size() > 0) {
            ja = new JSONArray();
            for (i = 0; i < types.size(); i++) {
                j2 = new JSONObject();
                j2.put("type", types.get(i));
                ja.add(j2);
            }
            j1.put("relationshipTypes", ja);
        }
        if (targetIds.size() > 0) {
            ja = new JSONArray();
            for (i = 0; i < targetIds.size(); i++) {
                j2 = new JSONObject();
                j2.put("recordItemId", targetIds.get(i));
                ja.add(j2);
            }
            j1.put("relatedRecordItems", ja);
        }
        if (descriptions.size() > 0) {
            ja = new JSONArray();
            for (i = 0; i < descriptions.size(); i++) {
                j2 = new JSONObject();
                j2.put("description", descriptions.get(i));
                ja.add(j2);
            }
            j1.put("descriptions", ja);
        }
        j2 = new JSONObject();
        j2.put("relationship", j1);
        return j2;
    }
}
