/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package optstandalone;

import java.util.AbstractMap.SimpleEntry;
import java.util.ArrayList;

/**
 *
 * @author fogim
 */
public class HardwareProperties {

    public HardwareProperties() {
        
    }
    
    private String collectionName = null;
    private String deviceName = null;
    private String portName = null;
    private final ArrayList<SimpleEntry<String, String>> COMProperties = new ArrayList<SimpleEntry<String, String>>();
    private final ArrayList<SimpleEntry<String, String>> deviceProperties = new ArrayList<SimpleEntry<String, String>>();

    public void SetCollection(String name) {
        collectionName = name;
    }

    public void SetDevice(String name) {
        deviceName = name;
    }

    public void SetPort(String name) {
        portName = name;
    }

    public String GetCollection() {
        return collectionName;
    }

    public String GetDevice() {
        return deviceName;
    }

    public String GetPort() {
        return portName;
    }

    public boolean IsPortSet() {
        return portName != null;
    }

    public void SetCOMProperty(String key, String value) {
        COMProperties.add(new SimpleEntry<String, String>(key, value));
    }

    public void SetDeviceProperty(String key, String value) {
        deviceProperties.add(new SimpleEntry<String, String>(key, value));
    }

    public ArrayList<SimpleEntry<String, String>> GetCOMProperties() {
        return COMProperties;
    }

    public ArrayList<SimpleEntry<String, String>> GetDeviceProperties() {
        return deviceProperties;
    }
}
