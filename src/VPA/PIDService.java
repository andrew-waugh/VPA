/*
 * Copyright Public Record Office Victoria 2018
 * Licensed under the CC-BY license http://creativecommons.org/licenses/by/3.0/au/
 * Author Andrew Waugh
 * Version 1.0 February 2018
 */
package VPA;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.ProtocolException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Base64;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

/**
 * Encapsulates a PIDService
 *
 * @author Andrew
 */
public class PIDService {

    URL pidService;        // connection to the PID service
    Base64.Encoder b64e;    // Base64 encoder
    boolean useRealHandleService;   // if true use real handle service, otherwise fake it
    static final String PID_SERVER_URL = "http://192.168.240.135:80/handle-service/mintingHandle";
    static final String USER_ID = "c42e0e76-0b8e-11e8-ab30-8b2f7fbf2ffe";
    static final String PASSWORD = "c3ca421a-0b8e-11e8-ab30-9b25d003b8dd";
    static final String CREDENTIALS = USER_ID + ":" + PASSWORD;
    static final String PID_PREFIX = "20.500.12189";
    static final String DEFAULT_TARGET_URL = "http://www.prosentient.com.au";
    static final String DEFAULT_AUTHOR = "VPA";

    /**
     * Open a connection to the PID service. This connection is used by
     * subsequent mint requests. Should disconnect the pidService before freeing
     * the PIDService
     *
     * @param useRealHandleService if true use real handle service
     * @throws AppFatal if the underlying connection could not be opened
     */
    public PIDService(boolean useRealHandleService) throws AppFatal {

        this.useRealHandleService = useRealHandleService;
        
        // open the underlying connection
        try {
            pidService = new URL(PID_SERVER_URL);
        } catch (MalformedURLException mue) {
            throw new AppFatal("PID Service URL is malformed: " + mue.getMessage());
        }
        b64e = Base64.getEncoder();
    }

    public void close() throws AppFatal {
        b64e = null;
    }

    public String mint() throws AppFatal {
        HttpURLConnection op;
        int res;
        String param, prefix, suffix, s;
        InputStreamReader isr;
        BufferedReader br;
        JSONParser parser = new JSONParser();
        JSONObject j1, j2;
        JSONArray ja1;
        byte b[];
        
        // for testing, so we don't hit the production server unless necessary
        if (!useRealHandleService) {
            return "123/"+LocalDateTime.now().toString();
        }

        // build POST data in JSON
        j1 = new JSONObject();
        j1.put("prefix", PID_PREFIX);
        j1.put("method", "guid");
        ja1 = new JSONArray();
        j2 = new JSONObject();
        j2.put("index", "1");
        j2.put("type", "URL");
        j2.put("value", DEFAULT_TARGET_URL);
        ja1.add(j2);
        j2 = new JSONObject();
        j2.put("index", "2");
        j2.put("type", "AUTHOR");
        j2.put("value", DEFAULT_AUTHOR);
        ja1.add(j2);
        j1.put("values", ja1);
        param = j1.toJSONString();
        b = param.getBytes(StandardCharsets.UTF_8);
        // System.out.println(prettyPrintJSON(param));

        // start a new HTTP operation (yes, the name is confusing)
        try {
            op = (HttpURLConnection) pidService.openConnection();
        } catch (IOException ioe) {
            throw new AppFatal("Could create the new POST request to the PID server: " + ioe.getMessage());
        }
        try {
            op.setRequestMethod("POST");
        } catch (ProtocolException pe) {
            throw new AppFatal("POST is not a valid HTTP request (should never occur): " + pe.getMessage());
        }
        op.setRequestProperty("Accept", "application/json");
        op.setRequestProperty("Content-Type", "application/json; charset=UTF-8");

        s = b64e.encodeToString(CREDENTIALS.getBytes(StandardCharsets.UTF_8));
        op.setRequestProperty("Authorization", "Basic " + s);

        op.setRequestProperty("Content-Length", Integer.toString(b.length));
        op.setUseCaches(false);
        op.setDoInput(true);
        op.setDoOutput(true);

        // we have finish configuring the HTTP operation
        try {
            op.connect();
        } catch (IOException ioe) {
            throw new AppFatal("Failed to connect to PID Service: " + ioe.getMessage());
        }

        // write POST data to server
        try {
            op.getOutputStream().write(b);
            op.getOutputStream().close();
        } catch (IOException ioe) {
            throw new AppFatal("Error when posting to PID Service:" + ioe.getMessage());
        }

        // get response to POST. SHould be a '201' (Created)
        try {
            res = op.getResponseCode();
        } catch (IOException ioe) {
            throw new AppFatal("Error when getting response: " + ioe.getMessage());
        }
        if (res != 201) {
            throw new AppFatal("PID Service did not respond with Created (201). Responded with: " + res);
        }

        // read the JSON response (should it contain a Location hearder giving
        // the URI?)
        try {
            isr = new InputStreamReader(op.getInputStream(), "UTF-8");
            br = new BufferedReader(isr);
            j1 = (JSONObject) parser.parse(br);
        } catch (ParseException pe) {
            throw new AppFatal("Failed parsing mint Post response: " + pe.toString());
        } catch (IOException ioe) {
            throw new AppFatal("Error reading response from PID Service: " + ioe.getMessage());
        }
        prefix = (String) j1.get("prefix");
        suffix = (String) j1.get("suffix");

        try {
            br.close();
            isr.close();
        } catch (IOException ioe) {
            /* ignore */ }

        if (prefix == null) {
            throw new AppFatal("Did not find suffix in response from PID Service: " + prettyPrintJSON(j1.toString()));
        }
        if (suffix == null) {
            throw new AppFatal("Did not find suffix in response from PID Service: " + prettyPrintJSON(j1.toString()));
        }

        // disconnect
        op.disconnect();

        return prefix + "/" + suffix;
    }

    /**
     * Format the JSON string for ease of human reading
     *
     * @param in ugly JSON
     * @return pretty printed JSON
     */
    private String prettyPrintJSON(String in) {
        StringBuffer sb;
        int i, j, indent;
        char ch;

        sb = new StringBuffer();
        indent = 0;
        for (i = 0; i < in.length(); i++) {
            ch = in.charAt(i);
            switch (ch) {
                case '{':
                    indent++;
                    sb.append("{");
                    break;
                case '}':
                    indent--;
                    sb.append("}");
                    break;
                case '[':
                    indent++;
                    sb.append("[\n");
                    for (j = 0; j < indent; j++) {
                        sb.append(" ");
                    }
                    break;
                case ']':
                    indent--;
                    sb.append("]");
                    break;
                case ',':
                    sb.append(",\n");
                    for (j = 0; j < indent; j++) {
                        sb.append(" ");
                    }
                    break;
                default:
                    sb.append(ch);
                    break;
            }
        }
        return sb.toString();
    }
}