/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package optstandalone;

import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;
import java.awt.image.DataBufferUShort;
import java.io.File;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Hashtable;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import mmcorej.CMMCore;
import mmcorej.PropertyType;
import loci.common.services.ServiceFactory;
import loci.formats.gui.BufferedImageWriter;
import loci.formats.meta.MetadataRetrieve;
import loci.formats.ome.OMEXMLMetadata;
import loci.formats.out.OMETiffWriter;
import loci.formats.out.TiffWriter;
import loci.formats.services.OMEXMLService;
import ome.xml.model.enums.DimensionOrder;
import ome.xml.model.enums.PixelType;
import ome.xml.model.primitives.NonNegativeInteger;
import ome.xml.model.primitives.PositiveInteger;

/**
 *
 * @author fogim
 */
public class OPTstandAlone {

    class AcquisitionParams {

        private final int nFrames;
        private final double angularDisplacement;
        private final String fileName;
        private final double timeLapse;
        private final double interChannelAngularDisplacement;
        private final boolean overrideDefaultParams;

        AcquisitionParams() {
            this(DefaultAcquisitionParams.Single);
        }

        AcquisitionParams(DefaultAcquisitionParams defaultAcquasitionParams) {
            this(defaultAcquasitionParams.getNFrames(), defaultAcquasitionParams.getAngularDisplacement(), defaultAcquasitionParams.getFileName(), defaultAcquasitionParams.getTimeLapse(), defaultAcquasitionParams.getInterFrameAngle(), defaultAcquasitionParams.getOverride());
        }

        AcquisitionParams(int nFrame, double angularDisplacement, String fileName, double timeLapse, double interChannelAngle, boolean overrideDefaults) {
            this.nFrames = nFrame;
            this.angularDisplacement = angularDisplacement;
            this.fileName = fileName;
            this.timeLapse = timeLapse;
            this.interChannelAngularDisplacement = interChannelAngle;
            this.overrideDefaultParams = overrideDefaults;
        }

        public int getNFrames() {
            return nFrames;
        }

        public double getAngularDisplacement() {
            return angularDisplacement;
        }

        public String getFileName() {
            return fileName;
        }

        public double getTimeLapse() {
            return timeLapse;
        }

        public double getInterChannelAngularDisplacement() {
            return interChannelAngularDisplacement;
        }

        public boolean getOverrride() {
            return overrideDefaultParams;
        }
    }

    enum DefaultAcquisitionParams {

        Single(1, 0, "C:\\Users\\fogim\\Documents\\TestImages\\TestImage_single.ome.tiff", 15, 90, true),
        Long(100, 0.9, "C:\\Users\\fogim\\Documents\\TestImages\\TestImage_long.ome.tiff", 15, 90, true);
        private final int nFrames;
        private final double angularDisplacement;
        private final String fileName;
        private final double timeLapse;
        private final double interChannelAngle;
        private final boolean overrideParam;

        DefaultAcquisitionParams(int nFrame, double angularDisplacement, String fileName, double timeLapse, double interChannelAngle, boolean overrideParam) {
            this.nFrames = nFrame;
            this.angularDisplacement = angularDisplacement;
            this.fileName = fileName;
            this.timeLapse = timeLapse;
            this.interChannelAngle = interChannelAngle;
            this.overrideParam = overrideParam;
        }

        public int getNFrames() {
            return nFrames;
        }

        public double getAngularDisplacement() {
            return angularDisplacement;
        }

        public String getFileName() {
            return fileName;
        }

        public double getTimeLapse() {
            return timeLapse;
        }

        public double getInterFrameAngle() {
            return interChannelAngle;
        }

        public boolean getOverride() {
            return overrideParam;
        }
    }

    /**
     *
     */
    public enum ImageType {

        Unassigned, EightBit, SixteenBit
    }

    enum RepeatedFrame {

        Unknown(null), True(Boolean.TRUE), False(Boolean.FALSE);
        private final Boolean state;

        private RepeatedFrame(Boolean state) {
            this.state = state;
        }

