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
        if (schema != null) {
            sb.append("    \"schema\":\"" + jsonSafe(schema) + "\",\n");
        }
        if (syntax != null) {
            sb.append("    \"format\":\"" + jsonSafe(syntax) + "\",\n");
        }
        if (content != null) {
            sb.append("    \"content\":\"" + jsonSafe(content) + "\"");
        }
        sb.append("}");
        return sb.toString();
    }

    private String jsonSafe(String s) {
        StringBuilder sb = new StringBuilder();
        int i;

        for (i = 0; i < s.length(); i++) {
            switch (s.charAt(i)) {
                case '"':
                    sb.append("\\u0022");
                    break;
                case '\\':
                    sb.append("\\u005c");
                    break;
                case '\n':
                    sb.append("\\u000a");
                    break;
                case '\r':
                    sb.append("\\u000d");
                    break;
                case '\t':
                    sb.append("\\u0009");
                    break;
                default:
                    sb.append(s.charAt(i));
                    break;
            }
        }
        return sb.toString();
    }
}
