/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.scandit.datacapture.matrixscansimplesample;

import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import com.scandit.datacapture.barcode.data.Symbology;
import com.scandit.datacapture.barcode.tracking.capture.BarcodeTracking;
import com.scandit.datacapture.barcode.tracking.capture.BarcodeTrackingListener;
import com.scandit.datacapture.barcode.tracking.capture.BarcodeTrackingSession;
import com.scandit.datacapture.barcode.tracking.capture.BarcodeTrackingSettings;
import com.scandit.datacapture.barcode.tracking.data.TrackedBarcode;
import com.scandit.datacapture.barcode.tracking.ui.armanager.ARCell;
import com.scandit.datacapture.barcode.tracking.ui.armanager.ARManager;
import com.scandit.datacapture.barcode.tracking.ui.armanager.ARView;
import com.scandit.datacapture.barcode.tracking.ui.armanager.BarcodeAreaRange;
import com.scandit.datacapture.barcode.tracking.ui.armanager.RowStyle;
import com.scandit.datacapture.barcode.tracking.ui.overlay.BarcodeTrackingAdvancedOverlay;
import com.scandit.datacapture.barcode.tracking.ui.overlay.BarcodeTrackingAdvancedOverlayListener;
import com.scandit.datacapture.barcode.tracking.ui.overlay.BarcodeTrackingBasicOverlay;
import com.scandit.datacapture.barcode.tracking.ui.overlay.BarcodeTrackingBasicOverlayStyle;
import com.scandit.datacapture.core.capture.DataCaptureContext;
import com.scandit.datacapture.core.common.geometry.Anchor;
import com.scandit.datacapture.core.common.geometry.FloatWithUnit;
import com.scandit.datacapture.core.common.geometry.MeasureUnit;
import com.scandit.datacapture.core.common.geometry.PointWithUnit;
import com.scandit.datacapture.core.data.FrameData;
import com.scandit.datacapture.core.source.Camera;
import com.scandit.datacapture.core.source.CameraSettings;
import com.scandit.datacapture.core.source.FrameSourceState;
import com.scandit.datacapture.core.source.VideoResolution;
import com.scandit.datacapture.core.ui.DataCaptureView;
import com.scandit.datacapture.matrixscansimplesample.data.ScanResult;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.w3c.dom.Text;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.regex.Pattern;