        public Boolean getState() {
            return state;
        }
    }
    // Global Variables
    CMMCore core = null;
    int buffSize;
    ImageType imageType = ImageType.Unassigned;
    String rotDeviceName = "RotStage";
    String cameraName = "Camera";
    String shutterName = "Shutter";
    OMEXMLMetadata omexml = null;
    BufferedImageWriter writer = null;
    AcquisitionParams acquParam = null;
    int globalImageCount = 0;
    AcquisitionThread acquisitionThread = null;
    ImageSaveThread imageSaveThread = null;
    List bufferedImageList = Collections.synchronizedList(new ArrayList<BufferedImage>());
    final AtomicBoolean finishedImaging = new AtomicBoolean(false);
    final AtomicBoolean finishedImageWriting = new AtomicBoolean(false);
    ArrayList<Long> deltaTs = null;
    long timeLapseLong = 0;
    long previousFrameTime = 0;
    double actualInterFrameAngularDisplacement = 0;
    double actualInterChannelAngularDisplacement = 0;
    int actualNframes = 0;
    RepeatedFrame repeatedFrame = RepeatedFrame.Unknown;
    //String microManagerPath_default = "C:\\Program Files\\Micro-Manager-1.4\\";
    //boolean validPath = false;
    java.awt.geom.Rectangle2D.Float originalROI = null;
    
    /**
     * @param args the command line arguments
     * @throws java.lang.Exception
     */
    public static void main(String[] args) throws Exception {
        OPTstandAlone OPT = new OPTstandAlone();
        OPT.MainProgram(args);
    }

    /**
     *
     * @throws Exception
     */
    public void DoImaging() throws Exception {
        //if (!validPath) {
            //System.out.println("Cannot start imaging, must set a valid micromanager path.");
            //return;
        //}
        if (core == null) {
            System.out.println("Cannot start imaging, must initalise system first.");
            return;
        }
        if (acquParam == null) {
            System.out.println("Cannot start imaging, must set acquisition parameters first.");
            return;
        }
        if (writer == null || omexml == null) {
            System.out.println("Cannot start imaging, must setup bioformats writer first.");
            return;
        }

        globalImageCount = 0;
        
        buffSize = core.getImageBufferSize() / (int) core.getBytesPerPixel();
        
        Exception exception = null;

        try {
            // Create Threads
            CreateAcquisitionThread();
            CreateImageSaveThread();

            // Wait for imaging to finish
            while (!finishedImageWriting.get()) {
                Thread.sleep(1000);
            }
        } catch (Exception e) {
            exception = e;
        }

        try {
            // Finalise output
            Finalise();
        } catch (Exception e) {
            if (exception == null) {
                exception = e;
            }
        }

        if (exception != null) {
            throw exception;
        }
    }
    
    /**
     *
     * @param args
     * @throws Exception
     */
    public void MainProgram(String[] args) throws Exception {
            //Set Paths
            //SetPath();
            
            //if (!validPath) {
            //    return;
            //}
            
            // Initalise
            Initalise();

            // Initalise Parameters
            InitaliseParameters(args);

            // Initalise OME
            InitaliseOME();

            // Create Threads
            CreateAcquisitionThread();
            CreateImageSaveThread();

            // Wait for imaging to finish
            while (!finishedImageWriting.get()) {
            }

            // Finalise output
            Finalise();
    }

    //void SetPath() throws Exception {
    //    SetPath(microManagerPath_default);
    //}
    
//    /**
//     *
//     * @param path
//     * @throws Exception
//     */
//    public void SetPath(String path) throws Exception {
//        validPath = false;
//
//        File dir = new File(path).getAbsoluteFile();
//        boolean ret_1 = false;
//        //boolean ret_2 = false;
//        boolean ret_2 = true;
//        
//        if (dir.exists()) {
//            ret_1 = (System.setProperty("user.dir", dir.getAbsolutePath()) != null);
//           //ret_2 = (System.setProperty("mmcorej.library.path", dir.getAbsolutePath()) != null);
//        } else {
//            throw new Exception("Micromanager path does not exist.");
//        }
//        
//        if (!ret_1 && !ret_2) {
//            throw new Exception("Could not set either 'user.dir' or 'mmcorj.library.path'.");
//        } else if (!ret_1) {
//            throw new Exception("Could not set 'user.dir'.");
//        } else if (!ret_2) {
//            throw new Exception("Could not set 'mmcorej.library.path'.");
//        }
//        
//        validPath = true;
//    }
    
