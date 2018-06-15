/*
 * Copyright Public Record Office Victoria 2018
 * Licensed under the CC-BY license http://creativecommons.org/licenses/by/3.0/au/
 * Author Andrew Waugh
 * Version 1.0 February 2018
 */
package VPA;

import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;

/**
 * This class represents an Event in the history of a VEO. An event is built
 * from information in the VEO.
 */
public class Event {

    String timestamp;   // time/date that the event occurred
    String eventType;   // type of event
    String initiator;   // who is responsible for initiating the event
    String description; // a description of the event
    static DateTimeFormatter formatter;

    public Event() {
        timestamp = null;
        eventType = null;
        initiator = null;
        description = null;
        formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssXXX");
    }

    public void free() {
        timestamp = null;
        eventType = null;
        initiator = null;
        description = null;
    }

    public String toJSON() {
        StringBuilder sb = new StringBuilder();

        sb.append("   {\n");
        sb.append("    \"eventDateTime\":\"" + Json.safe(timestamp) + "\",\n");
        sb.append("    \"eventType\":\"" + Json.safe(eventType) + "\",\n");
        sb.append("    \"eventDescription\":\"" + Json.safe(description) + "\"}");
        return sb.toString();
    }

    static public Event Ingest() {
        Event e;

        e = new Event();
        e.timestamp = OffsetDateTime.now().format(formatter);
        e.eventType = "Ingest";
        e.initiator = "PROV archival system";
        e.description = "Processed by Digital Archive Ingest Function/VPA";
        return e;
    }

    static public Event CustodyAccepted() {
        Event e;

        e = new Event();
        e.timestamp = OffsetDateTime.now().format(formatter);
        e.eventType = "Custody Accepted";
        e.initiator = "PROV archival system";
        e.description = "Processed by Digital Archive Ingest Function/VPA";
        return e;
    }

    static public Event Migrated() {
        Event e;

        e = new Event();
        e.timestamp = OffsetDateTime.now().format(formatter);
        e.eventType = "Migrated";
        e.initiator = "PROV archival system";
        e.description = "Migrated from old PROV Digital Archive to new Digital Archive";
        return e;
    }
}
