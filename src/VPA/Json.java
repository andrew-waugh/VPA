/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package VPA;

import java.util.Stack;

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

}