    /**
     *
     * @throws Exception
     */
    public void Initalise() throws Exception {
        if (core != null) {
            core.unloadAllDevices();
        }

        // Initialise
        core = new CMMCore();

        // Unload all devices
        core.unloadAllDevices();

        // Load SerialManager
        core.loadDevice("COM1", "SerialManager", "COM1");
        core.loadDevice("COM5", "SerialManager", "COM5");
        core.setProperty("COM5", "BaudRate", "115200");

        // Load Zaber and set port
        core.loadDevice(rotDeviceName, "ZaberRotationStage", "RotationStage");
        core.setProperty(rotDeviceName, "Port", "COM1");

        // Load Thorlabs TSC001 shutter and set port
        core.loadDevice(shutterName, "Thorlabs_TSC001", "TSC001");
        core.setProperty(shutterName, "Port", "COM5");
        
        // Load Zyla
        core.loadDevice(cameraName, "AndorSDK3", "Andor sCMOS Camera");
        
        // Initialise all devices
        core.initializeAllDevices();

        // Set Maximum bit-depth on camera
        core.setProperty(cameraName, "Sensitivity/DynamicRange", "16-bit (low noise & high well capacity)");

        // Set default camera
        core.setCameraDevice(cameraName);
        
        // Set default shutter
        core.setShutterDevice(shutterName);

        // Set auto-shutter to ON
        core.setAutoShutter(true);

        // Assign RotationStage to be synchronised before taking images
        core.assignImageSynchro(rotDeviceName);

        // Get image type
        if (core.getBytesPerPixel() == 1) {
            imageType = ImageType.EightBit;
        } else if (core.getBytesPerPixel() == 2) {
            imageType = ImageType.SixteenBit;
        } else {
            core.unloadAllDevices();
            core = null;
            throw new Exception("Don't know how to handle images with " + core.getBytesPerPixel() + " bytes per pixel.");
        }

        // Get Image Buffer Size
        buffSize = core.getImageBufferSize() / (int) core.getBytesPerPixel();

        // Get Original ROI
        originalROI = GetROI();
        
        // Reset stage position
        ResetStagePosition();
    }

    public void Initalise(HardwareSettingsCollection hardwareSettings) throws Exception {
        if (core != null) {
            core.unloadAllDevices();
        }

        // Initialise
        core = new CMMCore();

        UnpackHardwareSettings(hardwareSettings);
        
        if (core.getLoadedDevices().size() < 3) {
            core.unloadAllDevices();
            core = null;
            return;
        }
        
        // Set default camera
        core.setCameraDevice(cameraName);
        
        // Set default shutter
        core.setShutterDevice(shutterName);

        // Set auto-shutter to ON
        core.setAutoShutter(true);

        // Assign RotationStage to be synchronised before taking images
        core.assignImageSynchro(rotDeviceName);

        // Get image type
        if (core.getBytesPerPixel() == 1) {
            imageType = ImageType.EightBit;
        } else if (core.getBytesPerPixel() == 2) {
            imageType = ImageType.SixteenBit;
        } else {
            core.unloadAllDevices();
            core = null;
            throw new Exception("Don't know how to handle images with " + core.getBytesPerPixel() + " bytes per pixel.");
        }

        // Get Image Buffer Size
        buffSize = core.getImageBufferSize() / (int) core.getBytesPerPixel();

        // Get Original ROI
        originalROI = GetROI();
        
        // Reset stage position
        ResetStagePosition();
    }
    
    private void UnpackHardwareSettings(HardwareSettingsCollection hardwareSettings) throws Exception {
   
        class SettingsUnpacker {
            String devName = null;
            boolean IsCOM = false;
            String COM = null;
            String collection = null;
            String device = null;
            ArrayList<AbstractMap.SimpleEntry<String, String>> COMProps = null;
            ArrayList<AbstractMap.SimpleEntry<String, String>> deviceProps = null;
            
            SettingsUnpacker(HardwareProperties props, String name) {
                if (props == null) {
                    return;
                }
                
                devName = name;
                IsCOM = props.IsPortSet();
                COM = props.GetPort();
                collection = props.GetCollection();
                device = props.GetDevice();
                COMProps = props.GetCOMProperties();
                deviceProps = props.GetDeviceProperties();
            }
        }
        
        core.unloadAllDevices();
        
        ArrayList<SettingsUnpacker> settings = new ArrayList<SettingsUnpacker>(3);
        settings.add(0, new SettingsUnpacker(hardwareSettings.GetCameraProperties(), cameraName));
        settings.add(1, new SettingsUnpacker(hardwareSettings.GetStageProperties(), rotDeviceName));
        settings.add(2, new SettingsUnpacker(hardwareSettings.GetShutterProperties(), shutterName));
        
        for (SettingsUnpacker set : settings) {
            if (set.devName == null) {
                continue;
            }
            
            if (set.IsCOM) {
                core.loadDevice(set.COM, "SerialManager", set.COM);
                
                for (AbstractMap.SimpleEntry<String, String> ent : set.COMProps) {
                    //core.setProperty(set.COM, ent.getKey(), ent.getValue());
                    SetProperty(set.COM, ent.getKey(), ent.getValue());
                }
                
                core.loadDevice(set.devName, set.collection, set.device);
                //core.setProperty(set.devName, "Port", set.COM);
                SetProperty(set.devName, "Port", set.COM);
            } else {
                core.loadDevice(set.devName, set.collection, set.device);
            }
        }
        
        core.initializeAllDevices();
        
        for (SettingsUnpacker set : settings) {
            if (set.devName == null) {
                continue;
            }
            
            for (AbstractMap.SimpleEntry<String, String> ent : set.deviceProps) {
                //core.setProperty(set.devName, ent.getKey(), ent.getValue());
                SetProperty(set.devName, ent.getKey(), ent.getValue());
            }
        }
    }
    
