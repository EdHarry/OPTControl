/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package my.OPTcontrol;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Image;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.geom.AffineTransform;
import java.awt.image.AffineTransformOp;
import java.awt.image.BufferedImage;
import java.awt.image.BufferedImageOp;
import java.awt.image.DataBufferByte;
import java.awt.image.DataBufferInt;
import java.awt.image.DataBufferUShort;
import java.awt.image.LookupOp;
import java.awt.image.ShortLookupTable;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.ByteBuffer;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Random;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author edwardharry
 */
public class OPTcontrolUI extends javax.swing.JFrame {

    /**
     *
     */
    public class TextAreaOutputStream extends OutputStream {

        private final javax.swing.JTextArea textArea;
        private final StringBuilder sb = new StringBuilder();
        private final String title;

        public TextAreaOutputStream(final javax.swing.JTextArea textArea, String title) {
            this.textArea = textArea;
            this.title = title;
            sb.append(title).append("> ");
        }

        @Override
        public void flush() {
        }

        @Override
        public void close() {
        }

        @Override
        public void write(int b) throws IOException {

            if (b == '\r') {
                return;
            }

            if (b == '\n') {
                final String text = sb.toString() + "\n";
                javax.swing.SwingUtilities.invokeLater(new Runnable() {

                    @Override
                    public void run() {
                        textArea.append(text);
                    }
                });
                sb.setLength(0);
                sb.append(title).append("> ");
                return;
            }

            sb.append((char) b);
        }
    }

    /**
     * Creates new form OPTcontrolUI
     */
    public OPTcontrolUI() {
        initComponents();
        SetupConsole();
        MiscSetup();
        SetupOPT();
    }

    private final String OMEextension = ".ome.tiff";
    private final String settingsExtension = ".OPTsettings";