public class MatrixScanActivity extends CameraPermissionActivity
        implements BarcodeTrackingListener {

    // Enter your Scandit License key here.
    // Your Scandit License key is available via your Scandit SDK web account.
    public static final String SCANDIT_LICENSE_KEY = "APPLY FOR THE KEY AT https://ssl.scandit.com/dashboard/sign-up?p=test";
    public static final int REQUEST_CODE_SCAN_RESULTS = 1;

    private Camera camera;
    private BarcodeTracking barcodeTracking;
    private DataCaptureContext dataCaptureContext;
    private ARManager arManager;
    private BarcodeTrackingAdvancedOverlay overlay;

    private final HashSet<ScanResult> scanResults = new HashSet<>();
    private final HashMap<String,Map> scannedBarcodesData = new HashMap<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_matrix_scan);

        // Initialize and start the barcode recognition.
        initialize();

        Button doneButton = findViewById(R.id.done_button);
        doneButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                synchronized (scanResults) {
                    // Show new screen displaying a list of all barcodes that have been scanned.
                    Intent intent = ResultsActivity.getIntent(MatrixScanActivity.this, scanResults);
                    startActivityForResult(intent, REQUEST_CODE_SCAN_RESULTS);
                }
            }
        });
    }

    private void initialize() {
        // Create data capture context using your license key.
        dataCaptureContext = DataCaptureContext.forLicenseKey(SCANDIT_LICENSE_KEY);

        // Use the recommended camera settings for the BarcodeTracking mode.
        CameraSettings cameraSettings = BarcodeTracking.createRecommendedCameraSettings();
        // Adjust camera settings - set Full HD resolution.
        cameraSettings.setPreferredResolution(VideoResolution.FULL_HD);
        // Use the default camera and set it as the frame source of the context.
        // The camera is off by default and must be turned on to start streaming frames to the data
        // capture context for recognition.
        // See resumeFrameSource and pauseFrameSource below.
        camera = Camera.getDefaultCamera(cameraSettings);
        if (camera != null) {
            dataCaptureContext.setFrameSource(camera);
        } else {
            throw new IllegalStateException("Sample depends on a camera, which failed to initialize.");
        }

        // The barcode tracking process is configured through barcode tracking settings
        // which are then applied to the barcode tracking instance that manages barcode tracking.
        BarcodeTrackingSettings barcodeTrackingSettings = new BarcodeTrackingSettings();

        // The settings instance initially has all types of barcodes (symbologies) disabled.
        // For the purpose of this sample we enable a very generous set of symbologies.
        // In your own app ensure that you only enable the symbologies that your app requires
        // as every additional enabled symbology has an impact on processing times.
        HashSet<Symbology> symbologies = new HashSet<>();
        symbologies.add(Symbology.EAN13_UPCA);
        symbologies.add(Symbology.EAN8);
        symbologies.add(Symbology.UPCE);
        symbologies.add(Symbology.CODE39);
        symbologies.add(Symbology.CODE128);

        barcodeTrackingSettings.enableSymbologies(symbologies);

        // Create barcode tracking and attach to context.
        barcodeTracking =
                BarcodeTracking.forDataCaptureContext(dataCaptureContext, barcodeTrackingSettings);

        // Register self as a listener to get informed of tracked barcodes.
        //barcodeTracking.addListener(this);

        // To visualize the on-going barcode tracking process on screen, setup a data capture view
        // that renders the camera preview. The view must be connected to the data capture context.
        DataCaptureView dataCaptureView = DataCaptureView.newInstance(this, dataCaptureContext);

        // Add a barcode tracking overlay to the data capture view to render the tracked barcodes on
        // top of the video preview. This is optional, but recommended for better visual feedback.
//        BarcodeTrackingBasicOverlay.newInstance(
//                barcodeTracking,
//                dataCaptureView,
//                BarcodeTrackingBasicOverlayStyle.FRAME
//        );

        overlay=BarcodeTrackingAdvancedOverlay.newInstance(barcodeTracking,dataCaptureView);
//        overlay.setListener(this);
        barcodeTracking.addListener(this);
        // Add the DataCaptureView to the container.
        FrameLayout container = findViewById(R.id.data_capture_view_container);
        container.addView(dataCaptureView);

        initARManager(this, dataCaptureView);
    }

    private void initARManager(Context context, DataCaptureView dataCaptureView) {
        arManager=ARManager.newInstance(context, dataCaptureView);

        int[] largeViewRows= {1,2,3};
        ARView largeTemplate = arManager.createView(this,largeViewRows);
        largeTemplate.setAlpha(0.8f);
        largeTemplate.setHeaderRowCount(1);
        largeTemplate.setFooterRowCount(1);
        largeTemplate.setFooterRowStyle(Color.BLACK,Color.YELLOW,0.9f);
        largeTemplate.setHeaderRowStyle(Color.BLACK,Color.GREEN,0.9f);
        largeTemplate.setRowsStyle(Color.WHITE,Color.BLACK,0.8f);
        largeTemplate.setCellName(0,0,"title");
        largeTemplate.getCell("title").getView().setPadding(5,10,5,10);
        largeTemplate.setCellName(1,0,"dst_label");
        largeTemplate.getCell("dst_label").getView().setPadding(10,5,20,5);
        largeTemplate.setCellName(1,1,"dst_km");
        largeTemplate.setCellName(2,0,"price");
        largeTemplate.setCellName(2,1,"qty");
        largeTemplate.getCell(2,1).getView().setBackgroundColor(Color.BLACK);
        ((TextView)largeTemplate.getCell(2,1).getView()).setTextColor(Color.WHITE);
        largeTemplate.setCellName(2,2,"weight");
        largeTemplate.setCornersRadius(15,15,15,15);

        int[] medViewRows= {2,2};
        ARView medTemplate = arManager.createView(this,medViewRows);
        medTemplate.setAlpha(0.8f);
        medTemplate.setRowsStyle(Color.WHITE,Color.BLACK,0.8f);
        medTemplate.setCellName(0,0,"dst_km");
        medTemplate.setCellName(0,1,"price");
        medTemplate.setCellName(1,0,"qty");
        medTemplate.setCellName(1,1,"weight");
        medTemplate.setCornerRadii(new float[] {10.0f,10.0f,10.0f,10.0f,10.0f,10.0f,10.0f,10.0f});

        int[] smallViewRows= {2};
        ARView smallTemplate = arManager.createView(this,smallViewRows);
        smallTemplate.setAlpha(0.8f);
        smallTemplate.setRowsStyle(Color.WHITE,Color.BLACK,0.8f);
        smallTemplate.setCellName(0,0,"dst_km");
        smallTemplate.setCellName(0,1,"weight");
        smallTemplate.setCornerRadii(new float[] {10.0f,10.0f,10.0f,10.0f,10.0f,10.0f,10.0f,10.0f});

        arManager.setViewLayoutForRange(new BarcodeAreaRange(0.0f,0.002f),smallTemplate);
        arManager.setViewLayoutForRange(new BarcodeAreaRange(0.002f,0.01f),medTemplate);
        arManager.setViewLayoutForRange(new BarcodeAreaRange(0.01f,1.0f),largeTemplate);


    }


    @Override
    protected void onPause() {
        pauseFrameSource();
        super.onPause();
    }

    private void pauseFrameSource() {
        // Switch camera off to stop streaming frames.
        // The camera is stopped asynchronously and will take some time to completely turn off.
        // Until it is completely stopped, it is still possible to receive further results, hence
        // it's a good idea to first disable barcode tracking as well.
        barcodeTracking.setEnabled(false);
        camera.switchToDesiredState(FrameSourceState.OFF, null);
    }

    @Override
    protected void onResume() {
        super.onResume();

        // Check for camera permission and request it, if it hasn't yet been granted.
        // Once we have the permission the onCameraPermissionGranted() method will be called.
        requestCameraPermission();
    }

    @Override
    public void onCameraPermissionGranted() {
        resumeFrameSource();
    }

    private void resumeFrameSource() {
        // Switch camera on to start streaming frames.
        // The camera is started asynchronously and will take some time to completely turn on.
        barcodeTracking.setEnabled(true);
        camera.switchToDesiredState(FrameSourceState.ON, null);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQUEST_CODE_SCAN_RESULTS
                && resultCode == ResultsActivity.RESULT_CODE_CLEAN) {
            synchronized (scanResults) {
                scanResults.clear();
            }
        }
        super.onActivityResult(requestCode, resultCode, data);
    }


    @Override
    protected void onDestroy() {
        dataCaptureContext.removeMode(barcodeTracking);
        super.onDestroy();
    }

