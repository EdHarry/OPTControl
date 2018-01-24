/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package optstandalone;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Hashtable;


public class OPTsimple extends OPTstandAlone {
    
    boolean doTrackingExp = false;
    
    public OPTsimple() {
        
    }
    
    public OPTsimple(boolean tracking) {
        this.doTrackingExp = tracking;
    }
    
    public void SetToTracking() {
        this.doTrackingExp = true;
    }
    
    public void SetToSimple() {
        this.doTrackingExp = false;
    }
    
    @Override
    int GetNumberOfChannels() throws Exception {
        if (doTrackingExp) {
            return super.GetNumberOfChannels();
        }
        return 1;
    }
    
    @Override
    ArrayList<Hashtable> GetOriginalMetadataHashTables() throws Exception {
        if (doTrackingExp) {
            return super.GetOriginalMetadataHashTables();
        }
        return new ArrayList<Hashtable>();
    }

    @Override
    void UpdateAcquParams() throws Exception {
        if (doTrackingExp) {
            super.UpdateAcquParams();
            return;
        }
        CalcActualInterFrameAngularDisplacement();
        acquParam = new AcquisitionParams(acquParam.getNFrames(), actualInterFrameAngularDisplacement, acquParam.getFileName(), acquParam.getTimeLapse(), actualInterChannelAngularDisplacement, true);
    }
    
    @Override
    void RunImageAcquisition() throws Exception {
        if (doTrackingExp) {
            super.RunImageAcquisition();
            return;
        }
        
        SetShutter(true);
        
        deltaTs = new ArrayList<Long>(GetNumberOfChannels() * acquParam.getNFrames());
        double currentAngle = GetCurrentAngle();
        BufferedImage imgBuf;
        timeLapseLong = Math.round(1000 * acquParam.getTimeLapse());

        // Loop for remaining timepoints
        for (int iTime = 0; iTime < acquParam.getNFrames(); iTime++) {
            MoveStage(currentAngle);
            imgBuf = SnapToBufferedImage_lapse();
            synchronized (bufferedImageList) {
                bufferedImageList.add(imgBuf);
            }

            currentAngle += acquParam.getAngularDisplacement();
        }
        
        SetShutter(false);
    }
}
