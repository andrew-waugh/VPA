/*
 * Copyright Public Record Office Victoria 2018
 * Licensed under the CC-BY license http://creativecommons.org/licenses/by/3.0/au/
 * Author Andrew Waugh
 * Version 1.0 February 2018
 */
package VPA;

import VERSCommon.AppError;
import VERSCommon.AppFatal;
import java.util.ArrayList;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

/**
 * Represents an Information Object extracted from a VEO. A V2 VEO creates one
 * Information Object (a file or record), while a V3 VEO creates an Information
 * Object from every IO in the VEO. Each information object represents a Record
 * Item in the AMS.
 */
public final class InformationObject {

    private final PIDService ps;      // service to allocate PIDs
    String veoVersion;   // version of the VEO
    String veoFileName;  // file name of the VEO
    String v2VeoType;    // if V2 VEO, is it a File or Record VEO
    String veoPID;       // PID for the VEO that contained the IO
    String v2VEOId;      // if V2 VEO, the identifier
    int seqNo;           // sequence number of this IO within the VEO
    int depth;           // depth of this Information Object in VEO
    InformationObject parent; // parent IO
    ArrayList<InformationObject> children;  // child IOs
    String ioPID;        // PID for this IO
    ArrayList<String> titles; // titles (may be the same as label)
    String label;        // label associated with the IO
    ArrayList<Identifier> ids; // list of identifiers associated with this IO
    String dateCreated;  // date item created (null if unknown)
    String dateRegistered; // date item was registered (null if unknown)
    ArrayList<Date> dates; // a list of dates in the VEOs
    ArrayList<String> descriptions; // descriptions from VEO
    ArrayList<Relationship> relations; // links to other IOs
    ArrayList<String> jurisdictionalCoverage;    // list of jurisdictional coverage metadata
    ArrayList<String> spatialCoverage; // list of spatial coverage metadata
    Disposal disposalAuthority; // disposal authorisations
    ArrayList<String> canUseFor;   // list of canUseFor elements
    ArrayList<ContextPath> contextPaths; // list of context paths
    ArrayList<InformationPiece> infoPieces; // information pieces
    ArrayList<MetadataPackage> metaPackages; // metadata packages

    /**
     * Constructor
     *
     * @param ps persistent identifier service
     * @param seqNo sequence number of this IO within the VEO
     */
    public InformationObject(PIDService ps, int seqNo) {
        free();
        this.seqNo = seqNo;
        this.ps = ps;

        children = new ArrayList<>();
        titles = new ArrayList<>();
        ids = new ArrayList<>();
        dates = new ArrayList<>();
        descriptions = new ArrayList<>();
        relations = new ArrayList<>();
        metaPackages = new ArrayList<>();
        jurisdictionalCoverage = new ArrayList<>();
        spatialCoverage = new ArrayList<>();
        infoPieces = new ArrayList<>();
        disposalAuthority = new Disposal();
        canUseFor = new ArrayList<>();
        contextPaths = new ArrayList<>();
    }

