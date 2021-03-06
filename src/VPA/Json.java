/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package VPA;

/**
 *
 * @author Andrew
 */
public class Json {

    public Json() {
    }

    /**
     * Ensure that a string is a safe JSON string. A null string is turned into
     * an empty string.
     *
     * @param s the string to check
     * @return a safe string
     */
    public static String safe(String s) {
        StringBuilder sb;
        int i;

        if (s == null) {
            return "";
        }
        sb = new StringBuilder();
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

    /**
     * Format a JSON string for ease of human reading
     *
     * @param in ugly JSON
     * @return pretty printed JSON
     */
    public static String prettyPrintJSON(String in) {
        StringBuffer sb;
        int i, j, indent;
        char ch;
        boolean element;
        boolean string;
        boolean escape;

        sb = new StringBuffer();
        indent = 0;
        element = true;
        string = false;
        escape = false;
        for (i = 0; i < in.length(); i++) {
            ch = in.charAt(i);
            if (string) {
                switch (ch) {
                    case '"':
                        sb.append("\"");
                        if (!escape) {
                            string = false;
                        }
                        escape = false;
                        break;
                    case '\\':
                        sb.append("\\");
                        escape = true;
                        break;
                    default:
                        sb.append(ch);
                        escape = false;
                        break;
                }
            } else {
                switch (ch) {
                    case '"':
                        sb.append("\"");
                        string = true;
                        break;
                    case ':':
                        sb.append(":");
                        element = true;
                        break;
                    case '{':
                        indent++;
                        sb.append("{");
                        if (element) {
                            sb.append("\n");
                            for (j = 0; j < indent; j++) {
                                sb.append(" ");
                            }
                            element = false;
                        }
                        break;
                    case '}':
                        indent--;
                        sb.append("}");
                        element = false;
                        break;
                    case '[':
                        indent++;
                        sb.append("[\n");
                        for (j = 0; j < indent; j++) {
                            sb.append(" ");
                        }
                        element = false;
                        break;
                    case ']':
                        indent--;
                        sb.append("]");
                        element = false;
                        break;
                    case ',':
                        sb.append(",\n");
                        for (j = 0; j < indent; j++) {
                            sb.append(" ");
                        }
                        element = false;
                        break;
                    default:
                        sb.append(ch);
                        break;
                }
            }
        }
        return sb.toString();
    }
}