    private void SetProperty(String device, String key, String value) {
        PropertyType propType = null;
        try {
            propType = core.getPropertyType(device, key);
        } catch (Exception ex) {
            System.out.println("Warning: could not identify property '" + key + "' in device '" + device + "'");
            return;
        }
        
        try {
            if (propType.equals(PropertyType.Float)) {
                core.setProperty(device, key, Float.parseFloat(value));
            } else if (propType.equals(PropertyType.Integer)) {
                core.setProperty(device, key, Integer.parseInt(value));
            } else {
                core.setProperty(device, key, value);
            }
        } catch (Exception ex) {
            System.out.println("Warning: could not set property '" + key + "' in device '" + device + "'");
        }
        
    }
    
    /**
     *
     * @param args
     * @throws Exception
     */
    public void InitaliseParameters(String[] args) throws Exception {
        int nArgs = args.length;

        if (nArgs == 0) {
            acquParam = new AcquisitionParams();
        } else if (nArgs == 1) {
            if (args[0].equals("Single") || args[0].equals("single") || args[0].equals("S") || args[0].equals("s")) {
                acquParam = new AcquisitionParams(DefaultAcquisitionParams.Single);
            } else if (args[0].equals("Long") || args[0].equals("long") || args[0].equals("L") || args[0].equals("l")) {
                acquParam = new AcquisitionParams(DefaultAcquisitionParams.Long);
            } else {
                throw new Exception("Single input argument must be 'Single' or 'Long'.");
            }
        } else if (nArgs % 2 != 0) {
            throw new Exception("Input arguments must be issued in pairs.");
        } else {
            int nFrames = 0;
            boolean didSet_nFrames = false;
            double angularDisplacement = 0;
            boolean didSet_angularDisplacement = false;
            String fileName = "";
            boolean didSet_fileName = false;
            double timeLapse = 0;
            boolean didSet_timeLapse = false;
            double interChannelAngle = 0;
            boolean didSet_interChannelAngle = false;
            boolean overrideParam = false;
            boolean didSet_overrideParam = false;
            AcquisitionParams tmpAcquParam = new AcquisitionParams();

            for (int i = 0; i < nArgs; i += 2) {
                if (args[i].equals("nFrames") || args[i].equals("frames") || args[i].equals("F") || args[i].equals("f")) {
                    nFrames = Integer.parseInt(args[i + 1]);
                    didSet_nFrames = true;
                } else if (args[i].equals("angularDisplacement") || args[i].equals("angle") || args[i].equals("A") || args[i].equals("a")) {
                    angularDisplacement = Double.parseDouble(args[i + 1]);
                    didSet_angularDisplacement = true;
                } else if (args[i].equals("fileName") || args[i].equals("file") || args[i].equals("name") || args[i].equals("N") || args[i].equals("n")) {
                    fileName = args[i + 1];
                    didSet_fileName = true;
                } else if (args[i].equals("timeLapse") || args[i].equals("lapse") || args[i].equals("time") || args[i].equals("t") || args[i].equals("T")) {
                    timeLapse = Double.parseDouble(args[i + 1]);
                    didSet_timeLapse = true;
                } else if (args[i].equals("interChannelAngle") || args[i].equals("jump") || args[i].equals("bigAngle") || args[i].equals("j") || args[i].equals("J")) {
                    interChannelAngle = Double.parseDouble(args[i + 1]);
                    didSet_interChannelAngle = true;
                } else if (args[i].equals("override") || args[i].equals("o") || args[i].equals("O")) {
                    if (args[i + 1].equalsIgnoreCase("true") || args[i + 1].equalsIgnoreCase("false")) {
                        overrideParam = Boolean.parseBoolean(args[i + 1]);
                        didSet_overrideParam = true;
                    } else {
                        throw new Exception("Parsing boolean for parameter 'override' must be either 'true' or 'false'.");
                    }
                }
            }

            if (!didSet_nFrames) {
                nFrames = tmpAcquParam.getNFrames();
            }

            if (!didSet_angularDisplacement) {
                angularDisplacement = tmpAcquParam.getAngularDisplacement();
            }

            if (!didSet_fileName) {
                fileName = tmpAcquParam.getFileName();
            }

            if (!didSet_timeLapse) {
                timeLapse = tmpAcquParam.getTimeLapse();
            }

            if (!didSet_interChannelAngle) {
                interChannelAngle = tmpAcquParam.getInterChannelAngularDisplacement();
            }

            if (!didSet_overrideParam) {
                overrideParam = tmpAcquParam.getOverrride();
            }

            acquParam = new AcquisitionParams(nFrames, angularDisplacement, fileName, timeLapse, interChannelAngle, overrideParam);
        }
        UpdateAcquParams();
    }

