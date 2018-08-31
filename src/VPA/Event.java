/*
 * Copyright Public Record Office Victoria 2018
 * Licensed under the CC-BY license http://creativecommons.org/licenses/by/3.0/au/
 * Author Andrew Waugh
 * Version 1.0 February 2018
 */
package VPA;

import VERSCommon.AppError;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

/**
 * This class represents an Event in the history of a VEO. An event is built
 * from information in the VEO.
 */
public final class Event {

    String timestamp;   // time/date that the event occurred
    String eventType;   // type of event
    ArrayList<String> initiators;   // who is responsible for initiating the event
    ArrayList<String> descriptions; // a description of the event
    ArrayList<String> errors;       // an error that occurred
    static DateTimeFormatter formatter;

    public Event() {
        free();
        initiators = new ArrayList<>();
        descriptions = new ArrayList<>();
        errors = new ArrayList<>();
        formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssXXX");
    }

    public void free() {
        timestamp = null;
        eventType = null;
        if (initiators != null) {
            initiators.clear();
            initiators = null;
        }
        if (descriptions != null) {
            descriptions.clear();
            descriptions = null;
        }
        if (errors != null) {
            errors.clear();
            errors = null;
        }
    }

    /**
     * Represent the Event as JSON
     *
     * @return a string representing the event
     */
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        int i;

        sb.append("   {\n");
        sb.append("    \"eventDateTime\":\"" + Json.safe(timestamp) + "\",\n");
        sb.append("    \"eventType\":\"" + Json.safe(eventType) + "\",\n");
        sb.append("    \"eventInitiators\":[\n");
        for (i = 0; i < initiators.size(); i++) {
            sb.append("    \"initiator\":\"" + Json.safe(initiators.get(i)) + "\",\n");
        }
        sb.append("    ]\n");
        sb.append("    \"eventDescriptions\":[\n");
        for (i = 0; i < descriptions.size(); i++) {
            sb.append("    \"description\":\"" + Json.safe(descriptions.get(i)) + "\"],\n");
        }
        sb.append("    ]\n");
        sb.append("    \"eventErrors\":[\n");
        for (i = 0; i < errors.size(); i++) {
            sb.append("    \"error\":\"" + Json.safe(errors.get(i)) + "\"],\n");
        }
        sb.append("    ]\n");
        return sb.toString();
    }

    /**
     * Represent the Event as JSON
     *
     * @return a JSONObject representing the event
     * @throws VERSCommon.AppError in case of failure
     */
    public JSONObject toJSON() throws AppError {
        JSONObject j1, j2;
        JSONArray ja;
        int i;

        j1 = new JSONObject();
        j1.put("eventDateTime", timestamp);
        j1.put("eventType", eventType);
        if (initiators.size() > 0) {
            ja = new JSONArray();
            for (i = 0; i < initiators.size(); i++) {
                j2 = new JSONObject();
                j2.put("initiator", initiators.get(i));
                ja.add(j2);
            }
            j1.put("eventInitiators", ja);
        }
        if (descriptions.size() > 0) {
            ja = new JSONArray();
            for (i = 0; i < descriptions.size(); i++) {
                j2 = new JSONObject();
                j2.put("description", descriptions.get(i));
                ja.add(j2);
            }
            j1.put("eventDescriptions", ja);
        }
        if (errors.size() > 0) {
            ja = new JSONArray();
            for (i = 0; i < errors.size(); i++) {
                j2 = new JSONObject();
                j2.put("error", errors.get(i));
                ja.add(j2);
            }
            j1.put("eventErrors", ja);
        }
        return j1;
    }

    /**
     * Construct an event representing the Ingest into the Digital Archive
     *
     * @return the event
     */
    static public Event Ingest() {
        Event e;

        e = new Event();
        e.timestamp = OffsetDateTime.now().format(formatter);
        e.eventType = "Ingest";
        e.initiators.add("PROV archival system");
        e.descriptions.add("Processed by Digital Archive Ingest Function/VPA");
        return e;
    }

    /**
     * Construct an event representing the acceptance of custody by PROV
     *
     * @return the event
     */
    static public Event CustodyAccepted() {
        Event e;

        e = new Event();
        e.timestamp = OffsetDateTime.now().format(formatter);
        e.eventType = "Custody Accepted";
        e.initiators.add("PROV archival system");
        e.descriptions.add("Processed by Digital Archive Ingest Function/VPA");
        return e;
    }

    /**
     * Construct an event representing the migration of a VEO from the old DA to
     * the new one
     *
     * @return the event
     */
    static public Event Migrated() {
        Event e;

        e = new Event();
        e.timestamp = OffsetDateTime.now().format(formatter);
        e.eventType = "Migrated";
        e.initiators.add("PROV archival system");
        e.descriptions.add("Migrated from old PROV Digital Archive to new Digital Archive");
        return e;
    }
}
