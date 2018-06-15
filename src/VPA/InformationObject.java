/*
 * Copyright Public Record Office Victoria 2018
 * Licensed under the CC-BY license http://creativecommons.org/licenses/by/3.0/au/
 * Author Andrew Waugh
 * Version 1.0 February 2018
 */
package VPA;

import java.util.ArrayList;

/**
 * Represents an Information Object from a VEO
 */
public class InformationObject {

    private PIDService ps;      // service to allocate PIDs
    public String veoVersion;   // version of the VEO
    public String veoFileName;  // file name of the VEO
    public String v2VeoType;    // if V2 VEO, is it a File or Record VEO
    public String veoPID;       // PID for the VEO that contained the IO
    public String v2VEOId;      // if V2 VEO, the identifier
    public int seqNo;           // sequence number of this IO within the VEO
    public int depth;           // depth of this Information Object in VEO
    public InformationObject parent; // parent IO
    public ArrayList<InformationObject> children;  // child IOs
    public String ioPID;        // PID for this IO
    public String title;        // title (may be the same as label)
    public String label;        // label associated with the IO
    public ArrayList<Identifier> ids; // list of identifiers associated with this IO
    public String dateCreated;  // date item created (null if unknown)
    public String dateRegistered; // date item was registered (null if unknown)
    public ArrayList<String> descriptions; // descriptions from VEO
    public ArrayList<Relationship> relations; // links to other IOs
    public ArrayList<String> jurisdictionalCoverage;    // list of jurisdictional coverage metadata
    public ArrayList<String> spatialCoverage; // list of spatial coverage metadata
    public ArrayList<String> disposalAuthorisations; // list of disposal authorisations
    public ArrayList<InformationPiece> infoPieces; // information pieces
    public ArrayList<MetadataPackage> metaPackages; // metadata packages

    public InformationObject(PIDService ps, int seqNo) {
        free();
        this.seqNo = seqNo;
        this.ps = ps;

        children = new ArrayList<>();
        ids = new ArrayList<>();
        descriptions = new ArrayList<>();
        relations = new ArrayList<>();
        metaPackages = new ArrayList<>();
        jurisdictionalCoverage = new ArrayList<>();
        spatialCoverage = new ArrayList<>();
        disposalAuthorisations = new ArrayList<>();
        infoPieces = new ArrayList<>();
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
        }
        ioPID = null;
        title = null;
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
        if (disposalAuthorisations != null) {
            for (i = 0; i < disposalAuthorisations.size(); i++) {
                disposalAuthorisations.set(i, null);
            }
            disposalAuthorisations.clear();
            disposalAuthorisations = null;
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
     * Create a new Information Piece and return it
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
     * Create a new Information Piece and return it
     *
     * @return the new Information Piece
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
     * Represent the IO as JSON
     *
     * @return a string representing the IO
     */
    public String toJSON() {
        StringBuilder sb = new StringBuilder();
        int i, j;

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
        if (title != null) {
            sb.append("  \"title\":\"" + Json.safe(title) + "\",\n");
        }
        if (label != null) {
            sb.append("  \"label\":\"" + Json.safe(label) + "\",\n");
        }
        if (ids.size() > 0) {
            sb.append("  \"identifiers\":[\n");
            for (i = 0; i < ids.size(); i++) {
                sb.append("    {\"identifier\":{\"value\":" + ids.get(i).idString + ", \"scheme\":\""+ids.get(i).idScheme+"\"}}");
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
                sb.append(relations.get(i).toJSON());
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
        if (disposalAuthorisations.size() > 0) {
            sb.append("  \"disposalAuthorisation\":[\n");
            for (i = 0; i < disposalAuthorisations.size(); i++) {
                sb.append("    {\"mandate\":\"" + Json.safe(disposalAuthorisations.get(i)) + "\"}");
                if (i < disposalAuthorisations.size() - 1) {
                    sb.append(",\n");
                }
            }
            sb.append("],\n");
        }
        if (metaPackages.size() > 0) {
            sb.append("  \"metaPackages\":[\n");
            for (i = 0; i < metaPackages.size(); i++) {
                sb.append(metaPackages.get(i).toJSON());
                if (i < metaPackages.size() - 1) {
                    sb.append(",\n");
                }
            }
            sb.append("],\n");
        }
        if (infoPieces.size() > 0) {
            sb.append("  \"infoPieces\":[\n");
            for (i = 0; i < infoPieces.size(); i++) {
                sb.append(infoPieces.get(i).toJSON());
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
     * This private class encapsulates the information associated with an
     * Identifier
     */
    private class Identifier {

        public String idString; // the actual identifier
        public String idScheme; // the scheme that allocated the identifier

        public Identifier(String idString, String idScheme) {
            this.idString = idString;
            if (idScheme == null) {
                this.idScheme = "Unknown";
            } else {
                this.idScheme = idScheme;
            }
        }

        public void free() {
            idString = null;
            idScheme = null;
        }

        public String toJSON() {
            StringBuilder sb = new StringBuilder();

            sb.append("{");
            sb.append("\"value\":\"" + Json.safe(idString) + "\", ");
            sb.append("\"scheme\":\"" + Json.safe(idScheme) + "\"");
            sb.append("}");
            return sb.toString();
        }
    }
}