    void MoveStage(double angle) throws Exception {
        core.setProperty(rotDeviceName, "Angle [deg]", angle);
    }

    double GetCurrentAngle() throws Exception {
        return Double.parseDouble(core.getProperty(rotDeviceName, "Angle [deg]"));
    }

    /**
     *
     * @throws Exception
     */
    public void InitaliseOME() throws Exception {
        OMETiffWriter tiffWriter = new OMETiffWriter();
        tiffWriter.setBigTiff(true);
        writer = new BufferedImageWriter(tiffWriter);
        ServiceFactory factory = new ServiceFactory();
        OMEXMLService service = factory.getInstance(OMEXMLService.class);
        omexml = service.createOMEXMLMetadata();
        SetupOMEMetadata();

        ArrayList<Hashtable> list = GetOriginalMetadataHashTables();
        for (Hashtable hash : list) {
            service.populateOriginalMetadata(omexml, hash);
        }

        writer.setMetadataRetrieve((MetadataRetrieve) omexml);
        writer.setId(acquParam.getFileName());
        writer.setCompression(TiffWriter.COMPRESSION_UNCOMPRESSED);
        writer.setWriteSequentially(true);
    }

    void SetupOMEMetadata() throws Exception {
        omexml.setImageID("Image:0", 0);
        omexml.setPixelsID("Pixels:0", 0);
        omexml.setPixelsBinDataBigEndian(Boolean.TRUE, 0, 0);
        omexml.setPixelsDimensionOrder(DimensionOrder.XYCTZ, 0);

        switch (imageType) {
            case EightBit:
                omexml.setPixelsType(PixelType.UINT8, 0);

            case SixteenBit:
                omexml.setPixelsType(PixelType.UINT16, 0);
        }

        omexml.setPixelsSizeX(new PositiveInteger((int) core.getImageWidth()), 0);
        omexml.setPixelsSizeY(new PositiveInteger((int) core.getImageHeight()), 0);
        omexml.setPixelsSizeZ(new PositiveInteger(1), 0);
        omexml.setPixelsSizeC(new PositiveInteger(GetNumberOfChannels()), 0);
        omexml.setPixelsSizeT(new PositiveInteger(acquParam.getNFrames()), 0);

        for (int channel = 0; channel < GetNumberOfChannels(); channel++) {
            omexml.setChannelID("Channel:0" + channel, 0, channel);
            omexml.setChannelSamplesPerPixel(new PositiveInteger(1), 0, channel);
        }
    }

    void WriteToOME(BufferedImage imgBuf) throws Exception {
        writer.saveImage(globalImageCount, imgBuf);
        globalImageCount++;
    }

    void Finalise() throws Exception {
        Exception exception = null;
        try {
            WriteDeltaTMetadata();
        } catch (Exception e) {
            exception = e;
        }
        ResetStagePosition();
        writer.close();
        writer = null;
        omexml = null;
        globalImageCount = 0;
        finishedImageWriting.set(false);
        finishedImaging.set(false);
        repeatedFrame = RepeatedFrame.Unknown;
        System.out.println("Acquasition Complete");
        if (exception != null) {
            throw exception;
        }
    }

    void CreateAcquisitionThread() throws Exception {
        acquisitionThread = new AcquisitionThread();
        acquisitionThread.start();
        System.out.println("Image acquisition thread started.");
    }

