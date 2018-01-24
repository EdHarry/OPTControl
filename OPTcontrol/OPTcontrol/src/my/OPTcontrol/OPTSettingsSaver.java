/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package my.OPTcontrol;

/**
 *
 * @author edwardharry
 */
public class OPTSettingsSaver implements java.io.Serializable {

    String mMPath, exp, speed, acc, saveFile, frameAngle, timeLapse, channelAngle, nFrames;
    String pixBin, bitDepth, readoutRate, MRes;
    OPTcontrolUI.ExperimentType exType;
    java.awt.geom.Rectangle2D.Float rOI;
    boolean autoCalc;

    public OPTSettingsSaver(OPTcontrolUI.SaveSettings saveSettings) {
        this.mMPath = saveSettings.GetMMPath();
        this.exp = saveSettings.GetExp();
        this.speed = saveSettings.GetSpeed();
        this.acc = saveSettings.GetAcc();
        this.saveFile = saveSettings.GetSaveFile();
        this.frameAngle = saveSettings.GetFrameAngle();
        this.timeLapse = saveSettings.GetTimeLapse();
        this.channelAngle = saveSettings.GetChannelAngle();
        this.nFrames = saveSettings.GetNFrames();
        this.pixBin = saveSettings.GetPixelBin();
        this.bitDepth = saveSettings.GetBitDepth();
        this.readoutRate = saveSettings.GetReadoutRate();
        this.MRes = saveSettings.GetMicroStepRes();
        this.exType = saveSettings.GetExpierimentType();
        this.rOI = saveSettings.GetROI();
        this.autoCalc = saveSettings.GetIsOverride();
    }

    public String GetMMPath() {
        return this.mMPath;
    }

    public String GetExp() {
        return this.exp;
    }

    public String GetSpeed() {
        return this.speed;
    }

    public String GetAcc() {
        return this.acc;
    }

    public String GetSaveFile() {
        return this.saveFile;
    }

    public String GetFrameAngle() {
        return this.frameAngle;
    }

    public String GetTimeLapse() {
        return this.timeLapse;
    }

    public String GetChannelAngle() {
        return this.channelAngle;
    }

    public String GetNFrames() {
        return this.nFrames;
    }

    public String GetPixelBin() {
        return this.pixBin;
    }

    public String GetBitDepth() {
        return this.bitDepth;
    }

    public String GetReadoutRate() {
        return this.readoutRate;
    }

    public String GetMicroStepRes() {
        return this.MRes;
    }

    public OPTcontrolUI.ExperimentType GetExpierimentType() {
        return this.exType;
    }

    public java.awt.geom.Rectangle2D.Float GetROI() {
        return this.rOI;
    }

    public boolean GetIsOverride() {
        return this.autoCalc;
    }
}
