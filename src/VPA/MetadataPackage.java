/*
 * Copyright Public Record Office Victoria 2018
 * Licensed under the CC-BY license http://creativecommons.org/licenses/by/3.0/au/
 * Author Andrew Waugh
 * Version 1.0 February 2018
 */
package VPA;

/**
 * Represents a Metadata Package in an IO
 *
 * @author Andrew
 */
public class MetadataPackage {

    public int id;       // an identifier for this package
    public String schema;   // identifies the metadata schema for this package
    public String syntax;   // identifies the syntax of the package
    public String content;  // the actual metadata package

    public MetadataPackage() {
        free();
    }

    final public void free() {
        id = 0;
        schema = null;
        syntax = null;
        content = null;
    }

    public String toJSON() {
        StringBuilder sb = new StringBuilder();
        sb.append("    {\n");
        sb.append("    \"id\":" + id + ",\n");
        sb.append("    \"schema\":\"" + Json.safe(schema) + "\",\n");
        sb.append("    \"format\":\"" + Json.safe(syntax) + "\",\n");
        sb.append("    \"content\":\"" + Json.safe(content) + "\"");
        sb.append("}");
        return sb.toString();
    }
}