    BufferedImage CreateNewBufferedImage() throws Exception {
        // Setup BufferedImage
        switch (imageType) {
            case EightBit:
                return new BufferedImage((int) core.getImageWidth(), (int) core.getImageHeight(), BufferedImage.TYPE_BYTE_GRAY);

            case SixteenBit:
                return new BufferedImage((int) core.getImageWidth(), (int) core.getImageHeight(), BufferedImage.TYPE_USHORT_GRAY);
        }
        throw new Exception("CreateNewBufferedImage: Unknown ImageType.");
    }

    void WriteToBufferedImage(BufferedImage imgBuf) throws Exception {
        switch (imageType) {
            case EightBit:
                System.arraycopy((byte[]) core.getImage(), 0, ((DataBufferByte) imgBuf.getRaster().getDataBuffer()).getData(), 0, buffSize);

            case SixteenBit:
                System.arraycopy((short[]) core.getImage(), 0, ((DataBufferUShort) imgBuf.getRaster().getDataBuffer()).getData(), 0, buffSize);
        }
    }

    // Time-Lapse
    BufferedImage SnapToBufferedImage_lapse() throws Exception {
        core.waitForImageSynchro();
        Thread.sleep(Math.max(0, timeLapseLong + previousFrameTime - System.currentTimeMillis() - 10));
        while (System.currentTimeMillis() - previousFrameTime < timeLapseLong) {
        }
        previousFrameTime = System.currentTimeMillis();
        deltaTs.add(previousFrameTime);
        core.snapImage();
        BufferedImage imgBuf = CreateNewBufferedImage();
        WriteToBufferedImage(imgBuf);
        return imgBuf;
    }

    // No Time-Lapse
    BufferedImage SnapToBufferedImage() throws Exception {
        core.waitForImageSynchro();
        deltaTs.add(System.currentTimeMillis());
        core.snapImage();
        BufferedImage imgBuf = CreateNewBufferedImage();
        WriteToBufferedImage(imgBuf);
        return imgBuf;
    }

