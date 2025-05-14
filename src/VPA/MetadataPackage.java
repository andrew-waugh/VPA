/*
 * Copyright Public Record Office Victoria 2018
 * Licensed under the CC-BY license http://creativecommons.org/licenses/by/3.0/au/
 * Author Andrew Waugh
 * Version 1.0 February 2018
 */
package VPA;

import VERSCommon.AppError;
import org.json.simple.JSONObject;

/**
 * Thic class represents a Metadata Package in an Information Object
 *
 * @author Andrew
 */
public final class MetadataPackage {

    public int id;          // an identifier for this package
    public String schema;   // identifies the metadata schema for this package
    public String syntax;   // identifies the syntax of the package
    public String content;  // the actual metadata package

    /**
     * Default constructor
     */
    public MetadataPackage() {
        free();
    }

    /**
     * Free the information in the metadata package
     */
    final public void free() {
        id = 0;
        schema = null;
        syntax = null;
        content = null;
    }

    /**
     * Represent the Metadata Package as a string
     *
     * @return a string representing the event
     */
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("    {\n");
        sb.append("    \"id\":");
        sb.append(id);
        sb.append(",\n");
        sb.append("    \"schema\":\"");
        sb.append(Json.safe(schema));
        sb.append("\",\n");
        sb.append("    \"format\":\"");
        sb.append(Json.safe(syntax));
        sb.append("\",\n");
        sb.append("    \"content\":\"");
        sb.append(Json.safe(content));
        sb.append("\"");
        sb.append("}");
        return sb.toString();
    }

    /**
     * Represent the Metadata Package as JSON
     *
     * @return a JSONObject representing the event
     * @throws VERSCommon.AppError in case of failure
     */
    public JSONObject toJSON() throws AppError {
        JSONObject j = new JSONObject();
        j.put("schema", schema);
        j.put("format", syntax);
        j.put("content", content);
        return j;
    }

}