    /**
     * Clean up Information Object, ensuring that everything is garbage
     * collected. This is also used in the Constructor to ensure that a
     * constructed object is in a consistent state
     */
    final public void free() {
        int i;

        veoVersion = null;
        veoFileName = null;
        v2VeoType = null;
        veoPID = null;
        v2VEOId = null;
        seqNo = 0;
        depth = 0; // changed from 1
        parent = null;
        if (children != null) {
            for (i = 0; i < children.size(); i++) {
                children.get(i).free();
            }
            children.clear();
            children = null;
        }
        ioPID = null;
        if (titles != null) {
            titles.clear();
            titles = null;
        }
        label = null;
        if (ids != null) {
            for (i = 0; i < ids.size(); i++) {
                ids.get(i).free();
            }
            ids.clear();
            ids = null;
        }
        dateCreated = null;
        dateRegistered = null;
        if (dates != null) {
            for (i = 0; i < dates.size(); i++) {
                dates.get(i).free();
            }
            dates.clear();
            dates = null;
        }
        if (descriptions != null) {
            for (i = 0; i < descriptions.size(); i++) {
                descriptions.set(i, null);
            }
            descriptions.clear();
            descriptions = null;
        }
        if (relations != null) {
            for (i = 0; i < relations.size(); i++) {
                relations.set(i, null);
            }
            relations.clear();
            relations = null;
        }
        if (metaPackages != null) {
            for (i = 0; i < metaPackages.size(); i++) {
                metaPackages.get(i).free();
            }
            metaPackages.clear();
            metaPackages = null;
        }
        if (jurisdictionalCoverage != null) {
            for (i = 0; i < jurisdictionalCoverage.size(); i++) {
                jurisdictionalCoverage.set(i, null);
            }
            jurisdictionalCoverage.clear();
            jurisdictionalCoverage = null;
        }
        if (spatialCoverage != null) {
            for (i = 0; i < spatialCoverage.size(); i++) {
                spatialCoverage.set(i, null);
            }
            spatialCoverage.clear();
            spatialCoverage = null;
        }
        if (disposalAuthority != null) {
            disposalAuthority.free();
        }
        if (infoPieces != null) {
            for (i = 0; i < infoPieces.size(); i++) {
                infoPieces.get(i).free();
            }
            infoPieces.clear();
            infoPieces = null;
        }
    }

    /**
     * Add a new identifier to the IO
     *
     * @param value of the identifier
     * @param scheme identifier is allocated in
     */
    public void addIdentifier(String value, String scheme) {
        Identifier id;

        id = new Identifier(value, scheme);
        ids.add(id);
    }

    /**
     * Add a new identifier to the IO
     *
     * @param id the identifier
     */
    public void addIdentifier(Identifier id) {
        ids.add(id);
    }

    /**
     * Add a new Information Piece to the IO and return it
     *
     * @return the new Information Piece
     */
    public InformationPiece addInformationPiece() {
        InformationPiece ip;

        ip = new InformationPiece(this);
        infoPieces.add(ip);
        return ip;
    }

    /**
     * Add a new Metadata Package to the IO and return it
     *
     * @return the new metadata package
     */
    public MetadataPackage addMetadataPackage() {
        MetadataPackage mp;

        mp = new MetadataPackage();
        metaPackages.add(mp);
        return mp;
    }

    /**
     * Assign a PID to this IO
     *
     * @param veoPID the veoPID for this IO
     * @throws AppFatal if minting the PID failed
     */
    public void assignPIDs(String veoPID) throws AppFatal {
        this.veoPID = veoPID;
        this.ioPID = ps.mint();
    }

    /**
     * Assign a PID to this IO
     *
     * @param veoPID the veoPID for this IO
     * @param ioPID to ioPID for this IO
     */
    public void assignPIDs(String veoPID, String ioPID) {
        this.veoPID = veoPID;
        this.ioPID = ioPID;
    }

    /**
     * Represent the IO as a string
     *
     * @return a string representing the IO
     */
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        int i;