    void RunImageAcquisition() throws Exception {
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

            MoveStage(currentAngle + acquParam.getInterChannelAngularDisplacement());
            imgBuf = SnapToBufferedImage();
            synchronized (bufferedImageList) {
                bufferedImageList.add(imgBuf);
            }

            currentAngle += acquParam.getAngularDisplacement();
        }

    }

    class AcquisitionThread extends Thread {

        @Override
        public void run() {
            try {
                RunImageAcquisition();
                finishedImaging.set(true);
            } catch (Exception e) {
                System.out.println("Exception in imaging thread: " + e.getMessage() + "\nExiting now.");
                //System.exit(1);
                Thread t = Thread.currentThread();
                t.getUncaughtExceptionHandler().uncaughtException(t, e);
            }
        }
    }

    void ImageSaveSequence() throws Exception {
        BufferedImage imgBuf;
        while (!finishedImaging.get() || bufferedImageList.size() > 0) {
            if (bufferedImageList.size() > 0) {
                synchronized (bufferedImageList) {
                    imgBuf = (BufferedImage) bufferedImageList.get(0);
                    bufferedImageList.remove(0);
                }
                WriteToOME(imgBuf);
            }
        }
    }

    class ImageSaveThread extends Thread {

        @Override
        public void run() {
            try {
                ImageSaveSequence();
                finishedImageWriting.set(true);
            } catch (Exception e) {
                System.out.println("Exception in image save thread: " + e.getMessage() + "\nExiting now.");
                //System.exit(1);
                Thread t = Thread.currentThread();
                t.getUncaughtExceptionHandler().uncaughtException(t, e);
            }
        }
    }

    void CreateImageSaveThread() throws Exception {
        imageSaveThread = new ImageSaveThread();
        imageSaveThread.start();
        System.out.println("File writer thread started.");
    }

    void WriteDeltaTMetadata() throws Exception {
        long firstTime = deltaTs.get(0);
        for (int iTime = 0; iTime < GetNumberOfChannels() * acquParam.getNFrames(); iTime++) {
            omexml.setPlaneDeltaT(((double) (deltaTs.get(iTime) - firstTime)) / 1000, 0, iTime);
            omexml.setPlaneTheC(new NonNegativeInteger(iTime % GetNumberOfChannels()), 0, iTime);
            omexml.setPlaneTheT(new NonNegativeInteger((int) Math.floor(((double) iTime) / (double) GetNumberOfChannels())), 0, iTime);
            omexml.setPlaneTheZ(new NonNegativeInteger(0), 0, iTime);
        }
    }

    void CalcActualInterFrameAngularDisplacement() throws Exception {
        double currentAngle = GetCurrentAngle();
        MoveStage(currentAngle + acquParam.getAngularDisplacement());
        core.waitForImageSynchro();
        actualInterFrameAngularDisplacement = GetCurrentAngle() - currentAngle;
    }

    void CalcActualInterChannelAngularDisplacementAndNframes() throws Exception {
        long nStepsPer180 = Math.round(180 / actualInterFrameAngularDisplacement);
        long nStepsPer90 = (long) Math.ceil(((double) nStepsPer180) / 2);
        actualInterChannelAngularDisplacement = nStepsPer90 * actualInterFrameAngularDisplacement;
        actualNframes = (int) (nStepsPer180 - nStepsPer90 + 1);
        if (nStepsPer180 % 2 == 0) {
            repeatedFrame = RepeatedFrame.True;
        } else {
            repeatedFrame = RepeatedFrame.False;
        }
    }
    
    void CalcActualInterChannelAngularDisplacement() throws Exception {
        actualInterChannelAngularDisplacement = Math.round(acquParam.getInterChannelAngularDisplacement() / actualInterFrameAngularDisplacement) * actualInterFrameAngularDisplacement;
    }

    void UpdateAcquParams() throws Exception {
        CalcActualInterFrameAngularDisplacement();
        if (!acquParam.getOverrride()) {
            CalcActualInterChannelAngularDisplacement();
            acquParam = new AcquisitionParams(acquParam.getNFrames(), actualInterFrameAngularDisplacement, acquParam.getFileName(), acquParam.getTimeLapse(), actualInterChannelAngularDisplacement, true);
            return;
        }
        CalcActualInterChannelAngularDisplacementAndNframes();
        acquParam = new AcquisitionParams(actualNframes, actualInterFrameAngularDisplacement, acquParam.getFileName(), acquParam.getTimeLapse(), actualInterChannelAngularDisplacement, true);
    }

    ArrayList<Hashtable> GetOriginalMetadataHashTables() throws Exception {
        ArrayList<Hashtable> list = new ArrayList<Hashtable>();
        Hashtable<String, Double> angles = new Hashtable<String, Double>();
        angles.put("Inter frame angle", acquParam.getAngularDisplacement());
        angles.put("Inter channel angle", acquParam.getInterChannelAngularDisplacement());
        list.add(angles);

        switch (repeatedFrame) {
            case True:
            case False:
                Hashtable<String, Boolean> repFrame = new Hashtable<String, Boolean>();
                repFrame.put("Repeated frame", repeatedFrame.getState());
                list.add(repFrame);
        }

        return list;
    }

    void ResetStagePosition() throws Exception {
        core.setProperty(rotDeviceName, "Position", 0);
        core.waitForImageSynchro();
    }
    
    int GetNumberOfChannels() throws Exception {
        return 2;
    }
    
    /**
     *
     * @return
     * @throws Exception
     */
    public int GetNumberOfFrames() throws Exception {
        return acquParam.getNFrames();
    }
    
    /**
     *
     * @return
     * @throws Exception
     */
    public int GetCurrentFrame() throws Exception {
        int imageCount = Math.min(globalImageCount, GetNumberOfFrames() * GetNumberOfChannels());
        return (int) Math.floor(((double) imageCount) / ((double) GetNumberOfChannels()));
    }
    