//    @NotNull
//    @Override
//    public Anchor anchorForTrackedBarcode(@NotNull BarcodeTrackingAdvancedOverlay barcodeTrackingAdvancedOverlay, @NotNull TrackedBarcode trackedBarcode) {
//        return Anchor.TOP_CENTER;
//    }
//
//    @NotNull
//    @Override
//    public PointWithUnit offsetForTrackedBarcode(@NotNull BarcodeTrackingAdvancedOverlay barcodeTrackingAdvancedOverlay, @NotNull TrackedBarcode trackedBarcode, @NotNull View view) {
//        return new PointWithUnit(
//                new FloatWithUnit(0.0f, MeasureUnit.DIP),
//                new FloatWithUnit(-10.0f, MeasureUnit.DIP));
//    }
//
//    @Nullable
//    @Override
//    public View viewForTrackedBarcode(@NotNull BarcodeTrackingAdvancedOverlay barcodeTrackingAdvancedOverlay, @NotNull TrackedBarcode trackedBarcode) {
////        scanResults.add(new ScanResult(trackedBarcode.getBarcode()));
////
////        Map<String,String> barcodeValuesMap=getTestDataForBarcode(trackedBarcode);
////        ARView arView=arManager.getARViewFor(trackedBarcode,barcodeValuesMap);
////        return arView;
//    }

    private Map<String,String> getTestDataForBarcode(TrackedBarcode trackedBarcode){
        HashMap<String,String> barcodeValuesMap=new HashMap<>();
        Double magicNumber=20.0*Math.random();

        barcodeValuesMap.put("title",trackedBarcode.getBarcode().getData());
        barcodeValuesMap.put("dst_label","Distance:");
        barcodeValuesMap.put("dst_km",String.format("%.2f km", magicNumber));
        barcodeValuesMap.put("price",String.format("$%.2f",magicNumber));
        barcodeValuesMap.put("qty",String.valueOf(Math.round(2.0*magicNumber)));
        barcodeValuesMap.put("weight",String.format("%.2f",10*magicNumber));

        return barcodeValuesMap;
    }

    @Override
    public void onSessionUpdated(
            @NonNull BarcodeTracking mode,
            @NonNull final BarcodeTrackingSession session,
            @NonNull FrameData data
    ) {
        // Be careful, this function is not invoked on the main thread!
        runOnUiThread(new Runnable() {
            @Override
            public void run() {

                for (TrackedBarcode trackedBarcode : session.getAddedTrackedBarcodes()) {
                    //we use scanResults to display what codes were tracked on 'Done' button press
                    scanResults.add(new ScanResult(trackedBarcode.getBarcode()));

                    //retrieve once per barcode data about it from backend and store in scannedBarcodesData map
                    Map<String,String> barcodeValuesMap=getTestDataForBarcode(trackedBarcode);
                    scannedBarcodesData.put(trackedBarcode.getBarcode().getData(),barcodeValuesMap);

                    //get ARView for the barcode and push it to the overlay
                    ARView arView=arManager.getARViewFor(trackedBarcode,scannedBarcodesData.get(trackedBarcode.getBarcode().getData()));
                    overlay.setViewForTrackedBarcode(trackedBarcode, arView);

                }
                for (TrackedBarcode trackedBarcode : session.getTrackedBarcodes().values()){
                    if (!session.getAddedTrackedBarcodes().contains(trackedBarcode)) {

                        //verify if the area of barcode has changed and is out of previous range
                        if (!arManager.barcodeAreaInSameRange(trackedBarcode)) {
                            ARView arView = arManager.getARViewFor(trackedBarcode, scannedBarcodesData.get(trackedBarcode.getBarcode().getData()));
                            overlay.setViewForTrackedBarcode(trackedBarcode, arView);
                        }
                    }
                }
            }
        });
    }

    @Override
    public void onObservationStarted(@NotNull BarcodeTracking barcodeTracking) {

    }

    @Override
    public void onObservationStopped(@NotNull BarcodeTracking barcodeTracking) {

    }
}
