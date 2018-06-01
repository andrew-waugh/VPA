/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package VPA;

import java.util.ArrayList;

/**
 * This class represents a relationship between two Information Objects.
 *
 * @author Andrew
 */
public class Relationship {

    ArrayList<String> types;         // type of relationship
    ArrayList<String> targetIds;     // identifier of the related Information Object
    ArrayList<String> descriptions;  // description of relation

    public Relationship() {
        init();
    }

    public Relationship(String type, String targetId, String description) {
        init();
        types.add(type);
        if (targetId != null) {
            targetIds.add(targetId);
        }
        if (description != null) {
            descriptions.add(description);
        }
    }

    private void init() {
        types = new ArrayList<>();
        targetIds = new ArrayList<>();
        descriptions = new ArrayList<>();
    }

    public void free() {
        int i;

        for (i = 0; i < types.size(); i++) {
            types.set(i, null);
        }
        types.clear();
        types = null;
        for (i = 0; i < targetIds.size(); i++) {
            targetIds.set(i, null);
        }
        targetIds.clear();
        targetIds = null;
        for (i = 0; i < descriptions.size(); i++) {
            descriptions.set(i, null);
        }
        descriptions.clear();
        descriptions = null;
    }
}