//    /**
//     *
//     * @return
//     * @throws Exception
//     */
//    public boolean IsPathSet() throws Exception {
//        return validPath;
//    }
    
    /**
     *
     * @return
     * @throws Exception
     */
    public boolean IsInitalised() throws Exception {
        return core != null;
    }
    
    /**
     *
     * @param device
     * @param property
     * @return
     * @throws Exception
     */
    public String[] GetAvailablePropertyValues(String device, String property) throws Exception {
        if (device.equalsIgnoreCase("stage")) {
            return core.getAllowedPropertyValues(rotDeviceName, property).toArray();
        } else if (device.equalsIgnoreCase("camera")) {
            return core.getAllowedPropertyValues(cameraName, property).toArray();
        }
        return null;
    }
    
    /**
     *
     * @param device
     * @param property
     * @param value
     * @throws Exception
     */
    public void SetDeviceProperty(String device, String property, String value) throws Exception {
        if (device.equalsIgnoreCase("stage")) {
            core.setProperty(rotDeviceName, property, value);
        } else if (device.equalsIgnoreCase("camera")) {
            core.setProperty(cameraName, property, value);
        }
    }
    
    /**
     *
     * @param device
     * @param property
     * @param value
     * @throws Exception
     */
    public void SetDeviceProperty(String device, String property, double value) throws Exception {
        if (device.equalsIgnoreCase("stage")) {
            core.setProperty(rotDeviceName, property, value);
        } else if (device.equalsIgnoreCase("camera")) {
            core.setProperty(cameraName, property, value);
        }
    }
    
    /**
     *
     * @param device
     * @param property
     * @param value
     * @throws Exception
     */
    public void SetDeviceProperty(String device, String property, int value) throws Exception {
        if (device.equalsIgnoreCase("stage")) {
            core.setProperty(rotDeviceName, property, value);
        } else if (device.equalsIgnoreCase("camera")) {
            core.setProperty(cameraName, property, value);
        }
    }
    
    /**
     *
     * @param device
     * @param property
     * @return
     * @throws Exception
     */
    public String GetDeviceProperty(String device, String property) throws Exception {
        if (device.equalsIgnoreCase("stage")) {
            return core.getProperty(rotDeviceName, property);
        } else if (device.equalsIgnoreCase("camera")) {
            return core.getProperty(cameraName, property);
        }
        return null;
    }
    
    public String GetExposure() throws Exception {
        return Double.toString(core.getExposure());
    }
    
    public void SetExposure(String s) throws Exception {
        core.setExposure(Double.parseDouble(s));
    }
    
    public int[] GetImageWidthAndHeight() throws Exception {
        int[] sizes = new int[2];
        sizes[0] = (int) core.getImageWidth();
        sizes[1] = (int) core.getImageHeight();
        return sizes;
    }
    
    public Object GetSnapImage() throws Exception {
//        core.snapImage();
//        switch (imageType) {
//            case EightBit:
//                return (byte[]) core.getImage();
//            case SixteenBit:
//                return (short[]) core.getImage();
//        }
//        return null;
        core.snapImage();
        return core.getImage();
    }
    
    public ImageType GetImageType() throws Exception {
        return imageType;
    }
    
    public java.awt.geom.Rectangle2D.Float GetROI() throws Exception {
        int[] x = new int[1];
        int[] y = new int[1];
        int[] width = new int[1];
        int[] height = new int[1];
        core.getROI(x, y, width, height);
        return new java.awt.geom.Rectangle2D.Float((float) x[0], (float) y[0], (float) width[0], (float) height[0]);
    }
    
    public void SetROI(java.awt.geom.Rectangle2D.Float rec) throws Exception {
        core.setROI((int) rec.x, (int) rec.y, (int) rec.width, (int) rec.height);
    }
    
    public void ClearROI() throws Exception {
        SetROI(originalROI);
    }
    
    public void SetShutter(boolean state) throws Exception {
        if (state) {
            core.setAutoShutter(false);
            core.setShutterOpen(true);
        }
        else {
            core.setShutterOpen(false);
            core.setAutoShutter(true);
        }
    }
    
    public void RunCalibration(BufferedImage img_1, BufferedImage img_2) throws Exception {
        core.waitForImageSynchro();
        
        double currentAngle = GetCurrentAngle();
        
        buffSize = core.getImageBufferSize() / (int) core.getBytesPerPixel();
        
        core.snapImage();
        switch (imageType) {
            case EightBit:
                System.arraycopy((byte[]) core.getImage(), 0, ((DataBufferByte) img_1.getRaster().getDataBuffer()).getData(), 0, buffSize);
            case SixteenBit:
                System.arraycopy((short[]) core.getImage(), 0, ((DataBufferUShort) img_1.getRaster().getDataBuffer()).getData(), 0, buffSize);
        }
        
        MoveStage(180 + currentAngle);
        
        core.snapImage();
        switch (imageType) {
            case EightBit:
                System.arraycopy((byte[]) core.getImage(), 0, ((DataBufferByte) img_2.getRaster().getDataBuffer()).getData(), 0, buffSize);
            case SixteenBit:
                System.arraycopy((short[]) core.getImage(), 0, ((DataBufferUShort) img_2.getRaster().getDataBuffer()).getData(), 0, buffSize);
        }
        
        MoveStage(currentAngle);
    }
    
    public void Shutdown() throws Exception {
        if (core != null) {
            core.unloadAllDevices();
            core = null;
        }
    }
}