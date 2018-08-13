/*
 * Copyright Public Record Office Victoria 2018
 * Licensed under the CC-BY license http://creativecommons.org/licenses/by/3.0/au/
 * Author Andrew Waugh
 * Version 1.0 February 2018
 */
package VPA;

import VERSCommon.AppFatal;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.ProtocolException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

/**
 * This class encapsulates a persistent identifier service. The mint call mints
 * a new persistent identifier and returns it.
 */
public class PIDService {

    String serverURL;       // URL to connect to PID server
    String credentials;     // password name and password to log into server
    URL pidService;         // connection to the PID service
    String pidPrefix;       // prefix for PID
    String targetURL;       // URL of resource to be identified
    String author;          // the creator of the resource
    Base64.Encoder b64e;    // Base64 encoder
    boolean useRealHandleService;   // if true use real handle service, otherwise fake it
    int count;              // unique value to fake handle service

    /**
     * Open a connection to the PID service. This connection is used by
     * subsequent mint requests. Should disconnect the pidService before freeing
     * the PIDService
     *
     * @param useRealHandleService if true use real handle service
     * @param serverURL the URL of the server
     * @param userId the user Id
     * @param password the password of the user
     * @param targetURL the target URL
     * @param author the author of the resource
     * @throws AppFatal if the underlying connection could not be opened
     */
    public PIDService(boolean useRealHandleService, String serverURL, String userId, String password, String pidPrefix, String targetURL, String author) throws AppFatal {

        this.useRealHandleService = useRealHandleService;
        this.serverURL = serverURL;
        count = 0;

        // open the underlying connection
        try {
            pidService = new URL(serverURL);
        } catch (MalformedURLException mue) {
            throw new AppFatal("PID Service URL is malformed: " + mue.getMessage());
        }
        b64e = Base64.getEncoder();
        credentials = userId + ":" + password;
        this.pidPrefix = pidPrefix;
        this.targetURL = targetURL;
        this.author = author;
    }

    /**
     * Free the resources associated with this PID Service
     *
     * @throws AppFatal
     */
    public void free() throws AppFatal {
        b64e = null;
        serverURL = null;
        credentials = null;
        pidPrefix = null;
        targetURL = null;
        author = null;
    }

    /**
     * Use the PID service to mint a new persistent identifier
     *
     * @return a string containing the persistent identifier
     * @throws AppFatal if something fails
     */
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
            count++;
            return "123/" + count;
        }

        // build POST data in JSON and convert it to a UTF-8 encoded array of bytes
        j1 = new JSONObject();
        j1.put("prefix", pidPrefix);
        j1.put("method", "guid");
        ja1 = new JSONArray();
        j2 = new JSONObject();
        j2.put("index", "1");
        j2.put("type", "URL");
        j2.put("value", targetURL);
        ja1.add(j2);
        j2 = new JSONObject();
        j2.put("index", "2");
        j2.put("type", "AUTHOR");
        j2.put("value", author);
        ja1.add(j2);
        j1.put("values", ja1);
        param = j1.toJSONString();
        b = param.getBytes(StandardCharsets.UTF_8);
        System.out.println(Json.prettyPrintJSON(param));

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

        s = b64e.encodeToString(credentials.getBytes(StandardCharsets.UTF_8));
        op.setRequestProperty("Authorization", "Basic " + s);

        op.setRequestProperty("Content-Length", Integer.toString(b.length));
        op.setUseCaches(false);
        op.setDoInput(true);
        op.setDoOutput(true);

        // we have finish configuring the HTTP operation, actually make the connection
        try {
            op.connect();
        } catch (IOException ioe) {
            throw new AppFatal("Failed to connect to PID Service: " + ioe.getMessage());
        }

        // write the POST data to server
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
            throw new AppFatal("Did not find suffix in response from PID Service: " + Json.prettyPrintJSON(j1.toString()));
        }
        if (suffix == null) {
            throw new AppFatal("Did not find suffix in response from PID Service: " + Json.prettyPrintJSON(j1.toString()));
        }

        // disconnect
        op.disconnect();

        // return the persistent identifier
        return prefix + "/" + suffix;
    }
}
