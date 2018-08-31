/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package VPA;

import VERSCommon.AppError;
import org.json.simple.JSONObject;

/**
 * An instance of this class represents a date found in a VEO. VEOs can have a
 * large number of dates; this allows all the dates to be given to the AMS where
 * they can be analysed.
 */
public final class Date {

    public String type;    // type of date
    public String value;   // the actual date

    /**
     * Default constructor
     *
     * @param type the type of date represented
     * @param value the actual date
     */
    public Date(String type, String value) {
        this.type = type;
        this.value = value;
    }

    /**
     * Free the memory associated with a date
     */
    public void free() {
        type = null;
        value = null;
    }

    /**
     * Return this date as a string. The date will only be returned if
     * both type and value are not null, otherwise a blank string will be
     * returned
     *
     * @return a string containing a JSON type/value pair
     */
    @Override
    public String toString() {
        if (type == null || value == null) {
            return "";
        }
        return "{\"" + Json.safe(type) + "\":\"" + Json.safe(value) + "\"}";
    }
    
    /**
     * Return this date as a JSON instance. The date will only be returned if
     * both type and value are not null, otherwise a blank string will be
     * returned
     *
     * @return a string containing a JSON type/value pair
     * @throws VERSCommon.AppError in case of failure
     */
    public JSONObject toJSON() throws AppError {
        JSONObject j = new JSONObject();
        if (type == null || value == null) {
            return null;
        }
        j.put(type, value);
        return j;
    }
}
