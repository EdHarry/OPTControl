/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package optstandalone;

/**
 *
 * @author fogim
 */
public class HardwareSettingsCollection {
    
    public HardwareSettingsCollection() {
        
    }
    
    private HardwareProperties CameraProperties = new HardwareProperties();
    private HardwareProperties StageProperties = new HardwareProperties();
    private HardwareProperties ShutterProperties = new HardwareProperties();
    
    public HardwareProperties GetCameraProperties() {
        return CameraProperties;
    }
    public HardwareProperties GetStageProperties() {
        return StageProperties;
    }
    public HardwareProperties GetShutterProperties() {
        return ShutterProperties;
    }
    
    public void SetCameraProperties(HardwareProperties prop) {
        CameraProperties = prop;
    }
    public void SetStageProperties(HardwareProperties prop) {
        StageProperties = prop;
    }
    public void SetShutterProperties(HardwareProperties prop) {
        ShutterProperties = prop;
    }
}