        sb.append("{\n");
        sb.append("  \"veoFileName\":\"");
        sb.append(Json.safe(veoFileName));
        sb.append("\",\n");
        sb.append("  \"veoPID\": {\"scheme\": \"handle\", \"value\": \"");
        sb.append(Json.safe(veoPID));
        sb.append("\"},\n");
        sb.append("  \"veoVersion\":\"");
        sb.append(Json.safe(veoVersion));
        sb.append("\",\n");
        if (v2VeoType != null) {
            sb.append("  \"v2VeoType\":\"");
            sb.append(Json.safe(v2VeoType));
            sb.append("\",\n");
        }
        if (v2VEOId != null) {
            sb.append("  \"v2VEOId\":");
            sb.append(Json.safe(v2VEOId));
            sb.append(",\n");
        }
        if (parent != null) {
            sb.append("  \"parent\":\"");
            sb.append(Json.safe(parent.ioPID));
            sb.append("\",\n");
        }
        sb.append("  \"ioPID\": {\"scheme\": \"handle\", \"value\": \"");
        sb.append(Json.safe(ioPID));
        sb.append("\"},\n");
        sb.append("  \"ioSeqNo\":");
        sb.append(seqNo);
        sb.append(",\n");
        sb.append("  \"ioDepth\":");
        sb.append(depth);
        sb.append(",\n");
        if (!children.isEmpty()) {
            sb.append("  \"children\":[\n");
            for (i = 0; i < children.size(); i++) {
                sb.append("    {\"child\": {\"scheme\": \"handle\", \"value\": \"");
                sb.append(Json.safe(children.get(i).ioPID));
                sb.append("\"} }");
                if (i < children.size() - 1) {
                    sb.append(",\n");
                }
            }
            sb.append("],\n");
        }
        if (!titles.isEmpty()) {
            sb.append("  \"titles\":[\n");
            for (i = 0; i < titles.size(); i++) {
                sb.append("    ");
                sb.append(Json.safe(titles.get(i)));
                if (i < titles.size() - 1) {
                    sb.append(",\n");
                }
            }
            sb.append("],\n");
        }
        if (label != null) {
            sb.append("  \"label\":\"");
            sb.append(Json.safe(label));
            sb.append("\",\n");
        }
        if (!ids.isEmpty()) {
            sb.append("  \"identifiers\":[\n");
            for (i = 0; i < ids.size(); i++) {
                String s = ids.get(i).itemId;
                if (!s.startsWith("{")) {
                    s = "\"" + s + "\"";
                }
                sb.append("    {\"identifier\":{\"value\":");
                sb.append(Json.safe(s));
                sb.append(", \"scheme\":\"");
                sb.append(ids.get(i).idScheme);
                sb.append("\"}}");
                if (i < ids.size() - 1) {
                    sb.append(",\n");
                }
            }
            sb.append("],\n");
        }
        if (dateCreated != null) {
            sb.append("  \"dateCreated\":\"");
            sb.append(Json.safe(dateCreated));
            sb.append("\",\n");
        }
        if (dateRegistered != null) {
            sb.append("  \"dateRegistered\":\"");
            sb.append(Json.safe(dateRegistered));
            sb.append("\",\n");
        }
        if (!dates.isEmpty()) {
            sb.append("  \"dates\":[\n");
            for (i = 0; i < dates.size(); i++) {
                sb.append("    ");
                sb.append(dates.get(i).toString());
                if (i < dates.size() - 1) {
                    sb.append(",\n");
                }
            }
            sb.append("],\n");
        }
        if (!descriptions.isEmpty()) {
            sb.append("  \"descriptions\":[\n");
            for (i = 0; i < descriptions.size(); i++) {
                sb.append("    {\"description\":\"");
                sb.append(Json.safe(descriptions.get(i)));
                sb.append("\"}");
                if (i < descriptions.size() - 1) {
                    sb.append(",\n");
                }
            }
            sb.append("],\n");
        }
        if (!contextPaths.isEmpty()) {
            sb.append("  \"contextPaths\":[\n");
            for (i = 0; i < contextPaths.size(); i++) {
                sb.append("    {\"contextPath\":");
                sb.append(Json.safe(contextPaths.get(i).toString()));
                if (i < contextPaths.size() - 1) {
                    sb.append(",\n");
                }
            }
            sb.append("],\n");
        }
        if (!canUseFor.isEmpty()) {
            sb.append("  \"canUseForList\":[\n");
            for (i = 0; i < canUseFor.size(); i++) {
                sb.append("    {\"canUseFor\":");
                sb.append(Json.safe(canUseFor.get(i)));
                if (i < canUseFor.size() - 1) {
                    sb.append(",\n");
                }
            }
            sb.append("],\n");
        }
        if (!relations.isEmpty()) {
            sb.append("  \"relationships\":[\n");
            for (i = 0; i < relations.size(); i++) {
                sb.append("    {\"relation\":");
                sb.append(relations.get(i).toString());
                if (i < relations.size() - 1) {
                    sb.append(",\n");
                }
            }
            sb.append("],\n");
        }
        if (!jurisdictionalCoverage.isEmpty()) {
            sb.append("  \"jurisdictionalCoverage\":[\n");
            for (i = 0; i < jurisdictionalCoverage.size(); i++) {
                sb.append("    {\"jurisdiction\":\"");
                sb.append(Json.safe(jurisdictionalCoverage.get(i)));
                sb.append("\"}");
                if (i < jurisdictionalCoverage.size() - 1) {
                    sb.append(",\n");
                }
            }
            sb.append("],\n");
        }
        if (!spatialCoverage.isEmpty()) {
            sb.append("  \"spatialCoverage\":[\n");
            for (i = 0; i < spatialCoverage.size(); i++) {
                sb.append("    {\"place\":\"");
                sb.append(Json.safe(spatialCoverage.get(i)));
                sb.append("\"}");
                if (i < spatialCoverage.size() - 1) {
                    sb.append(",\n");
                }
            }
            sb.append("],\n");
        }
        sb.append(disposalAuthority.toString());
        if (!metaPackages.isEmpty()) {
            sb.append("  \"metadataPackages\":[\n");
            for (i = 0; i < metaPackages.size(); i++) {
                sb.append(metaPackages.get(i).toString());
                if (i < metaPackages.size() - 1) {
                    sb.append(",\n");
                }
            }
            sb.append("],\n");
        }
        if (!infoPieces.isEmpty()) {
            sb.append("  \"infoPieces\":[\n");
            for (i = 0; i < infoPieces.size(); i++) {
                sb.append(infoPieces.get(i).toString());
                if (i < infoPieces.size() - 1) {
                    sb.append(",\n");
                }
            }
            sb.append("]\n");
        }
        // StringBuilder vers2SignedObject;
        // StringBuilder aglsMetadata;
        // String xmlContent;

