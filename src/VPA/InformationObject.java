/*
 * Copyright Public Record Office Victoria 2018
 * Licensed under the CC-BY license http://creativecommons.org/licenses/by/3.0/au/
 * Author Andrew Waugh
 * Version 1.0 February 2018
 */
package VPA;

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
        depth = 1;
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
     * Represent the IO as a string
     *
     * @return a string representing the IO
     */
    public String toString() {
        StringBuilder sb = new StringBuilder();
        int i;

        sb.append("{\n");
        sb.append("  \"veoFileName\":\"" + Json.safe(veoFileName) + "\",\n");
        sb.append("  \"veoPID\": {\"scheme\": \"handle\", \"value\": \"" + Json.safe(veoPID) + "\"},\n");
        sb.append("  \"veoVersion\":\"" + Json.safe(veoVersion) + "\",\n");
        if (v2VeoType != null) {
            sb.append("  \"v2VeoType\":\"" + Json.safe(v2VeoType) + "\",\n");
        }
        if (v2VEOId != null) {
            sb.append("  \"v2VEOId\":" + v2VEOId + ",\n");
        }
        if (parent != null) {
            sb.append("  \"parent\":\"" + Json.safe(parent.ioPID) + "\",\n");
        }
        sb.append("  \"ioPID\": {\"scheme\": \"handle\", \"value\": \"" + Json.safe(ioPID) + "\"},\n");
        sb.append("  \"ioSeqNo\":" + seqNo + ",\n");
        sb.append("  \"ioDepth\":" + depth + ",\n");
        if (children.size() > 0) {
            sb.append("  \"children\":[\n");
            for (i = 0; i < children.size(); i++) {
                sb.append("    {\"child\": {\"scheme\": \"handle\", \"value\": \"" + Json.safe(children.get(i).ioPID) + "\"} }");
                if (i < children.size() - 1) {
                    sb.append(",\n");
                }
            }
            sb.append("],\n");
        }
        if (titles.size() > 0) {
            sb.append("  \"titles\":[\n");
            for (i = 0; i < titles.size(); i++) {
                sb.append("    " + Json.safe(titles.get(i)));
                if (i < titles.size() - 1) {
                    sb.append(",\n");
                }
            }
            sb.append("],\n");
        }
        if (label != null) {
            sb.append("  \"label\":\"" + Json.safe(label) + "\",\n");
        }
        if (ids.size() > 0) {
            sb.append("  \"identifiers\":[\n");
            for (i = 0; i < ids.size(); i++) {
                String s = ids.get(i).itemId;
                if (!s.startsWith("{")) {
                    s = "\"" + s + "\"";
                }
                sb.append("    {\"identifier\":{\"value\":" + s + ", \"scheme\":\"" + ids.get(i).idScheme + "\"}}");
                if (i < ids.size() - 1) {
                    sb.append(",\n");
                }
            }
            sb.append("],\n");
        }
        if (dateCreated != null) {
            sb.append("  \"dateCreated\":\"" + Json.safe(dateCreated) + "\",\n");
        }
        if (dateRegistered != null) {
            sb.append("  \"dateRegistered\":\"" + Json.safe(dateRegistered) + "\",\n");
        }
        if (dates.size() > 0) {
            sb.append("  \"dates\":[\n");
            for (i = 0; i < dates.size(); i++) {
                sb.append("    " + dates.get(i).toString());
                if (i < dates.size() - 1) {
                    sb.append(",\n");
                }
            }
            sb.append("],\n");
        }
        if (descriptions.size() > 0) {
            sb.append("  \"descriptions\":[\n");
            for (i = 0; i < descriptions.size(); i++) {
                sb.append("    {\"description\":\"" + Json.safe(descriptions.get(i)) + "\"}");
                if (i < descriptions.size() - 1) {
                    sb.append(",\n");
                }
            }
            sb.append("],\n");
        }
        if (relations.size() > 0) {
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
        if (jurisdictionalCoverage.size() > 0) {
            sb.append("  \"jurisdictionalCoverage\":[\n");
            for (i = 0; i < jurisdictionalCoverage.size(); i++) {
                sb.append("    {\"jurisdiction\":\"" + Json.safe(jurisdictionalCoverage.get(i)) + "\"}");
                if (i < jurisdictionalCoverage.size() - 1) {
                    sb.append(",\n");
                }
            }
            sb.append("],\n");
        }
        if (spatialCoverage.size() > 0) {
            sb.append("  \"spatialCoverage\":[\n");
            for (i = 0; i < spatialCoverage.size(); i++) {
                sb.append("    {\"place\":\"" + Json.safe(spatialCoverage.get(i)) + "\"}");
                if (i < spatialCoverage.size() - 1) {
                    sb.append(",\n");
                }
            }
            sb.append("],\n");
        }
        sb.append(disposalAuthority.toString());
        if (metaPackages.size() > 0) {
            sb.append("  \"metadataPackages\":[\n");
            for (i = 0; i < metaPackages.size(); i++) {
                sb.append(metaPackages.get(i).toString());
                if (i < metaPackages.size() - 1) {
                    sb.append(",\n");
                }
            }
            sb.append("],\n");
        }
        if (infoPieces.size() > 0) {
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
     */
    public JSONObject toJSON() {
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
        if (children.size() > 0) {
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
        if (titles.size() > 0) {
            ja = new JSONArray();
            j1.put("titles", ja);
            for (i = 0; i < titles.size(); i++) {
                j2 = new JSONObject();
                j2.put("title", titles.get(i));
                ja.add(j2);
            }
        }
        if (label != null) {
            j1.put("label", label);
        }
        if (ids.size() > 0) {
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
        if (dates.size() > 0) {
            ja = new JSONArray();
            j1.put("dates", ja);
            for (i = 0; i < dates.size(); i++) {
                ja.add(dates.get(i).toJSON());
            }
        }
        if (descriptions.size() > 0) {
            ja = new JSONArray();
            j1.put("descriptions", ja);
            for (i = 0; i < descriptions.size(); i++) {
                j2 = new JSONObject();
                j2.put("description", descriptions.get(i));
                ja.add(j2);
            }
        }
        if (relations.size() > 0) {
            ja = new JSONArray();
            j1.put("relationships", ja);
            for (i = 0; i < relations.size(); i++) {
                ja.add(relations.get(i).toJSON());
            }
        }
        if (jurisdictionalCoverage.size() > 0) {
            ja = new JSONArray();
            j1.put("jurisdictionalCoverage", ja);
            for (i = 0; i < jurisdictionalCoverage.size(); i++) {
                j2 = new JSONObject();
                j2.put("jurisdiction", jurisdictionalCoverage.get(i));
                ja.add(j2);
            }
        }
        if (spatialCoverage.size() > 0) {
            ja = new JSONArray();
            j1.put("spatialCoverage", ja);
            for (i = 0; i < spatialCoverage.size(); i++) {
                j2 = new JSONObject();
                j2.put("place", spatialCoverage.get(i));
                ja.add(j2);
            }
        }
        j1.put("disposalAuthority", disposalAuthority.toJSON());
        if (metaPackages.size() > 0) {
            ja = new JSONArray();
            j1.put("metadataPackages", ja);
            for (i = 0; i < metaPackages.size(); i++) {
                ja.add(metaPackages.get(i).toJSON());
            }
        }
        if (infoPieces.size() > 0) {
            ja = new JSONArray();
            j1.put("infoPieces", ja);
            for (i = 0; i < infoPieces.size(); i++) {
                ja.add(infoPieces.get(i).toJSON());
            }
        }
        return j1;
    }
}