    private void MiscSetup() {
        setMMPathFileChooser.setFileSelectionMode(javax.swing.JFileChooser.DIRECTORIES_ONLY);

        fullExperimentRadioButton.setSelected(true);

        autoCalcCheckBox.setSelected(false);
        isOverrideSelected = false;

        currentInterFrameAngle = interFrameAngleField.getText();
        currentTimeLapse = timeLapseField.getText();
        currentChannelAngle = interChannelAngleField.getText();
        currentNFrames = nFramesField.getText();

        loadSettingsFileChooser.addChoosableFileFilter(new javax.swing.filechooser.FileNameExtensionFilter("OPT settings", settingsExtension.substring(1)));
        loadSettingsFileChooser.setAcceptAllFileFilterUsed(false);

        UpdateEstimatedTotalTime();
        try {
            UpdatePathString();
        } catch (Exception ex) {
            Logger.getLogger(OPTcontrolUI.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    private void SetupOPT() {
        SetupOPT(false);
    }

    private void SetupOPT(boolean fullOverride) {
        try {
            if (!OPT.IsPathSet()) {
                System.out.println("Cannot setup microscope controler, micromanager path has not been set to a valid value.");
                return;
            }
        } catch (Exception ex) {
            Logger.getLogger(OPTcontrolUI.class.getName()).log(Level.SEVERE, null, ex);
        }
        try {
            if (!fullOverride && OPT.IsInitalised()) {
                return;
            }
        } catch (Exception ex) {
            Logger.getLogger(OPTcontrolUI.class.getName()).log(Level.SEVERE, null, ex);
        }
        try {
            OPT.Initalise();
            UpdateBinningBox();
            UpdateBitDepthBox();
            UpdateReadoutBox();
            UpdateExposureTimeBox();
            UpdateMicrostepBox();
            UpdateSpeedBox();
            UpdateAccBox();
            roi.Set(OPT.GetROI());
        } catch (Exception ex) {
            Logger.getLogger(OPTcontrolUI.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    private void SetupConsole() {
        TextAreaOutputStream taOutput = new TextAreaOutputStream(consoleTextArea, "Console");
        System.setOut(new PrintStream(taOutput));
        System.setErr(new PrintStream(taOutput));
    }

    private boolean CheckFileName() {
        return fileField.getText().endsWith(OMEextension);
    }

    private String[] MakeCommandArgs() {
        List<String> args = new ArrayList<>();

        // File name
        args.add("file");
        args.add(fileField.getText());

        // Time-lapse
        args.add("lapse");
        args.add(timeLapseField.getText());

        // Inter-frame angle
        args.add("angle");
        args.add(interFrameAngleField.getText());

        // Override
        args.add("override");
        if (isOverrideSelected) {
            args.add("true");
        } else {
            args.add("false");
        }

        if (!isOverrideSelected) {
            // interChannelAngle
            args.add("interChannelAngle");
            args.add(interChannelAngleField.getText());

            // nFrames
            args.add("nFrames");
            args.add(nFramesField.getText());
        }

        return args.toArray(new String[args.size()]);
    }

    private void StartImaging() {
        SetupOPT();
        if (!CheckFileName()) {
            imagingInfoLabel.setText("Cannot start imaging, file extension must be '.ome.tiff'");
            return;
        } else {
            imagingInfoLabel.setText("...");
        }

        startImagingButton.setEnabled(false);
        ActivatePanels(false);

        ImagingThread imThread = new ImagingThread();
        imThread.execute();
    }

    private void ActivatePanels(boolean state) {
        parametersPanel.setEnabled(state);
        filePanel.setEnabled(state);
        microManagerPanel.setEnabled(state);
        cameraPanel.setEnabled(state);
        stagePanel.setEnabled(state);
        saveSettingPanel.setEnabled(state);
    }

    private class ImagingThread extends javax.swing.SwingWorker<Void, Void> {

        @Override
        protected Void doInBackground() {
            try {
                System.out.println("Preparing to image...");

                switch (experimentType) {
                    case Full:
                        OPT_full = (optstandalone.OPTstandAlone) OPT;
                        OPT_full.InitaliseParameters(MakeCommandArgs());
                        OPT_full.InitaliseOME();
                        OPT_full.DoImaging();

                    case Simple:
                        OPT.InitaliseParameters(MakeCommandArgs());
                        OPT.InitaliseOME();
                        OPT.DoImaging();
                }

            } catch (Exception e) {
                System.out.println("Exception: " + e.getMessage() + "\nExiting now.");
            }
            startImagingButton.setEnabled(true);
            ActivatePanels(true);
            return null;
        }
    }

    /**
     * This method is called from within the constructor to initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is always
     * regenerated by the Form Editor.
     */
    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents()
    {

        saveOutputFileChooser = new javax.swing.JFileChooser();
        setMMPathFileChooser = new javax.swing.JFileChooser();
        experimentTypeButtonGroup = new javax.swing.ButtonGroup();
        saveSettingsFileChooser = new javax.swing.JFileChooser();
        loadSettingsFileChooser = new javax.swing.JFileChooser();
        parametersPanel = new javax.swing.JPanel();
        parametersSubPanel = new javax.swing.JPanel();
        nFramesField = new javax.swing.JTextField();
        nFramesLabel = new javax.swing.JLabel();
        interFrameAngleLabel = new javax.swing.JLabel();
        interFrameAngleField = new javax.swing.JTextField();
        interChannelAngleField = new javax.swing.JTextField();
        timeLapseField = new javax.swing.JTextField();
        interChannelAngleLabel = new javax.swing.JLabel();
        autoCalcLabel = new javax.swing.JLabel();
        autoCalcCheckBox = new javax.swing.JCheckBox();
        timeLapseLabel = new javax.swing.JLabel();
        experimentTypePanel = new javax.swing.JPanel();
        fullExperimentRadioButton = new javax.swing.JRadioButton();
        simpleExperimentRadioButton = new javax.swing.JRadioButton();
        estTimeActualLabel = new javax.swing.JLabel();
        estTimeLabel = new javax.swing.JLabel();
        filePanel = new javax.swing.JPanel();
        fileField = new javax.swing.JTextField();
        fileButton = new javax.swing.JButton();
        startImagingButton = new javax.swing.JButton();
        consolePane = new javax.swing.JScrollPane();
        consoleTextArea = new javax.swing.JTextArea();
        imagingInfoLabel = new javax.swing.JLabel();
        microManagerPanel = new javax.swing.JPanel();
        microManagerPathField = new javax.swing.JTextField();
        setMMPathButton = new javax.swing.JButton();
        cameraPanel = new javax.swing.JPanel();
        cameraSettingSubPanel = new javax.swing.JPanel();
        bitDepthBox = new javax.swing.JComboBox();
        pixelBinningLabel = new javax.swing.JLabel();
        exposureTimeLabel = new javax.swing.JLabel();
        exposureTimeField = new javax.swing.JTextField();
        bitDepthLabel = new javax.swing.JLabel();
        pixelBinningBox = new javax.swing.JComboBox();
        readoutRateLabel = new javax.swing.JLabel();
        readoutRateBox = new javax.swing.JComboBox();
        liveButton = new javax.swing.JButton();
        calibrateButton = new javax.swing.JButton();
        stagePanel = new javax.swing.JPanel();
        stageSettingsSubPanel = new javax.swing.JPanel();
        accField = new javax.swing.JTextField();
        microstepResLabel = new javax.swing.JLabel();
        speedLabel = new javax.swing.JLabel();
        microstepResBox = new javax.swing.JComboBox();
        accLabel = new javax.swing.JLabel();
        speedField = new javax.swing.JTextField();
        saveSettingPanel = new javax.swing.JPanel();
        saveSettingsButton = new javax.swing.JButton();
        loadSettingsButton = new javax.swing.JButton();
        saveOutputFileChooser.setSelectedFile(new File(saveOutputFileChooser.getCurrentDirectory(), "*.ome.tiff"));

        experimentTypeButtonGroup.add(fullExperimentRadioButton);
        experimentTypeButtonGroup.add(simpleExperimentRadioButton);

        setDefaultCloseOperation(javax.swing.WindowConstants.EXIT_ON_CLOSE);
        setPreferredSize(new java.awt.Dimension(860, 853));

        parametersPanel.setBorder(javax.swing.BorderFactory.createTitledBorder("Parameters"));

        nFramesField.setText("100");
        nFramesField.addFocusListener(new java.awt.event.FocusAdapter()
        {
            public void focusLost(java.awt.event.FocusEvent evt)
            {
                nFramesFieldFocusLost(evt);
            }
        });
        nFramesField.addActionListener(new java.awt.event.ActionListener()
        {
            public void actionPerformed(java.awt.event.ActionEvent evt)
            {
                nFramesFieldActionPerformed(evt);
            }
        });

        nFramesLabel.setText("Number of frames");

        interFrameAngleLabel.setText("Inter-frame step angle [degrees]");

        interFrameAngleField.setText("0.9");
        interFrameAngleField.addFocusListener(new java.awt.event.FocusAdapter()
        {
            public void focusLost(java.awt.event.FocusEvent evt)
            {
                interFrameAngleFieldFocusLost(evt);
            }
        });
        interFrameAngleField.addActionListener(new java.awt.event.ActionListener()
        {
            public void actionPerformed(java.awt.event.ActionEvent evt)
            {
                interFrameAngleFieldActionPerformed(evt);
            }
        });

        interChannelAngleField.setText("90");
        interChannelAngleField.addFocusListener(new java.awt.event.FocusAdapter()
        {
            public void focusLost(java.awt.event.FocusEvent evt)
            {
                interChannelAngleFieldFocusLost(evt);
            }
        });
        interChannelAngleField.addActionListener(new java.awt.event.ActionListener()
        {
            public void actionPerformed(java.awt.event.ActionEvent evt)
            {
                interChannelAngleFieldActionPerformed(evt);
            }
        });

        timeLapseField.setText("15");
        timeLapseField.addFocusListener(new java.awt.event.FocusAdapter()
        {
            public void focusLost(java.awt.event.FocusEvent evt)
            {
                timeLapseFieldFocusLost(evt);
            }
        });
        timeLapseField.addActionListener(new java.awt.event.ActionListener()
        {
            public void actionPerformed(java.awt.event.ActionEvent evt)
            {
                timeLapseFieldActionPerformed(evt);
            }
        });

        interChannelAngleLabel.setText("Inter-channel step angle [degrees]");

        autoCalcLabel.setText("Auto calculate these parameters for optimal 180 degree imaging?");

        autoCalcCheckBox.addActionListener(new java.awt.event.ActionListener()
        {
            public void actionPerformed(java.awt.event.ActionEvent evt)
            {
                autoCalcCheckBoxActionPerformed(evt);
            }
        });

        timeLapseLabel.setText("Time-lapse [seconds]");

        javax.swing.GroupLayout parametersSubPanelLayout = new javax.swing.GroupLayout(parametersSubPanel);
        parametersSubPanel.setLayout(parametersSubPanelLayout);
        parametersSubPanelLayout.setHorizontalGroup(
            parametersSubPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(parametersSubPanelLayout.createSequentialGroup()
                .addGap(6, 6, 6)
                .addGroup(parametersSubPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING)
                    .addGroup(parametersSubPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                        .addGroup(parametersSubPanelLayout.createSequentialGroup()
                            .addGap(194, 194, 194)
                            .addGroup(parametersSubPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING)
                                .addComponent(timeLapseLabel)
                                .addComponent(nFramesLabel)
                                .addComponent(interChannelAngleLabel)))
                        .addComponent(autoCalcLabel))
                    .addComponent(interFrameAngleLabel))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(parametersSubPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                    .addComponent(interFrameAngleField)
                    .addComponent(interChannelAngleField)
                    .addComponent(autoCalcCheckBox)
                    .addComponent(nFramesField)
                    .addComponent(timeLapseField, javax.swing.GroupLayout.PREFERRED_SIZE, 60, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addContainerGap())
        );
        parametersSubPanelLayout.setVerticalGroup(
            parametersSubPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(parametersSubPanelLayout.createSequentialGroup()
                .addContainerGap()
                .addGroup(parametersSubPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(interFrameAngleLabel)
                    .addComponent(interFrameAngleField, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(parametersSubPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(timeLapseLabel)
                    .addComponent(timeLapseField, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addGap(18, 18, 18)
                .addGroup(parametersSubPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING)
                    .addComponent(autoCalcCheckBox)
                    .addComponent(autoCalcLabel))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(parametersSubPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(interChannelAngleLabel)
                    .addComponent(interChannelAngleField, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(parametersSubPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(nFramesLabel)
                    .addComponent(nFramesField, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addContainerGap())
        );

        autoCalcCheckBox.getAccessibleContext().setAccessibleName("autoGenParam");

        experimentTypePanel.setBorder(javax.swing.BorderFactory.createTitledBorder("Experiment Type"));

        fullExperimentRadioButton.setText("OPT + cell tracking");
        fullExperimentRadioButton.addActionListener(new java.awt.event.ActionListener()
        {
            public void actionPerformed(java.awt.event.ActionEvent evt)
            {
                fullExperimentRadioButtonActionPerformed(evt);
            }
        });

        simpleExperimentRadioButton.setText("Simple OPT");
        simpleExperimentRadioButton.addActionListener(new java.awt.event.ActionListener()
        {
            public void actionPerformed(java.awt.event.ActionEvent evt)
            {
                simpleExperimentRadioButtonActionPerformed(evt);
            }
        });

        javax.swing.GroupLayout experimentTypePanelLayout = new javax.swing.GroupLayout(experimentTypePanel);
        experimentTypePanel.setLayout(experimentTypePanelLayout);
        experimentTypePanelLayout.setHorizontalGroup(
            experimentTypePanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(experimentTypePanelLayout.createSequentialGroup()
                .addContainerGap()
                .addGroup(experimentTypePanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(fullExperimentRadioButton)
                    .addComponent(simpleExperimentRadioButton))
                .addContainerGap())
        );
        experimentTypePanelLayout.setVerticalGroup(
            experimentTypePanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(experimentTypePanelLayout.createSequentialGroup()
                .addContainerGap()
                .addComponent(fullExperimentRadioButton)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(simpleExperimentRadioButton)
                .addContainerGap())
        );

        estTimeActualLabel.setFont(new java.awt.Font("Lucida Grande", 1, 15)); // NOI18N
        estTimeActualLabel.setText("...");

        estTimeLabel.setText("Est. total time (hh:mm:ss):");

        javax.swing.GroupLayout parametersPanelLayout = new javax.swing.GroupLayout(parametersPanel);
        parametersPanel.setLayout(parametersPanelLayout);
        parametersPanelLayout.setHorizontalGroup(
            parametersPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(parametersPanelLayout.createSequentialGroup()
                .addGroup(parametersPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(parametersPanelLayout.createSequentialGroup()
                        .addContainerGap()
                        .addComponent(experimentTypePanel, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                    .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, parametersPanelLayout.createSequentialGroup()
                        .addContainerGap(107, Short.MAX_VALUE)
                        .addComponent(estTimeLabel)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(estTimeActualLabel)
                        .addGap(32, 32, 32)))
                .addComponent(parametersSubPanel, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addGap(25, 25, 25))
        );
        parametersPanelLayout.setVerticalGroup(
            parametersPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(parametersSubPanel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
            .addGroup(parametersPanelLayout.createSequentialGroup()
                .addGap(14, 14, 14)
                .addComponent(experimentTypePanel, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addGroup(parametersPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(estTimeLabel)
                    .addComponent(estTimeActualLabel))
                .addGap(18, 18, 18))
        );

        filePanel.setBorder(javax.swing.BorderFactory.createTitledBorder("Image File"));

        fileField.setText("Full path and file name");
        fileField.addActionListener(new java.awt.event.ActionListener()
        {
            public void actionPerformed(java.awt.event.ActionEvent evt)
            {
                fileFieldActionPerformed(evt);
            }
        });

        fileButton.setText("Choose save location");
        fileButton.addMouseListener(new java.awt.event.MouseAdapter()
        {
            public void mouseClicked(java.awt.event.MouseEvent evt)
            {
                fileButtonMouseClicked(evt);
            }
        });
        fileButton.addActionListener(new java.awt.event.ActionListener()
        {
            public void actionPerformed(java.awt.event.ActionEvent evt)
            {
                fileButtonActionPerformed(evt);
            }
        });

        javax.swing.GroupLayout filePanelLayout = new javax.swing.GroupLayout(filePanel);
        filePanel.setLayout(filePanelLayout);
        filePanelLayout.setHorizontalGroup(
            filePanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, filePanelLayout.createSequentialGroup()
                .addComponent(fileButton)
                .addGap(18, 18, 18)
                .addComponent(fileField)
                .addContainerGap())
        );
        filePanelLayout.setVerticalGroup(
            filePanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, filePanelLayout.createSequentialGroup()
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addGroup(filePanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(fileField, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(fileButton))
                .addContainerGap())
        );

        startImagingButton.setText("Start Imaging");
        startImagingButton.addActionListener(new java.awt.event.ActionListener()
        {
            public void actionPerformed(java.awt.event.ActionEvent evt)
            {
                startImagingButtonActionPerformed(evt);
            }
        });

        consoleTextArea.setColumns(20);
        consoleTextArea.setRows(5);
        consolePane.setViewportView(consoleTextArea);

        imagingInfoLabel.setText("...");

        microManagerPanel.setBorder(javax.swing.BorderFactory.createTitledBorder("Micro-Manager Path"));

        microManagerPathField.setText("C:\\Program Files\\Micro-Manager-1.4\\");
            microManagerPathField.addFocusListener(new java.awt.event.FocusAdapter()
            {
                public void focusLost(java.awt.event.FocusEvent evt)
                {
                    microManagerPathFieldFocusLost(evt);
                }
            });
            microManagerPathField.addActionListener(new java.awt.event.ActionListener()
            {
                public void actionPerformed(java.awt.event.ActionEvent evt)
                {
                    microManagerPathFieldActionPerformed(evt);
                }
            });

            setMMPathButton.setText("Choose MM path");
            setMMPathButton.addMouseListener(new java.awt.event.MouseAdapter()
            {
                public void mouseClicked(java.awt.event.MouseEvent evt)
                {
                    setMMPathButtonMouseClicked(evt);
                }
            });
            setMMPathButton.addActionListener(new java.awt.event.ActionListener()
            {
                public void actionPerformed(java.awt.event.ActionEvent evt)
                {
                    setMMPathButtonActionPerformed(evt);
                }
            });

            javax.swing.GroupLayout microManagerPanelLayout = new javax.swing.GroupLayout(microManagerPanel);
            microManagerPanel.setLayout(microManagerPanelLayout);
            microManagerPanelLayout.setHorizontalGroup(
                microManagerPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                .addGroup(microManagerPanelLayout.createSequentialGroup()
                    .addComponent(setMMPathButton, javax.swing.GroupLayout.PREFERRED_SIZE, 151, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                    .addComponent(microManagerPathField)
                    .addContainerGap())
            );
            microManagerPanelLayout.setVerticalGroup(
                microManagerPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, microManagerPanelLayout.createSequentialGroup()
                    .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addGroup(microManagerPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                        .addComponent(microManagerPathField, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addComponent(setMMPathButton))
                    .addContainerGap())
            );

            cameraPanel.setBorder(javax.swing.BorderFactory.createTitledBorder("Camera Settings"));

            bitDepthBox.setModel(new javax.swing.DefaultComboBoxModel(new String[] { "Item 1", "Item 2", "Item 3", "Item 4" }));
            bitDepthBox.addActionListener(new java.awt.event.ActionListener()
            {
                public void actionPerformed(java.awt.event.ActionEvent evt)
                {
                    bitDepthBoxActionPerformed(evt);
                }
            });

            pixelBinningLabel.setText("Pixel binning");

            exposureTimeLabel.setText("Exposure time [ms]");

            exposureTimeField.addFocusListener(new java.awt.event.FocusAdapter()
            {
                public void focusLost(java.awt.event.FocusEvent evt)
                {
                    exposureTimeFieldFocusLost(evt);
                }
            });
            exposureTimeField.addActionListener(new java.awt.event.ActionListener()
            {
                public void actionPerformed(java.awt.event.ActionEvent evt)
                {
                    exposureTimeFieldActionPerformed(evt);
                }
            });

            bitDepthLabel.setText("Bit depth");

            pixelBinningBox.setModel(new javax.swing.DefaultComboBoxModel(new String[] { "Item 1", "Item 2", "Item 3", "Item 4" }));
            pixelBinningBox.addActionListener(new java.awt.event.ActionListener()
            {
                public void actionPerformed(java.awt.event.ActionEvent evt)
                {
                    pixelBinningBoxActionPerformed(evt);
                }
            });

            readoutRateLabel.setText("Readout rate");

            readoutRateBox.setModel(new javax.swing.DefaultComboBoxModel(new String[] { "Item 1", "Item 2", "Item 3", "Item 4" }));
            readoutRateBox.addActionListener(new java.awt.event.ActionListener()
            {
                public void actionPerformed(java.awt.event.ActionEvent evt)
                {
                    readoutRateBoxActionPerformed(evt);
                }
            });

            javax.swing.GroupLayout cameraSettingSubPanelLayout = new javax.swing.GroupLayout(cameraSettingSubPanel);
            cameraSettingSubPanel.setLayout(cameraSettingSubPanelLayout);
            cameraSettingSubPanelLayout.setHorizontalGroup(
                cameraSettingSubPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                .addGroup(cameraSettingSubPanelLayout.createSequentialGroup()
                    .addContainerGap()
                    .addGroup(cameraSettingSubPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING)
                        .addComponent(pixelBinningLabel)
                        .addComponent(exposureTimeLabel)
                        .addComponent(bitDepthLabel)
                        .addComponent(readoutRateLabel))
                    .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                    .addGroup(cameraSettingSubPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                        .addGroup(cameraSettingSubPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                            .addComponent(exposureTimeField)
                            .addComponent(pixelBinningBox, 0, 1, Short.MAX_VALUE)
                            .addComponent(bitDepthBox, javax.swing.GroupLayout.PREFERRED_SIZE, 84, javax.swing.GroupLayout.PREFERRED_SIZE))
                        .addComponent(readoutRateBox, javax.swing.GroupLayout.Alignment.TRAILING, javax.swing.GroupLayout.PREFERRED_SIZE, 84, javax.swing.GroupLayout.PREFERRED_SIZE))
                    .addContainerGap())
            );
            cameraSettingSubPanelLayout.setVerticalGroup(
                cameraSettingSubPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                .addGroup(cameraSettingSubPanelLayout.createSequentialGroup()
                    .addContainerGap()
                    .addGroup(cameraSettingSubPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                        .addComponent(exposureTimeLabel)
                        .addComponent(exposureTimeField, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                    .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                    .addGroup(cameraSettingSubPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                        .addComponent(pixelBinningLabel)
                        .addComponent(pixelBinningBox, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                    .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                    .addGroup(cameraSettingSubPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                        .addComponent(bitDepthBox, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addComponent(bitDepthLabel))
                    .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                    .addGroup(cameraSettingSubPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                        .addComponent(readoutRateBox, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addComponent(readoutRateLabel))
                    .addContainerGap())
            );

            liveButton.setText("Live / Set ROI");
            liveButton.addActionListener(new java.awt.event.ActionListener()
            {
                public void actionPerformed(java.awt.event.ActionEvent evt)
                {
                    liveButtonActionPerformed(evt);
                }
            });

            calibrateButton.setText("Calibrate");
            calibrateButton.addMouseListener(new java.awt.event.MouseAdapter()
            {
                public void mouseClicked(java.awt.event.MouseEvent evt)
                {
                    calibrateButtonMouseClicked(evt);
                }
            });
            calibrateButton.addActionListener(new java.awt.event.ActionListener()
            {
                public void actionPerformed(java.awt.event.ActionEvent evt)
                {
                    calibrateButtonActionPerformed(evt);
                }
            });

            javax.swing.GroupLayout cameraPanelLayout = new javax.swing.GroupLayout(cameraPanel);
            cameraPanel.setLayout(cameraPanelLayout);
            cameraPanelLayout.setHorizontalGroup(
                cameraPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                .addGroup(cameraPanelLayout.createSequentialGroup()
                    .addContainerGap()
                    .addGroup(cameraPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                        .addComponent(calibrateButton, javax.swing.GroupLayout.DEFAULT_SIZE, 160, Short.MAX_VALUE)
                        .addComponent(liveButton, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                    .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                    .addComponent(cameraSettingSubPanel, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addContainerGap())
            );
            cameraPanelLayout.setVerticalGroup(
                cameraPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                .addGroup(cameraPanelLayout.createSequentialGroup()
                    .addContainerGap()
                    .addGroup(cameraPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                        .addGroup(cameraPanelLayout.createSequentialGroup()
                            .addComponent(liveButton, javax.swing.GroupLayout.PREFERRED_SIZE, 64, javax.swing.GroupLayout.PREFERRED_SIZE)
                            .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                            .addComponent(calibrateButton, javax.swing.GroupLayout.PREFERRED_SIZE, 64, javax.swing.GroupLayout.PREFERRED_SIZE))
                        .addComponent(cameraSettingSubPanel, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                    .addContainerGap(27, Short.MAX_VALUE))
            );

            stagePanel.setBorder(javax.swing.BorderFactory.createTitledBorder("Stage Settings"));

            accField.addFocusListener(new java.awt.event.FocusAdapter()
            {
                public void focusLost(java.awt.event.FocusEvent evt)
                {
                    accFieldFocusLost(evt);
                }
            });
            accField.addActionListener(new java.awt.event.ActionListener()
            {
                public void actionPerformed(java.awt.event.ActionEvent evt)
                {
                    accFieldActionPerformed(evt);
                }
            });

            microstepResLabel.setText("Microstep resolution");

            speedLabel.setText("Speed [degrees/second]");

            microstepResBox.setModel(new javax.swing.DefaultComboBoxModel(new String[] { "Item 1", "Item 2", "Item 3", "Item 4" }));
            microstepResBox.addActionListener(new java.awt.event.ActionListener()
            {
                public void actionPerformed(java.awt.event.ActionEvent evt)
                {
                    microstepResBoxActionPerformed(evt);
                }
            });

            accLabel.setText("Acceleration [degrees/second^2]");

            speedField.addFocusListener(new java.awt.event.FocusAdapter()
            {
                public void focusLost(java.awt.event.FocusEvent evt)
                {
                    speedFieldFocusLost(evt);
                }
            });
            speedField.addActionListener(new java.awt.event.ActionListener()
            {
                public void actionPerformed(java.awt.event.ActionEvent evt)
                {
                    speedFieldActionPerformed(evt);
                }
            });

            javax.swing.GroupLayout stageSettingsSubPanelLayout = new javax.swing.GroupLayout(stageSettingsSubPanel);
            stageSettingsSubPanel.setLayout(stageSettingsSubPanelLayout);
            stageSettingsSubPanelLayout.setHorizontalGroup(
                stageSettingsSubPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                .addGroup(stageSettingsSubPanelLayout.createSequentialGroup()
                    .addGroup(stageSettingsSubPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                        .addGroup(stageSettingsSubPanelLayout.createSequentialGroup()
                            .addGap(20, 20, 20)
                            .addComponent(accLabel))
                        .addGroup(stageSettingsSubPanelLayout.createSequentialGroup()
                            .addGap(94, 94, 94)
                            .addComponent(microstepResLabel))
                        .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, stageSettingsSubPanelLayout.createSequentialGroup()
                            .addContainerGap()
                            .addComponent(speedLabel)))
                    .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                    .addGroup(stageSettingsSubPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                        .addComponent(accField)
                        .addComponent(speedField)
                        .addComponent(microstepResBox, 0, 1, Short.MAX_VALUE))
                    .addContainerGap())
            );
            stageSettingsSubPanelLayout.setVerticalGroup(
                stageSettingsSubPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                .addGroup(stageSettingsSubPanelLayout.createSequentialGroup()
                    .addContainerGap()
                    .addGroup(stageSettingsSubPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                        .addComponent(speedLabel)
                        .addComponent(speedField, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                    .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                    .addGroup(stageSettingsSubPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                        .addComponent(accField, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addComponent(accLabel))
                    .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                    .addGroup(stageSettingsSubPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                        .addComponent(microstepResBox, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addComponent(microstepResLabel))
                    .addContainerGap())
            );

            javax.swing.GroupLayout stagePanelLayout = new javax.swing.GroupLayout(stagePanel);
            stagePanel.setLayout(stagePanelLayout);
            stagePanelLayout.setHorizontalGroup(
                stagePanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                .addGroup(stagePanelLayout.createSequentialGroup()
                    .addContainerGap()
                    .addComponent(stageSettingsSubPanel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addContainerGap())
            );
            stagePanelLayout.setVerticalGroup(
                stagePanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                .addGroup(stagePanelLayout.createSequentialGroup()
                    .addGap(31, 31, 31)
                    .addComponent(stageSettingsSubPanel, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
            );

            saveSettingsButton.setText("Save Settings");
            saveSettingsButton.addMouseListener(new java.awt.event.MouseAdapter()
            {
                public void mouseClicked(java.awt.event.MouseEvent evt)
                {
                    saveSettingsButtonMouseClicked(evt);
                }
            });

            loadSettingsButton.setText("Load Settings");
            loadSettingsButton.addMouseListener(new java.awt.event.MouseAdapter()
            {
                public void mouseClicked(java.awt.event.MouseEvent evt)
                {
                    loadSettingsButtonMouseClicked(evt);
                }
            });

            javax.swing.GroupLayout saveSettingPanelLayout = new javax.swing.GroupLayout(saveSettingPanel);
            saveSettingPanel.setLayout(saveSettingPanelLayout);
            saveSettingPanelLayout.setHorizontalGroup(
                saveSettingPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                .addGroup(saveSettingPanelLayout.createSequentialGroup()
                    .addComponent(saveSettingsButton, javax.swing.GroupLayout.PREFERRED_SIZE, 134, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                    .addComponent(loadSettingsButton, javax.swing.GroupLayout.PREFERRED_SIZE, 134, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addContainerGap())
            );
            saveSettingPanelLayout.setVerticalGroup(
                saveSettingPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                .addComponent(saveSettingsButton, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addComponent(loadSettingsButton, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
            );

            javax.swing.GroupLayout layout = new javax.swing.GroupLayout(getContentPane());
            getContentPane().setLayout(layout);
            layout.setHorizontalGroup(
                layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                .addGroup(layout.createSequentialGroup()
                    .addContainerGap()
                    .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                        .addComponent(filePanel, javax.swing.GroupLayout.Alignment.TRAILING, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                        .addComponent(parametersPanel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                        .addGroup(layout.createSequentialGroup()
                            .addComponent(cameraPanel, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                            .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                            .addComponent(stagePanel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                        .addComponent(microManagerPanel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                        .addComponent(consolePane)
                        .addGroup(layout.createSequentialGroup()
                            .addComponent(startImagingButton, javax.swing.GroupLayout.PREFERRED_SIZE, 144, javax.swing.GroupLayout.PREFERRED_SIZE)
                            .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                            .addComponent(imagingInfoLabel, javax.swing.GroupLayout.PREFERRED_SIZE, 408, javax.swing.GroupLayout.PREFERRED_SIZE)
                            .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                            .addComponent(saveSettingPanel, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                            .addGap(0, 0, Short.MAX_VALUE)))
                    .addContainerGap())
            );
            layout.setVerticalGroup(
                layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                .addGroup(layout.createSequentialGroup()
                    .addContainerGap()
                    .addComponent(microManagerPanel, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                    .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                        .addComponent(cameraPanel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                        .addComponent(stagePanel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                    .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                    .addComponent(parametersPanel, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                    .addComponent(filePanel, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                    .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                        .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                            .addComponent(startImagingButton, javax.swing.GroupLayout.PREFERRED_SIZE, 47, javax.swing.GroupLayout.PREFERRED_SIZE)
                            .addComponent(imagingInfoLabel))
                        .addComponent(saveSettingPanel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                    .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                    .addComponent(consolePane, javax.swing.GroupLayout.DEFAULT_SIZE, 145, Short.MAX_VALUE)
                    .addContainerGap())
            );

            pack();
        }// </editor-fold>//GEN-END:initComponents

    private void autoCalcCheckBoxActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_autoCalcCheckBoxActionPerformed
        // TODO add your handling code here:
        boolean selected = ((javax.swing.AbstractButton) evt.getSource()).getModel().isSelected();
        interChannelAngleField.setEnabled(!selected);
        nFramesField.setEnabled(!selected);
        isOverrideSelected = selected;
        UpdateEstimatedTotalTime();
    }//GEN-LAST:event_autoCalcCheckBoxActionPerformed

    private void interFrameAngleFieldActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_interFrameAngleFieldActionPerformed
        // TODO add your handling code here:
    }//GEN-LAST:event_interFrameAngleFieldActionPerformed

    private void interChannelAngleFieldActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_interChannelAngleFieldActionPerformed
        // TODO add your handling code here:
    }//GEN-LAST:event_interChannelAngleFieldActionPerformed

    private void nFramesFieldActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_nFramesFieldActionPerformed
        // TODO add your handling code here:
    }//GEN-LAST:event_nFramesFieldActionPerformed

    private void timeLapseFieldActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_timeLapseFieldActionPerformed
        // TODO add your handling code here:
    }//GEN-LAST:event_timeLapseFieldActionPerformed

    private void fileFieldActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_fileFieldActionPerformed
        // TODO add your handling code here:
    }//GEN-LAST:event_fileFieldActionPerformed

    private void fileButtonMouseClicked(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_fileButtonMouseClicked
        // TODO add your handling code here:
        boolean loop = true;
        String fileAndPath = "";
        while (loop) {
            int ret = saveOutputFileChooser.showSaveDialog(OPTcontrolUI.this);
            if (ret == javax.swing.JFileChooser.APPROVE_OPTION) {
                try {
                    fileAndPath = AddFileExtension(saveOutputFileChooser.getSelectedFile().getCanonicalPath(), OMEextension);
                } catch (IOException ex) {
                    loop = false;
                    Logger.getLogger(OPTcontrolUI.class.getName()).log(Level.SEVERE, null, ex);
                }
                if (!fileAndPath.endsWith(OMEextension)) {
                    saveOutputFileChooser.setDialogTitle("Cannot set file name, file must have extension '.ome.tiff'");
                } else {
                    fileField.setText(fileAndPath);
                    loop = false;
                }
            } else {
                loop = false;
            }
        }
    }//GEN-LAST:event_fileButtonMouseClicked

    private String AddFileExtension(String fullPath, String ext) {
        String tmp = fullPath + ".q";
        while (!fullPath.equals(tmp)) {
            fullPath = tmp;
            tmp = RemoveExtension(fullPath);
        }
        //return fullPath + ext;
        return RemoveExtension(fullPath) + ext;
    }

    private String RemoveExtension(String s) { // from http://stackoverflow.com/questions/941272/how-do-i-trim-a-file-extension-from-a-string-in-java

        String separator = System.getProperty("file.separator");
        String filename;
        String filenameSub;
        String sSub;

        // Remove the path upto the filename.
        int lastSeparatorIndex = s.lastIndexOf(separator);
        if (lastSeparatorIndex == -1) {
            filename = s;
            sSub = "";
        } else {
            filename = s.substring(lastSeparatorIndex + 1);
            sSub = s.substring(0, lastSeparatorIndex + 1);
        }

        // Remove the extension.
        int extensionIndex = filename.lastIndexOf(".");
        if (extensionIndex == -1) {
            filenameSub = filename;
        } else {
            filenameSub = filename.substring(0, extensionIndex);
        }

        return sSub + filenameSub;
    }

    private void startImagingButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_startImagingButtonActionPerformed
        // TODO add your handling code here:
        StartImaging();
    }//GEN-LAST:event_startImagingButtonActionPerformed

    private void fileButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_fileButtonActionPerformed
        // TODO add your handling code here:
    }//GEN-LAST:event_fileButtonActionPerformed

    private void setMMPathButtonMouseClicked(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_setMMPathButtonMouseClicked
        // TODO add your handling code here:
        int ret = setMMPathFileChooser.showOpenDialog(OPTcontrolUI.this);
        if (ret == javax.swing.JFileChooser.APPROVE_OPTION) {
            try {
                microManagerPathField.setText(setMMPathFileChooser.getSelectedFile().getCanonicalPath());
                UpdatePathString();
            } catch (IOException ex) {
                Logger.getLogger(OPTcontrolUI.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
    }//GEN-LAST:event_setMMPathButtonMouseClicked

    private void UpdatePathString() {
        String mmPathString = microManagerPathField.getText();
        if (!mmPathString.endsWith(File.separator)) {
            mmPathString = mmPathString + File.separator;
            microManagerPathField.setText(mmPathString);
        }
        if (mmPathString.equals(lastPathString)) {
            return;
        }
        lastPathString = mmPathString;
        SetPath(mmPathString);
    }

    private void microManagerPathFieldFocusLost(java.awt.event.FocusEvent evt) {//GEN-FIRST:event_microManagerPathFieldFocusLost
        // TODO add your handling code here:
        UpdatePathString();
    }//GEN-LAST:event_microManagerPathFieldFocusLost

    private void simpleExperimentRadioButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_simpleExperimentRadioButtonActionPerformed
        // TODO add your handling code here:
        if (((javax.swing.AbstractButton) evt.getSource()).getModel().isSelected()) {
            SetToSimpleOPT();
            autoCalcCheckBox.setEnabled(false);
            interChannelAngleField.setEnabled(false);
            nFramesField.setEnabled(true);
        }
    }//GEN-LAST:event_simpleExperimentRadioButtonActionPerformed

    private void microManagerPathFieldActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_microManagerPathFieldActionPerformed
        // TODO add your handling code here:
    }//GEN-LAST:event_microManagerPathFieldActionPerformed

    private void speedFieldActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_speedFieldActionPerformed
        // TODO add your handling code here:
    }//GEN-LAST:event_speedFieldActionPerformed

    private void accFieldActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_accFieldActionPerformed
        // TODO add your handling code here:
    }//GEN-LAST:event_accFieldActionPerformed

    private void fullExperimentRadioButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_fullExperimentRadioButtonActionPerformed
        // TODO add your handling code here:
        if (((javax.swing.AbstractButton) evt.getSource()).getModel().isSelected()) {
            SetToOPTPlusCellTracking();
            autoCalcCheckBox.setEnabled(true);
            if (!isOverrideSelected) {
                interChannelAngleField.setEnabled(true);
            } else {
                nFramesField.setEnabled(false);
            }
        }
    }//GEN-LAST:event_fullExperimentRadioButtonActionPerformed

    private void setMMPathButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_setMMPathButtonActionPerformed
        // TODO add your handling code here:
    }//GEN-LAST:event_setMMPathButtonActionPerformed

    private void interFrameAngleFieldFocusLost(java.awt.event.FocusEvent evt) {//GEN-FIRST:event_interFrameAngleFieldFocusLost
        // TODO add your handling code here:
        try {
            Double.parseDouble(interFrameAngleField.getText());
            currentInterFrameAngle = interFrameAngleField.getText();
        } catch (Exception e) {
            interFrameAngleField.setText(currentInterFrameAngle);
            Logger.getLogger(OPTcontrolUI.class.getName()).log(Level.SEVERE, null, e);
        }
        UpdateEstimatedTotalTime();
    }//GEN-LAST:event_interFrameAngleFieldFocusLost

    private void timeLapseFieldFocusLost(java.awt.event.FocusEvent evt) {//GEN-FIRST:event_timeLapseFieldFocusLost
        // TODO add your handling code here:
        try {
            Double.parseDouble(timeLapseField.getText());
            currentTimeLapse = timeLapseField.getText();
        } catch (Exception e) {
            timeLapseField.setText(currentTimeLapse);
            Logger.getLogger(OPTcontrolUI.class.getName()).log(Level.SEVERE, null, e);
        }
        UpdateEstimatedTotalTime();
    }//GEN-LAST:event_timeLapseFieldFocusLost

    private void interChannelAngleFieldFocusLost(java.awt.event.FocusEvent evt) {//GEN-FIRST:event_interChannelAngleFieldFocusLost
        // TODO add your handling code here:
        try {
            Double.parseDouble(interChannelAngleField.getText());
            currentChannelAngle = interChannelAngleField.getText();
        } catch (Exception e) {
            interChannelAngleField.setText(currentChannelAngle);
            Logger.getLogger(OPTcontrolUI.class.getName()).log(Level.SEVERE, null, e);
        }
    }//GEN-LAST:event_interChannelAngleFieldFocusLost

    private void nFramesFieldFocusLost(java.awt.event.FocusEvent evt) {//GEN-FIRST:event_nFramesFieldFocusLost
        // TODO add your handling code here:
        try {
            Integer.parseInt(nFramesField.getText());
            currentNFrames = nFramesField.getText();
        } catch (Exception e) {
            nFramesField.setText(currentChannelAngle);
            Logger.getLogger(OPTcontrolUI.class.getName()).log(Level.SEVERE, null, e);
        }
        UpdateEstimatedTotalTime();
    }//GEN-LAST:event_nFramesFieldFocusLost

    private void pixelBinningBoxActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_pixelBinningBoxActionPerformed
        System.out.println("event trigger...");
        try {
            // TODO add your handling code here:
            OPT.SetDeviceProperty("camera", "Binning", pixelBinningBox.getSelectedItem().toString());
        } catch (Exception ex) {
            Logger.getLogger(OPTcontrolUI.class.getName()).log(Level.SEVERE, null, ex);
        }
    }//GEN-LAST:event_pixelBinningBoxActionPerformed

    private void exposureTimeFieldActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_exposureTimeFieldActionPerformed
        // TODO add your handling code here:
    }//GEN-LAST:event_exposureTimeFieldActionPerformed

    private void exposureTimeFieldFocusLost(java.awt.event.FocusEvent evt) {//GEN-FIRST:event_exposureTimeFieldFocusLost
        // TODO add your handling code here:
        try {
            Double.parseDouble(exposureTimeField.getText());
            currentExp = exposureTimeField.getText();
        } catch (Exception e) {
            exposureTimeField.setText(currentExp);
            Logger.getLogger(OPTcontrolUI.class.getName()).log(Level.SEVERE, null, e);
        }
        try {
            OPT.SetExposure(exposureTimeField.getText());
        } catch (Exception ex) {
            Logger.getLogger(OPTcontrolUI.class.getName()).log(Level.SEVERE, null, ex);
        }
    }//GEN-LAST:event_exposureTimeFieldFocusLost

    private void bitDepthBoxActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_bitDepthBoxActionPerformed
        // TODO add your handling code here:
        try {
            // TODO add your handling code here:
            OPT.SetDeviceProperty("camera", "Sensitivity/DynamicRange", bitDepthBox.getSelectedItem().toString());
        } catch (Exception ex) {
            Logger.getLogger(OPTcontrolUI.class.getName()).log(Level.SEVERE, null, ex);
        }
    }//GEN-LAST:event_bitDepthBoxActionPerformed

    private void readoutRateBoxActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_readoutRateBoxActionPerformed
        // TODO add your handling code here:
        // TODO add your handling code here:
        try {
            // TODO add your handling code here:
            OPT.SetDeviceProperty("camera", "PixelReadoutRate", readoutRateBox.getSelectedItem().toString());
        } catch (Exception ex) {
            Logger.getLogger(OPTcontrolUI.class.getName()).log(Level.SEVERE, null, ex);
        }
    }//GEN-LAST:event_readoutRateBoxActionPerformed

    private void microstepResBoxActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_microstepResBoxActionPerformed
        try {
            // TODO add your handling code here:
            OPT.SetDeviceProperty("stage", "Microstep Size Resolution", Integer.parseInt(microstepResBox.getSelectedItem().toString()));
        } catch (Exception ex) {
            Logger.getLogger(OPTcontrolUI.class.getName()).log(Level.SEVERE, null, ex);
        }
    }//GEN-LAST:event_microstepResBoxActionPerformed

    private void speedFieldFocusLost(java.awt.event.FocusEvent evt) {//GEN-FIRST:event_speedFieldFocusLost
        // TODO add your handling code here:
        try {
            Double.parseDouble(speedField.getText());
            currentSpeed = speedField.getText();
        } catch (Exception e) {
            speedField.setText(currentSpeed);
            Logger.getLogger(OPTcontrolUI.class.getName()).log(Level.SEVERE, null, e);
        }
        try {
            OPT.SetDeviceProperty("stage", "Speed [deg/s]", Double.parseDouble(speedField.getText()));
        } catch (Exception ex) {
            Logger.getLogger(OPTcontrolUI.class.getName()).log(Level.SEVERE, null, ex);
        }
    }//GEN-LAST:event_speedFieldFocusLost

    private void accFieldFocusLost(java.awt.event.FocusEvent evt) {//GEN-FIRST:event_accFieldFocusLost
        // TODO add your handling code here:
        try {
            Double.parseDouble(accField.getText());
            currentAcc = accField.getText();
        } catch (Exception e) {
            accField.setText(currentAcc);
            Logger.getLogger(OPTcontrolUI.class.getName()).log(Level.SEVERE, null, e);
        }
        try {
            OPT.SetDeviceProperty("stage", "Acceleration [deg/s^2]", Double.parseDouble(accField.getText()));
        } catch (Exception ex) {
            Logger.getLogger(OPTcontrolUI.class.getName()).log(Level.SEVERE, null, ex);
        }
    }//GEN-LAST:event_accFieldFocusLost

    private void liveButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_liveButtonActionPerformed
        // TODO add your handling code here:
        MakeLiveWindow();
    }//GEN-LAST:event_liveButtonActionPerformed

    private void saveSettingsButtonMouseClicked(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_saveSettingsButtonMouseClicked
        // TODO add your handling code here:
        SettingsSaver();
    }//GEN-LAST:event_saveSettingsButtonMouseClicked

    private void loadSettingsButtonMouseClicked(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_loadSettingsButtonMouseClicked
        // TODO add your handling code here:
        SettingLoader();
    }//GEN-LAST:event_loadSettingsButtonMouseClicked

    private void calibrateButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_calibrateButtonActionPerformed
        // TODO add your handling code here:
    }//GEN-LAST:event_calibrateButtonActionPerformed

    private void calibrateButtonMouseClicked(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_calibrateButtonMouseClicked
        // TODO add your handling code here:
        RunCalibration();
    }//GEN-LAST:event_calibrateButtonMouseClicked

    private void MakeLiveWindow() {
        int[] imgSize = new int[2];
        if (roi.IsSet()) {
            imgSize[0] = (int) roi.GetROI().width;
            imgSize[1] = (int) roi.GetROI().height;
        } else {
            imgSize[0] = 500;
            imgSize[1] = 500;
        }
//        try {
//            imgSize = OPT.GetImageWidthAndHeight();
//        } catch (Exception ex) {
//            Logger.getLogger(OPTcontrolUI.class.getName()).log(Level.SEVERE, null, ex);
//            return;
//        }

//        imageType = optstandalone.OPTstandAlone.ImageType.Unassigned;
//        try {
//            imageType = OPT.GetImageType();
//        } catch (Exception ex) {
//            Logger.getLogger(OPTcontrolUI.class.getName()).log(Level.SEVERE, null, ex);
//        }     
//        switch (imageType) {
//            case EightBit:
//               LiveImg = new BufferedImage(imgSize[0], imgSize[1], BufferedImage.TYPE_BYTE_GRAY);
//            case SixteenBit:
//                LiveImg = new BufferedImage(imgSize[0], imgSize[1], BufferedImage.TYPE_USHORT_GRAY);
//            case Unassigned:
//                return;          
//        }
        LiveImg = new BufferedImage(imgSize[0], imgSize[1], BufferedImage.TYPE_USHORT_GRAY);
        totalLiveImageSize = imgSize[0] * imgSize[1];
        b = new byte[totalLiveImageSize * 2];
        s = new short[b.length / 2];

        LiveImgBoard = new ImageDrawingBoard(imgSize, LiveImg);
        LiveImgBoard.setVisible(true);

        updateLiveImg.set(true);
        LiveWindowThread tmp = new LiveWindowThread();
        tmp.execute();
    }

    optstandalone.OPTstandAlone.ImageType imageType = optstandalone.OPTstandAlone.ImageType.Unassigned;
    ImageDrawingBoard LiveImgBoard;
    int totalLiveImageSize;
    byte[] b;
    short[] s;
    BufferedImage LiveImg;
    final AtomicBoolean updateLiveImg = new AtomicBoolean(false);

    private class LiveWindowThread extends javax.swing.SwingWorker<Void, Void> {

        @Override
        protected Void doInBackground() {
            while (updateLiveImg.get()) {
//                switch (imageType) {
//                    case EightBit: {
//                        try {
//                            System.arraycopy((byte[]) OPT.GetSnapImage(), 0, ((DataBufferByte) LiveImg.getRaster().getDataBuffer()).getData(), 0, totalLiveImageSize);
//                        } catch (Exception ex) {
//                            Logger.getLogger(OPTcontrolUI.class.getName()).log(Level.SEVERE, null, ex);
//                        }
//                    }
//                    case SixteenBit: {
//                        try {
//                            System.arraycopy((short[]) OPT.GetSnapImage(), 0, ((DataBufferUShort) LiveImg.getRaster().getDataBuffer()).getData(), 0, totalLiveImageSize);
//                        } catch (Exception ex) {
//                            Logger.getLogger(OPTcontrolUI.class.getName()).log(Level.SEVERE, null, ex);
//                        }
//                    }
//                }

                new Random().nextBytes(b);
                ByteBuffer.wrap(b).asShortBuffer().get(s);
                System.arraycopy(s, 0, ((DataBufferUShort) LiveImg.getRaster().getDataBuffer()).getData(), 0, totalLiveImageSize);

                LiveImgBoard.ReRender();
            }
            return null;
        }
    }

    private void SetPath(String s) {
        try {
            if (OPT.IsInitalised()) {
                return;
            }
        } catch (Exception ex) {
            Logger.getLogger(OPTcontrolUI.class.getName()).log(Level.SEVERE, null, ex);
        }
        try {
            OPT.SetPath(s);
        } catch (Exception ex) {
            Logger.getLogger(OPTcontrolUI.class.getName()).log(Level.SEVERE, null, ex);
        }
        System.out.println("Micro-Manager path set");
        SetupOPT(true);
    }

    /**
     * @param args the command line arguments
     */
    public static void main(String args[]) {
        /* Set the Nimbus look and feel */
        //<editor-fold defaultstate="collapsed" desc=" Look and feel setting code (optional) ">
        /* If Nimbus (introduced in Java SE 6) is not available, stay with the default look and feel.
         * For details see http://download.oracle.com/javase/tutorial/uiswing/lookandfeel/plaf.html 
         */
        try {
            for (javax.swing.UIManager.LookAndFeelInfo info : javax.swing.UIManager.getInstalledLookAndFeels()) {
                if ("Nimbus".equals(info.getName())) {
                    javax.swing.UIManager.setLookAndFeel(info.getClassName());
                    break;
                }
            }
        } catch (ClassNotFoundException | InstantiationException | IllegalAccessException | javax.swing.UnsupportedLookAndFeelException ex) {
            java.util.logging.Logger.getLogger(OPTcontrolUI.class.getName()).log(java.util.logging.Level.SEVERE, null, ex);
        }
        //</editor-fold>

        //</editor-fold>

        /* Create and display the form */
        java.awt.EventQueue.invokeLater(new Runnable() {

            @Override
            public void run() {
                new OPTcontrolUI().setVisible(true);
            }
        });
    }

    private void SetToOPTPlusCellTracking() {
        experimentType = ExperimentType.Full;
    }

    private void SetToSimpleOPT() {
        experimentType = ExperimentType.Simple;
    }

    private double EstimateTotalExperimentTime() {
        int nFrames;
        if (isOverrideSelected) {
            nFrames = ((int) Math.ceil(90 / Double.parseDouble(interFrameAngleField.getText()))) + 1;
        } else {
            nFrames = Integer.parseInt(nFramesField.getText());
        }
        return Double.parseDouble(timeLapseField.getText()) * (double) nFrames;
    }

    private void UpdateEstimatedTotalTime() {
        estTimeActualLabel.setText(new SimpleDateFormat("HH:mm:ss").format(new Date((long) (1000 * (Math.round(EstimateTotalExperimentTime() - 3600))))));
    }

    private String[] GetCameraBitDepthOptions() {
        try {
            return OPT.GetAvailablePropertyValues("camera", "Sensitivity/DynamicRange");
        } catch (Exception ex) {
            Logger.getLogger(OPTcontrolUI.class.getName()).log(Level.SEVERE, null, ex);
        }
        return null;
    }

    private String[] GetCameraBinningOptions() {
        try {
            return OPT.GetAvailablePropertyValues("camera", "Binning");
        } catch (Exception ex) {
            Logger.getLogger(OPTcontrolUI.class.getName()).log(Level.SEVERE, null, ex);
        }
        return null;
    }

    private String[] GetCameraReadoutOptions() {
        try {
            return OPT.GetAvailablePropertyValues("camera", "PixelReadoutRate");
        } catch (Exception ex) {
            Logger.getLogger(OPTcontrolUI.class.getName()).log(Level.SEVERE, null, ex);
        }
        return null;
    }

    private String[] GetMicrostepResolutionOptions() {
        try {
            return OPT.GetAvailablePropertyValues("stage", "Microstep Size Resolution");
        } catch (Exception ex) {
            Logger.getLogger(OPTcontrolUI.class.getName()).log(Level.SEVERE, null, ex);
        }
        return null;
    }

    private void UpdateBinningBox() {
        try {
            if (!OPT.IsInitalised()) {
                return;
            }
        } catch (Exception ex) {
            Logger.getLogger(OPTcontrolUI.class.getName()).log(Level.SEVERE, null, ex);
            return;
        }
        pixelBinningBox.removeAllItems();
        String[] values = GetCameraBinningOptions();
        String currentValue = null;
        try {
            currentValue = OPT.GetDeviceProperty("camera", "Binning");
        } catch (Exception ex) {
            Logger.getLogger(OPTcontrolUI.class.getName()).log(Level.SEVERE, null, ex);
        }
        for (String value : values) {
            if (!value.equals(currentValue)) {
                pixelBinningBox.addItem(MakeObj(value));
            }
        }
        if (currentValue != null) {
            pixelBinningBox.setEditable(true);
            pixelBinningBox.setSelectedItem(MakeObj(currentValue));
            pixelBinningBox.setEditable(false);
        }
    }

    private void UpdateBitDepthBox() {
        try {
            if (!OPT.IsInitalised()) {
                return;
            }
        } catch (Exception ex) {
            Logger.getLogger(OPTcontrolUI.class.getName()).log(Level.SEVERE, null, ex);
            return;
        }
        bitDepthBox.removeAllItems();
        String[] values = GetCameraBitDepthOptions();
        String currentValue = null;
        try {
            currentValue = OPT.GetDeviceProperty("camera", "Sensitivity/DynamicRange");
        } catch (Exception ex) {
            Logger.getLogger(OPTcontrolUI.class.getName()).log(Level.SEVERE, null, ex);
        }
        for (String value : values) {
            if (!value.equals(currentValue)) {
                bitDepthBox.addItem(MakeObj(value));
            }
        }
        if (currentValue != null) {
            bitDepthBox.setEditable(true);
            bitDepthBox.setSelectedItem(MakeObj(currentValue));
            bitDepthBox.setEditable(false);
        }
    }

    private void UpdateReadoutBox() {
        try {
            if (!OPT.IsInitalised()) {
                return;
            }
        } catch (Exception ex) {
            Logger.getLogger(OPTcontrolUI.class.getName()).log(Level.SEVERE, null, ex);
            return;
        }
        readoutRateBox.removeAllItems();
        String[] values = GetCameraReadoutOptions();
        String currentValue = null;
        try {
            currentValue = OPT.GetDeviceProperty("camera", "PixelReadoutRate");
        } catch (Exception ex) {
            Logger.getLogger(OPTcontrolUI.class.getName()).log(Level.SEVERE, null, ex);
        }
        for (String value : values) {
            if (!value.equals(currentValue)) {
                readoutRateBox.addItem(MakeObj(value));
            }
        }
        if (currentValue != null) {
            readoutRateBox.setEditable(true);
            readoutRateBox.setSelectedItem(MakeObj(currentValue));
            readoutRateBox.setEditable(false);
        }
    }

    private void UpdateMicrostepBox() {
        try {
            if (!OPT.IsInitalised()) {
                return;
            }
        } catch (Exception ex) {
            Logger.getLogger(OPTcontrolUI.class.getName()).log(Level.SEVERE, null, ex);
            return;
        }
        microstepResBox.removeAllItems();
        String[] values = GetMicrostepResolutionOptions();
        String currentValue = null;
        try {
            currentValue = OPT.GetDeviceProperty("stage", "Microstep Size Resolution");
        } catch (Exception ex) {
            Logger.getLogger(OPTcontrolUI.class.getName()).log(Level.SEVERE, null, ex);
        }
        for (String value : values) {
            if (!value.equals(currentValue)) {
                microstepResBox.addItem(MakeObj(value));
            }
        }
        if (currentValue != null) {
            microstepResBox.setEditable(true);
            microstepResBox.setSelectedItem(MakeObj(currentValue));
            microstepResBox.setEditable(false);
        }
    }

    private Object MakeObj(final String item) {
        return new Object() {
            @Override
            public String toString() {
                return item;
            }
        };
    }

    private void UpdateExposureTimeBox() {
        try {
            if (!OPT.IsInitalised()) {
                return;
            }
        } catch (Exception ex) {
            Logger.getLogger(OPTcontrolUI.class.getName()).log(Level.SEVERE, null, ex);
            return;
        }
        try {
            exposureTimeField.setText(OPT.GetExposure());
        } catch (Exception ex) {
            Logger.getLogger(OPTcontrolUI.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    private void UpdateSpeedBox() {
        try {
            if (!OPT.IsInitalised()) {
                return;
            }
        } catch (Exception ex) {
            Logger.getLogger(OPTcontrolUI.class.getName()).log(Level.SEVERE, null, ex);
            return;
        }
        try {
            speedField.setText(OPT.GetDeviceProperty("stage", "Speed [deg/s]"));
        } catch (Exception ex) {
            Logger.getLogger(OPTcontrolUI.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    private void UpdateAccBox() {
        try {
            if (!OPT.IsInitalised()) {
                return;
            }
        } catch (Exception ex) {
            Logger.getLogger(OPTcontrolUI.class.getName()).log(Level.SEVERE, null, ex);
            return;
        }
        try {
            accField.setText(OPT.GetDeviceProperty("stage", "Acceleration [deg/s^2]"));
        } catch (Exception ex) {
            Logger.getLogger(OPTcontrolUI.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JTextField accField;
    private javax.swing.JLabel accLabel;
    private javax.swing.JCheckBox autoCalcCheckBox;
    private javax.swing.JLabel autoCalcLabel;
    private javax.swing.JComboBox bitDepthBox;
    private javax.swing.JLabel bitDepthLabel;
    private javax.swing.JButton calibrateButton;
    private javax.swing.JPanel cameraPanel;
    private javax.swing.JPanel cameraSettingSubPanel;
    private javax.swing.JScrollPane consolePane;
    private javax.swing.JTextArea consoleTextArea;
    private javax.swing.JLabel estTimeActualLabel;
    private javax.swing.JLabel estTimeLabel;
    private javax.swing.ButtonGroup experimentTypeButtonGroup;
    private javax.swing.JPanel experimentTypePanel;
    private javax.swing.JTextField exposureTimeField;
    private javax.swing.JLabel exposureTimeLabel;
    private javax.swing.JButton fileButton;
    private javax.swing.JTextField fileField;
    private javax.swing.JPanel filePanel;
    private javax.swing.JRadioButton fullExperimentRadioButton;
    private javax.swing.JLabel imagingInfoLabel;
    private javax.swing.JTextField interChannelAngleField;
    private javax.swing.JLabel interChannelAngleLabel;
    private javax.swing.JTextField interFrameAngleField;
    private javax.swing.JLabel interFrameAngleLabel;
    private javax.swing.JButton liveButton;
    private javax.swing.JButton loadSettingsButton;
    private javax.swing.JFileChooser loadSettingsFileChooser;
    private javax.swing.JPanel microManagerPanel;
    private javax.swing.JTextField microManagerPathField;
    private javax.swing.JComboBox microstepResBox;
    private javax.swing.JLabel microstepResLabel;
    private javax.swing.JTextField nFramesField;
    private javax.swing.JLabel nFramesLabel;
    private javax.swing.JPanel parametersPanel;
    private javax.swing.JPanel parametersSubPanel;
    private javax.swing.JComboBox pixelBinningBox;
    private javax.swing.JLabel pixelBinningLabel;
    private javax.swing.JComboBox readoutRateBox;
    private javax.swing.JLabel readoutRateLabel;
    private javax.swing.JFileChooser saveOutputFileChooser;
    private javax.swing.JPanel saveSettingPanel;
    private javax.swing.JButton saveSettingsButton;
    private javax.swing.JFileChooser saveSettingsFileChooser;
    private javax.swing.JButton setMMPathButton;
    private javax.swing.JFileChooser setMMPathFileChooser;
    private javax.swing.JRadioButton simpleExperimentRadioButton;
    private javax.swing.JTextField speedField;
    private javax.swing.JLabel speedLabel;
    private javax.swing.JPanel stagePanel;
    private javax.swing.JPanel stageSettingsSubPanel;
    private javax.swing.JButton startImagingButton;
    private javax.swing.JTextField timeLapseField;
    private javax.swing.JLabel timeLapseLabel;
    // End of variables declaration//GEN-END:variables

    private boolean isOverrideSelected = true;
    private optstandalone.OPTsimple OPT = new optstandalone.OPTsimple();
    private optstandalone.OPTstandAlone OPT_full = null;
    private String lastPathString = "";

    enum ExperimentType {

        Full, Simple
    }

    ExperimentType experimentType = ExperimentType.Full;

    String currentInterFrameAngle;
    String currentTimeLapse;
    String currentChannelAngle;
    String currentNFrames;
    String currentExp;
    String currentSpeed;
    String currentAcc;

    ROI roi = new ROI();

    enum ROISet {

        Unset, Set
    }

    private class ROI {

        java.awt.geom.Rectangle2D.Float rec;
        ROISet roiSet = ROISet.Unset;
        float x, y, width, height;

        private ROI() {
            rec = null;
            x = 0;
            y = 0;
            width = 0;
            height = 0;
        }

        private ROI(java.awt.geom.Rectangle2D.Float rec) {
            this.rec = rec;
            roiSet = ROISet.Set;
            x = rec.x;
            y = rec.y;
            width = rec.width;
            height = rec.height;
        }

        public void Unset() {
            roiSet = ROISet.Unset;
        }

        public void Set(java.awt.geom.Rectangle2D.Float rec) {
            boolean currentlySet = roiSet == ROISet.Set;
            float xToAdd = 0;
            float yToAdd = 0;
            if (rec == null) {
                roiSet = ROISet.Unset;
                return;
            }
            if (currentlySet) {
                xToAdd = x;
                yToAdd = y;
            }
            this.rec = rec;
            roiSet = ROISet.Set;
            x = xToAdd + rec.x;
            y = yToAdd + rec.y;
            width = rec.width;
            height = rec.height;
        }

        public java.awt.geom.Rectangle2D.Float GetROI() {
            switch (roiSet) {
                case Unset:
                    return null;
                case Set:
                    return new java.awt.geom.Rectangle2D.Float(x, y, width, height);
            }
            return null;
        }

        public boolean IsSet() {
            return roiSet == ROISet.Set;
        }
    }

    private class ImageDrawingBoard extends javax.swing.JFrame {

        PaintSurface paintSurface;

        int extraHeight = 70;

        javax.swing.JButton setButton = new javax.swing.JButton();
        javax.swing.JButton resetButton = new javax.swing.JButton();

        private ImageDrawingBoard(int[] imageSize, BufferedImage img) {
            OPTcontrolUI.this.setEnabled(false);

            this.setSize(imageSize[0], imageSize[1] + extraHeight);
            this.setResizable(false);

            paintSurface = new PaintSurface(img, new Dimension(imageSize[0], imageSize[1]));
            this.add(paintSurface, java.awt.BorderLayout.CENTER);

            setButton.setText("Set ROI");
            setButton.setPreferredSize(new Dimension(imageSize[0] / 3, Math.round(0.8f * (float) extraHeight)));
            setButton.setBackground(java.awt.Color.green);
            resetButton.setText("Reset ROI");
            resetButton.setPreferredSize(new Dimension(imageSize[0] / 3, Math.round(0.8f * (float) extraHeight)));
            resetButton.setBackground(java.awt.Color.red);

            javax.swing.JPanel buttonPanel = new javax.swing.JPanel();
            buttonPanel.setPreferredSize(new Dimension(imageSize[0], Math.round(0.9f * (float) extraHeight)));
            buttonPanel.add(setButton, java.awt.BorderLayout.WEST);
            buttonPanel.add(resetButton, java.awt.BorderLayout.EAST);

            this.add(buttonPanel, java.awt.BorderLayout.PAGE_END);

            setButton.addMouseListener(new java.awt.event.MouseAdapter() {
                @Override
                public void mouseClicked(java.awt.event.MouseEvent evt) {
                    SetROIButtonClicked(evt);
                }
            });

            resetButton.addMouseListener(new java.awt.event.MouseAdapter() {
                @Override
                public void mouseClicked(java.awt.event.MouseEvent evt) {
                    ResetROIButtonClicked(evt);
                }
            });

            this.setVisible(false);

            this.addWindowListener(new WindowAdapter() {
                @Override
                public void windowClosing(WindowEvent e) {
                    updateLiveImg.set(false);
                    OPTcontrolUI.this.setEnabled(true);
                }
            });
        }

        public void ReRender() {
            paintSurface.repaint();
        }

        public void SetROIButtonClicked(java.awt.event.MouseEvent evt) {
            roi.Set(paintSurface.GetROI());
            this.dispatchEvent(new WindowEvent(this, WindowEvent.WINDOW_CLOSING));
            MakeLiveWindow();
        }

        public void ResetROIButtonClicked(java.awt.event.MouseEvent evt) {
            roi.Unset();
            this.dispatchEvent(new WindowEvent(this, WindowEvent.WINDOW_CLOSING));
            MakeLiveWindow();
        }

        private class PaintSurface extends javax.swing.JComponent {

            java.awt.Shape shape;

            java.awt.Point startDrag, endDrag;

            BufferedImage img;

            Dimension dim;

            private PaintSurface(BufferedImage img, Dimension dim) {

                this.setDoubleBuffered(true);

                this.img = img;

                this.dim = dim;

                this.addMouseListener(new java.awt.event.MouseAdapter() {

                    @Override
                    public void mousePressed(java.awt.event.MouseEvent e) {
                        startDrag = new java.awt.Point(e.getX(), e.getY());
                        endDrag = startDrag;
                        repaint();
                    }

                    @Override
                    public void mouseReleased(java.awt.event.MouseEvent e) {
                        shape = MakeRectangle(startDrag.x, startDrag.y, e.getX(), e.getY());
                        startDrag = null;
                        endDrag = null;
                        repaint();
                    }
                });

                this.addMouseMotionListener(new java.awt.event.MouseMotionAdapter() {
                    @Override
                    public void mouseDragged(java.awt.event.MouseEvent e) {
                        endDrag = new java.awt.Point(e.getX(), e.getY());
                        repaint();
                    }
                });
            }

            private void PaintBackground(java.awt.Graphics2D g2) {
                g2.drawImage(img, new AffineTransform(), null);
            }

            @Override
            public void paint(java.awt.Graphics g) {
                java.awt.Graphics2D g2 = (java.awt.Graphics2D) g;
                g2.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING, java.awt.RenderingHints.VALUE_ANTIALIAS_ON);
                PaintBackground(g2);

                g2.setStroke(new java.awt.BasicStroke(4));
                g2.setComposite(java.awt.AlphaComposite.getInstance(java.awt.AlphaComposite.SRC_OVER, 0.30f));

                if (shape != null) {
                    g2.setPaint(java.awt.Color.BLACK);
                    g2.draw(shape);
                    g2.setPaint(java.awt.Color.GREEN);
                    g2.fill(shape);
                }

                if (startDrag != null && endDrag != null) {
                    g2.setPaint(java.awt.Color.LIGHT_GRAY);
                    java.awt.Shape r = MakeRectangle(startDrag.x, startDrag.y, endDrag.x, endDrag.y);
                    g2.draw(r);
                }
            }

            private java.awt.geom.Rectangle2D.Float MakeRectangle(int x1, int y1, int x2, int y2) {
                return new java.awt.geom.Rectangle2D.Float(Math.max(Math.min(x1, x2), 0), Math.max(Math.min(y1, y2), 0), Math.abs(Math.max(Math.min(x1, dim.width), 0) - Math.max(Math.min(x2, dim.width), 0)), Math.abs(Math.max(Math.min(y1, dim.height), 0) - Math.max(Math.min(y2, dim.height), 0)));
            }

            public java.awt.geom.Rectangle2D.Float GetROI() {
                if (shape != null) {
                    return (java.awt.geom.Rectangle2D.Float) shape;
                }
                return null;
            }
        }
    }

    public class SaveSettings {

        String mMPath, exp, speed, acc, saveFile, frameAngle, timeLapse, channelAngle, nFrames;
        String pixBin, bitDepth, readoutRate, MRes;
        ExperimentType exType;
        java.awt.geom.Rectangle2D.Float rOI;
        boolean autoCalc;

        private SaveSettings(OPTSettingsSaver oPTSettingsSaver) {
            this.mMPath = oPTSettingsSaver.GetMMPath();
            this.exp = oPTSettingsSaver.GetExp();
            this.speed = oPTSettingsSaver.GetSpeed();
            this.acc = oPTSettingsSaver.GetAcc();
            this.saveFile = oPTSettingsSaver.GetSaveFile();
            this.frameAngle = oPTSettingsSaver.GetFrameAngle();
            this.timeLapse = oPTSettingsSaver.GetTimeLapse();
            this.channelAngle = oPTSettingsSaver.GetChannelAngle();
            this.nFrames = oPTSettingsSaver.GetNFrames();
            this.pixBin = oPTSettingsSaver.GetPixelBin();
            this.bitDepth = oPTSettingsSaver.GetBitDepth();
            this.readoutRate = oPTSettingsSaver.GetReadoutRate();
            this.MRes = oPTSettingsSaver.GetMicroStepRes();
            this.exType = oPTSettingsSaver.GetExpierimentType();
            this.rOI = oPTSettingsSaver.GetROI();
            this.autoCalc = oPTSettingsSaver.GetIsOverride();
        }

        private SaveSettings() {
            this.mMPath = microManagerPathField.getText();
            this.exp = exposureTimeField.getText();
            this.speed = speedField.getText();
            this.acc = accField.getText();
            this.saveFile = fileField.getText();
            this.frameAngle = interFrameAngleField.getText();
            this.timeLapse = timeLapseField.getText();
            this.channelAngle = interChannelAngleField.getText();
            this.nFrames = nFramesField.getText();

            this.pixBin = pixelBinningBox.getSelectedItem().toString();
            this.bitDepth = bitDepthBox.getSelectedItem().toString();
            this.readoutRate = readoutRateBox.getSelectedItem().toString();
            this.MRes = microstepResBox.getSelectedItem().toString();

            this.exType = experimentType;
            this.rOI = roi.GetROI();
            this.autoCalc = isOverrideSelected;
        }

        public void LoadSettings() {
            microManagerPathField.setText(this.mMPath);
            microManagerPathFieldFocusLost(null);

            exposureTimeField.setText(this.exp);
            exposureTimeFieldFocusLost(null);

            speedField.setText(this.speed);
            speedFieldFocusLost(null);

            accField.setText(this.acc);
            accFieldFocusLost(null);

            fileField.setText(this.saveFile);

            interFrameAngleField.setText(this.frameAngle);
            interFrameAngleFieldFocusLost(null);

            timeLapseField.setText(this.timeLapse);
            timeLapseFieldFocusLost(null);

            interChannelAngleField.setText(this.channelAngle);
            interChannelAngleFieldFocusLost(null);

            nFramesField.setText(this.nFrames);
            nFramesFieldFocusLost(null);

            pixelBinningBox.setSelectedItem(MakeObj(this.pixBin));
            pixelBinningBoxActionPerformed(null);

            bitDepthBox.setSelectedItem(MakeObj(this.bitDepth));
            bitDepthBoxActionPerformed(null);

            readoutRateBox.setSelectedItem(MakeObj(this.readoutRate));
            readoutRateBoxActionPerformed(null);

            microstepResBox.setSelectedItem(MakeObj(this.MRes));
            microstepResBoxActionPerformed(null);

            if (this.exType == ExperimentType.Full && !fullExperimentRadioButton.isSelected()) {
                fullExperimentRadioButton.doClick();
            } else if (this.exType == ExperimentType.Simple && !simpleExperimentRadioButton.isSelected()) {
                simpleExperimentRadioButton.doClick();
            }

            roi = new ROI(this.rOI);

            isOverrideSelected = this.autoCalc;
            if (isOverrideSelected && !autoCalcCheckBox.isSelected()) {
                autoCalcCheckBox.doClick();
            } else if (!isOverrideSelected && autoCalcCheckBox.isSelected()) {
                autoCalcCheckBox.doClick();
            }
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

        public ExperimentType GetExpierimentType() {
            return this.exType;
        }

        public java.awt.geom.Rectangle2D.Float GetROI() {
            return this.rOI;
        }

        public boolean GetIsOverride() {
            return this.autoCalc;
        }
    }

    private void SettingsSaver() {
        String fileAndPath = null;
        SaveSettings saveSettings;
        OPTSettingsSaver oPTSettingsSaver;
        FileOutputStream fout;
        ObjectOutputStream oos;

        int ret = saveSettingsFileChooser.showSaveDialog(OPTcontrolUI.this);
        if (ret == javax.swing.JFileChooser.APPROVE_OPTION) {
            try {
                fileAndPath = AddFileExtension(saveSettingsFileChooser.getSelectedFile().getCanonicalPath(), settingsExtension);
            } catch (IOException ex) {
                Logger.getLogger(OPTcontrolUI.class.getName()).log(Level.SEVERE, null, ex);
            }
        }

        if (fileAndPath != null) {
            try {
                saveSettings = new SaveSettings();
                oPTSettingsSaver = new OPTSettingsSaver(saveSettings);
                fout = new FileOutputStream(fileAndPath);
                oos = new ObjectOutputStream(fout);
                oos.writeObject(oPTSettingsSaver);
                oos.close();
                fout.close();
            } catch (FileNotFoundException ex) {
                Logger.getLogger(OPTcontrolUI.class.getName()).log(Level.SEVERE, null, ex);
            } catch (IOException ex) {
                Logger.getLogger(OPTcontrolUI.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
    }

    private void SettingLoader() {
        String fileAndPath = null;
        SaveSettings saveSettings = null;
        OPTSettingsSaver oPTSettingsSaver;
        FileInputStream fin;
        ObjectInputStream ois;

        int ret = loadSettingsFileChooser.showOpenDialog(OPTcontrolUI.this);
        if (ret == javax.swing.JFileChooser.APPROVE_OPTION) {
            try {
                fileAndPath = loadSettingsFileChooser.getSelectedFile().getCanonicalPath();
            } catch (IOException ex) {
                Logger.getLogger(OPTcontrolUI.class.getName()).log(Level.SEVERE, null, ex);
            }
        }

        if (fileAndPath != null) {
            try {
                fin = new FileInputStream(fileAndPath);
                ois = new ObjectInputStream(fin);
                oPTSettingsSaver = (OPTSettingsSaver) ois.readObject();
                saveSettings = new SaveSettings(oPTSettingsSaver);
                ois.close();
                fin.close();
            } catch (FileNotFoundException ex) {
                Logger.getLogger(OPTcontrolUI.class.getName()).log(Level.SEVERE, null, ex);
            } catch (IOException ex) {
                Logger.getLogger(OPTcontrolUI.class.getName()).log(Level.SEVERE, null, ex);
            } catch (ClassNotFoundException ex) {
                Logger.getLogger(OPTcontrolUI.class.getName()).log(Level.SEVERE, null, ex);
            }
        }

        if (saveSettings != null) {
            saveSettings.LoadSettings();
        }
    }

    private final AtomicBoolean runCalibration = new AtomicBoolean(false);
    BufferedImage calImg_1 = null, calImg_2 = null;
    Calibration calibration;

    private void RunCalibration() {
//        int[] imgSize;
//        try {
//            imgSize = OPT.GetImageWidthAndHeight();
//        } catch (Exception ex) {
//            Logger.getLogger(OPTcontrolUI.class.getName()).log(Level.SEVERE, null, ex);
//            return;
//        }
//
//        imageType = optstandalone.OPTstandAlone.ImageType.Unassigned;
//        try {
//            imageType = OPT.GetImageType();
//        } catch (Exception ex) {
//            Logger.getLogger(OPTcontrolUI.class.getName()).log(Level.SEVERE, null, ex);
//        }
//        switch (imageType) {
//            case EightBit:
//                calImg_1 = new BufferedImage(imgSize[0], imgSize[1], BufferedImage.TYPE_BYTE_GRAY);
//                calImg_2 = new BufferedImage(imgSize[0], imgSize[1], BufferedImage.TYPE_BYTE_GRAY);
//            case SixteenBit:
//                calImg_1 = new BufferedImage(imgSize[0], imgSize[1], BufferedImage.TYPE_USHORT_GRAY);
//                calImg_2 = new BufferedImage(imgSize[0], imgSize[1], BufferedImage.TYPE_USHORT_GRAY);
//            case Unassigned:
//                return;
//        }
//
//        if (calImg_1 == null || calImg_2 == null) {
//            return;
//        }
        int[] imgSize = {500, 500};
        totalLiveImageSize = imgSize[0] * imgSize[1];
        b = new byte[totalLiveImageSize * 2];
        s = new short[b.length / 2];
        calImg_1 = new BufferedImage(imgSize[0], imgSize[1], BufferedImage.TYPE_USHORT_GRAY);
        calImg_2 = new BufferedImage(imgSize[0], imgSize[1], BufferedImage.TYPE_USHORT_GRAY);

        runCalibration.set(true);
        calibration = new Calibration(calImg_1, calImg_2, Color.magenta, Color.cyan, 0.6);

        CalibrationThread calibrationThread = new CalibrationThread();
        calibrationThread.execute();
    }

    private class CalibrationThread extends javax.swing.SwingWorker<Void, Void> {

        @Override
        protected Void doInBackground() {
            while (runCalibration.get()) {
//          try {
//                OPT.RunCalibration(calImg_1, calImg_2);
//            } catch (Exception ex) {
//                Logger.getLogger(OPTcontrolUI.class.getName()).log(Level.SEVERE, null, ex);
//            }
//            calibration.ReRender();

                new Random().nextBytes(b);
                ByteBuffer.wrap(b).asShortBuffer().get(s);
                System.arraycopy(s, 0, ((DataBufferUShort) calImg_1.getRaster().getDataBuffer()).getData(), 0, totalLiveImageSize);

                new Random().nextBytes(b);
                ByteBuffer.wrap(b).asShortBuffer().get(s);
                System.arraycopy(s, 0, ((DataBufferUShort) calImg_2.getRaster().getDataBuffer()).getData(), 0, totalLiveImageSize);

                calibration.ReRender();
            }
            calibration = null;
            return null;
        }
    }

    private class Calibration {

        BufferedImage img_1, img_2, mixedImage;
        BufferedImageOp colorisedFilter_1, colorisedFilter_2, imageReflector;
        double mixRatio, inverseMixRatio;
        ImageWindow imageWindow;
        int width, height;

        private Calibration(BufferedImage img_1, BufferedImage img_2, Color col_1, Color col_2, double mixRatio) {
            this.img_1 = img_1;
            this.img_2 = img_2;
            this.width = this.img_1.getWidth();
            this.height = this.img_1.getHeight();
            this.colorisedFilter_1 = CreateColoriseOp((short) col_1.getRed(), (short) col_1.getGreen(), (short) col_1.getBlue());
            this.colorisedFilter_2 = CreateColoriseOp((short) col_2.getRed(), (short) col_2.getGreen(), (short) col_2.getBlue());
            this.imageReflector = ReflectImage();
            this.mixRatio = mixRatio;
            this.inverseMixRatio = 1.0 - this.mixRatio;
            this.mixedImage = new BufferedImage(this.width, this.height, BufferedImage.TYPE_INT_RGB);
            this.imageWindow = new ImageWindow(this.mixedImage);
        }

        public void ReRender() {
            BlendImages(this.colorisedFilter_1.filter(GrayScale2RGB(this.img_1), null), this.colorisedFilter_2.filter(this.imageReflector.filter(GrayScale2RGB(this.img_2), null), null), this.mixedImage);
            this.imageWindow.ReRender();
        }

        private AffineTransformOp ReflectImage() {
            AffineTransform affineTransform = AffineTransform.getScaleInstance(-1, 1);
            affineTransform.translate(-this.width, 0);
            return new AffineTransformOp(affineTransform, AffineTransformOp.TYPE_NEAREST_NEIGHBOR);
        }

        private BufferedImage GrayScale2RGB(BufferedImage bufferedImage) {
            BufferedImage rgbBufferedImage = new BufferedImage(this.width, this.height, BufferedImage.TYPE_INT_ARGB);
            Graphics rgbGraphics = rgbBufferedImage.getGraphics();
            rgbGraphics.drawImage(bufferedImage, 0, 0, null);
            rgbGraphics.dispose();
            return rgbBufferedImage;
        }

        private LookupOp CreateColoriseOp(short R1, short G1, short B1) {
            short[] alpha = new short[256];
            short[] red = new short[256];
            short[] green = new short[256];
            short[] blue = new short[256];

            // 0.3*R + 0.59*G + 0.11*B
            for (short i = 0; i < 256; i++) {
                alpha[i] = i;
                red[i] = (short) ((R1 + (i * 0.3)) / 2);
                green[i] = (short) ((G1 + (i * 0.59)) / 2);
                blue[i] = (short) ((B1 + (i * 0.11)) / 2);
            }

            return new LookupOp(new ShortLookupTable(0, new short[][]{red, green, blue, alpha}), null);
        }

        private void BlendImages(BufferedImage bi1, BufferedImage bi2, BufferedImage bi3) { // From: http://www.informit.com/articles/article.aspx?p=1245201

            int[] rgbim1 = new int[this.width];
            int[] rgbim2 = new int[this.width];
            int[] rgbim3 = new int[this.width];

            for (int row = 0; row < this.height; row++) {
                bi1.getRGB(0, row, this.width, 1, rgbim1, 0, this.width);
                bi2.getRGB(0, row, this.width, 1, rgbim2, 0, this.width);

                for (int col = 0; col < this.width; col++) {
                    int rgb1 = rgbim1[col];
                    int r1 = (rgb1 >> 16) & 255;
                    int g1 = (rgb1 >> 8) & 255;
                    int b1 = rgb1 & 255;

                    int rgb2 = rgbim2[col];
                    int r2 = (rgb2 >> 16) & 255;
                    int g2 = (rgb2 >> 8) & 255;
                    int b2 = rgb2 & 255;

                    int r3 = (int) ((r1 * this.mixRatio) + (r2 * this.inverseMixRatio));
                    int g3 = (int) ((g1 * this.mixRatio) + (g2 * this.inverseMixRatio));
                    int b3 = (int) ((b1 * this.mixRatio) + (b2 * this.inverseMixRatio));
                    rgbim3[col] = (r3 << 16) | (g3 << 8) | b3;
                }

                bi3.setRGB(0, row, this.width, 1, rgbim3, 0, this.width);
            }
        }

        private class ImageWindow extends javax.swing.JFrame {

            PaintSurface paintSurface;

            private ImageWindow(BufferedImage im) {
                OPTcontrolUI.this.setEnabled(false);
                this.setSize(im.getWidth(), im.getHeight());
                this.setVisible(true);
                this.paintSurface = new PaintSurface(im);
                this.add(this.paintSurface, java.awt.BorderLayout.CENTER);
                this.addWindowListener(new WindowAdapter() {
                    @Override
                    public void windowClosing(WindowEvent e) {
                        runCalibration.set(false);
                        OPTcontrolUI.this.setEnabled(true);
                    }
                });
            }

            public void ReRender() {
                this.paintSurface.repaint();
            }

            private class PaintSurface extends javax.swing.JComponent {

                BufferedImage im;

                private PaintSurface(BufferedImage im) {
                    this.im = im;
                    this.setDoubleBuffered(true);
                }

                @Override
                public void paint(java.awt.Graphics g) {
                    java.awt.Graphics2D g2 = (java.awt.Graphics2D) g;
                    g2.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING, java.awt.RenderingHints.VALUE_ANTIALIAS_ON);
                    g2.drawImage(this.im, new AffineTransform(), null);
                }
            }
        }
    }
}