        sb.append("}");
        return sb.toString();
    }

    /**
     * Represent the IO as JSON
     *
     * @return a JSONObject representing the IO
     * @throws VERSCommon.AppError in case of failure
     */
    public JSONObject toJSON() throws AppError {
        JSONObject j1, j2, j3;
        JSONArray ja;
        int i;

        j1 = new JSONObject();
        j1.put("veoFileName", veoFileName);
        j2 = new JSONObject();
        j1.put("veoPID", j2);
        j2.put("scheme", "handle");
        j2.put("value", veoPID);
        j1.put("veoVersion", veoVersion);
        if (v2VeoType != null) {
            j1.put("v2VeoType", v2VeoType);
        }
        if (v2VEOId != null) {
            j1.put("v2VeoId", v2VEOId);
        }
        if (parent != null) {
            j1.put("parent", parent.ioPID);
        }
        j2 = new JSONObject();
        j1.put("ioPID", j2);
        j2.put("scheme", "handle");
        j2.put("value", ioPID);
        j1.put("ioSeqNo", seqNo);
        j1.put("ioDepth", depth);
        if (!children.isEmpty()) {
            ja = new JSONArray();
            j1.put("children", ja);
            for (i = 0; i < children.size(); i++) {
                j2 = new JSONObject();
                j3 = new JSONObject();
                j3.put("scheme", "handle");
                j3.put("value", children.get(i).ioPID);
                j2.put("child", j3);
                ja.add(j2);
            }
        }
        if (!titles.isEmpty()) {
            ja = new JSONArray();
            j1.put("titles", ja);
            for (i = 0; i < titles.size(); i++) {
                j2 = new JSONObject();
                j2.put("title", titles.get(i));
                ja.add(j2);
            }
        } else {
            // this code has been temporarily added to allow end to end testing
            // of the AMS. It ensures that the IO *always* contains a titles/title
            // property, even if the IO in the VEO doesn't.
            ja = new JSONArray();
            j1.put("titles", ja);
            j2 = new JSONObject();
            if (label != null) {
                j2.put("title", label);
            } else {
                j2.put("title", "[Not Set]");
            }
            ja.add(j2);
        }
        if (label != null) {
            j1.put("label", label);
        }
        if (!ids.isEmpty()) {
            ja = new JSONArray();
            j1.put("identifiers", ja);
            for (i = 0; i < ids.size(); i++) {
                ja.add(ids.get(i).toJSON());
            }
        }
        /* Removed at request of DAS team - DAS will use the dates directly
        if (dateCreated != null) {
            j1.put("dateCreated", dateCreated);
        }
        if (dateRegistered != null) {
            j1.put("dateRegistered", dateRegistered);
        }
         */
        if (!dates.isEmpty()) {
            ja = new JSONArray();
            j1.put("dates", ja);
            for (i = 0; i < dates.size(); i++) {
                ja.add(dates.get(i).toJSON());
            }
        }
        if (!descriptions.isEmpty()) {
            ja = new JSONArray();
            j1.put("descriptions", ja);
            for (i = 0; i < descriptions.size(); i++) {
                j2 = new JSONObject();
                j2.put("description", descriptions.get(i));
                ja.add(j2);
            }
        }
        if (!contextPaths.isEmpty()) {
            ja = new JSONArray();
            j1.put("contextPaths", ja);
            for (i = 0; i < contextPaths.size(); i++) {
                ja.add(contextPaths.get(i).toJSON());
            }
        }
        if (!canUseFor.isEmpty()) {
            ja = new JSONArray();
            j1.put("canUseForList", ja);
            for (i = 0; i < canUseFor.size(); i++) {
                j2 = new JSONObject();
                j2.put("canUseFor", canUseFor.get(i));
                ja.add(j2);
            }
        }
        if (!relations.isEmpty()) {
            ja = new JSONArray();
            j1.put("relationships", ja);
            for (i = 0; i < relations.size(); i++) {
                ja.add(relations.get(i).toJSON());
            }
        }
        if (!jurisdictionalCoverage.isEmpty()) {
            ja = new JSONArray();
            j1.put("jurisdictionalCoverage", ja);
            for (i = 0; i < jurisdictionalCoverage.size(); i++) {
                j2 = new JSONObject();
                j2.put("jurisdiction", jurisdictionalCoverage.get(i));
                ja.add(j2);
            }
        }
        if (!spatialCoverage.isEmpty()) {
            ja = new JSONArray();
            j1.put("spatialCoverage", ja);
            for (i = 0; i < spatialCoverage.size(); i++) {
                j2 = new JSONObject();
                j2.put("place", spatialCoverage.get(i));
                ja.add(j2);
            }
        }
        j1.put("disposalAuthority", disposalAuthority.toJSON());
        if (!metaPackages.isEmpty()) {
            ja = new JSONArray();
            j1.put("metadataPackages", ja);
            for (i = 0; i < metaPackages.size(); i++) {
                ja.add(metaPackages.get(i).toJSON());
            }
        }
        if (!infoPieces.isEmpty()) {
            ja = new JSONArray();
            j1.put("infoPieces", ja);
            for (i = 0; i < infoPieces.size(); i++) {
                ja.add(infoPieces.get(i).toJSON());
            }
        }
        return j1;
    }

    /**
     * Add a new context path found in one of the metadata packages to the IO.
     * The domain may be null.
     *
     * @param domain
     * @param value
     */
    public void addContextPath(String domain, String value) {
        contextPaths.add(new ContextPath(domain, value));
    }

    /**
     * Private class representing the contents of a context path
     */
    private class ContextPath {

        String domain;
        String value;

        public ContextPath(String domain, String value) {
            this.domain = domain;
            this.value = value;
        }

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            sb.append("   \"contextPathDomain\":");
            if (domain != null) {
                sb.append("\"");
                sb.append(Json.safe(domain));
                sb.append("\"");
            } else {
                sb.append("\"");
                sb.append("<null>");
                sb.append("\"");
            }
            sb.append("\n");
            sb.append("   \"contextPathValue\":");
            if (value != null) {
                sb.append("\"");
                sb.append(Json.safe(value));
                sb.append("\"");
            } else {
                sb.append("<null>");
            }
            sb.append("\n");
            return sb.toString();
        }

        public JSONObject toJSON() throws AppError {
            JSONObject j1, j2;
            
            j1 = new JSONObject();
            j2 = new JSONObject();
            if (domain != null) {
                j2.put("domain", domain);
            } else {
                j2.put("domain", "[No domain]");
            }
            if (value != null) {
                j2.put("value", value);
            } else {
                j2.put("value", "[No value]");
            }
            j1.put("contextPath", j2);
            return j1;
        }
    }
}
