package com.arkconcepts.cameraserve;

import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Rect;
import android.graphics.YuvImage;
import android.hardware.Camera;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.design.widget.FloatingActionButton;
import android.support.v7.app.AppCompatActivity;
import android.view.Display;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.WindowManager;
import android.widget.Toast;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class MainActivity extends AppCompatActivity implements SurfaceHolder.Callback, Camera.PreviewCallback {

    private SurfaceHolder holder;
    private Camera camera;
    private boolean previewRunning = false;
    private int camId = 0;
    private ByteArrayOutputStream previewStream = new ByteArrayOutputStream();
    private int rotationSteps = 0;
    private boolean aboveLockScreen = true;

    private static SsdpAdvertiser ssdpAdvertiser = new SsdpAdvertiser();
    private static Thread ssdpThread = new Thread(ssdpAdvertiser);
    private static MjpegServer mjpegServer = new MjpegServer();
    private static Thread serverThread = new Thread(mjpegServer);
    private static HashMap<Integer, List<Camera.Size>> cameraSizes = new HashMap<>();
    private static ReentrantReadWriteLock frameLock = new ReentrantReadWriteLock();
    private static byte[] jpegFrame;

    public static byte[] getJpegFrame() {
        try {
            frameLock.readLock().lock();
            return jpegFrame;
        } finally {
            frameLock.readLock().unlock();
        }
    }

    public static HashMap<Integer, List<Camera.Size>> getCameraSizes() {
        return cameraSizes;
    }

    private static void setJpegFrame(ByteArrayOutputStream stream) {
        try {
            frameLock.writeLock().lock();
            jpegFrame = stream.toByteArray();
        } finally {
            frameLock.writeLock().unlock();
        }
    }

    private void cacheResolutions() {
        int cams = Camera.getNumberOfCameras();
        for (int i = 0; i < cams; i++) {
            Camera cam = Camera.open(i);
            Camera.Parameters params = cam.getParameters();
            cameraSizes.put(i, params.getSupportedPreviewSizes());
            cam.release();
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        cacheResolutions();

        FloatingActionButton flipButton = (FloatingActionButton) findViewById(R.id.flipButton);
        flipButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                int cams = Camera.getNumberOfCameras();
                camId++;
                if (camId > cams - 1) camId = 0;
                if (previewRunning) stopPreview();
                if (camera != null) camera.release();
                camera = null;

                SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(MainActivity.this);
                preferences.edit().putString("cam", String.valueOf(camId)).apply();

                openCamAndPreview();

                Toast.makeText(MainActivity.this, "Cam " + (camId + 1),
                        Toast.LENGTH_SHORT).show();
            }
        });

        FloatingActionButton settingsButton = (FloatingActionButton) findViewById(R.id.settingsButton);
        settingsButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                startActivity(new Intent(MainActivity.this, SettingsActivity.class));
            }
        });

        SurfaceView cameraView = (SurfaceView) findViewById(R.id.surfaceView);
        holder = cameraView.getHolder();
        holder.addCallback(this);
        holder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);
    }

    @Override
    protected void onResume() {
        super.onResume();

        loadPreferences();

        openCamAndPreview();

        if (!ssdpThread.isAlive()) ssdpThread.start();
        if (!serverThread.isAlive()) serverThread.start();

        getWindow().addFlags(WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON);

        if (aboveLockScreen)
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED);
        else
            getWindow().clearFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED);
    }

    @Override
    public void onBackPressed() {
        super.onBackPressed();

        this.finish();
        System.exit(0);
    }

    private void openCamAndPreview() {
        try {
            if (camera == null) camera = Camera.open(camId);
            startPreview();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void loadPreferences() {
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(this);
        camId = Integer.parseInt(preferences.getString("cam", "0"));
        Integer rotDegrees = Integer.parseInt(preferences.getString("rotation", "0"));
        rotationSteps = rotDegrees / 90;
        Integer port = Integer.parseInt(preferences.getString("port", "8080"));
        MjpegServer.setPort(port);
        aboveLockScreen = preferences.getBoolean("above_lock_screen", aboveLockScreen);
        Boolean allIps = preferences.getBoolean("allow_all_ips", false);
        MjpegServer.setAllIpsAllowed(allIps);
    }

    private void startPreview() {
        if (previewRunning) stopPreview();

        Display display = ((WindowManager) getSystemService(WINDOW_SERVICE)).getDefaultDisplay();

        if (display.getRotation() == Surface.ROTATION_0) {
            camera.setDisplayOrientation(90);
        } else if (display.getRotation() == Surface.ROTATION_270) {
            camera.setDisplayOrientation(180);
        } else {
            camera.setDisplayOrientation(0);
        }

        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(this);
        String res = preferences.getString("resolution", "640x480");
        String[] resParts = res.split("x");

        Camera.Parameters p = camera.getParameters();
        p.setPreviewSize(Integer.parseInt(resParts[0]), Integer.parseInt(resParts[1]));
        camera.setParameters(p);

        try {
            camera.setPreviewDisplay(holder);
            camera.setPreviewCallback(this);
        } catch (IOException e) {
            e.printStackTrace();
        }

        camera.startPreview();

        holder.addCallback(this);

        previewRunning = true;
    }

    private void stopPreview() {
        if (!previewRunning) return;

        holder.removeCallback(this);
        camera.stopPreview();
        camera.setPreviewCallback(null);

        previewRunning = false;
    }

    @Override
    public void surfaceCreated(SurfaceHolder surfaceHolder) {
        stopPreview();
        if (camera != null) camera.release();
        camera = null;

        openCamAndPreview();
    }

    @Override
    public void surfaceChanged(SurfaceHolder surfaceHolder, int format, int width, int height) {
        openCamAndPreview();
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder surfaceHolder) {
        stopPreview();
        if (camera != null) camera.release();
        camera = null;
    }

    @Override
    public void onPreviewFrame(byte[] bytes, Camera camera) {
        previewStream.reset();
        Camera.Parameters p = camera.getParameters();

        int previewHeight = p.getPreviewSize().height,
            previewWidth = p.getPreviewSize().width;

        switch(rotationSteps) {
            case 1:
                bytes = Rotator.rotateYUV420Degree90(bytes, previewWidth, previewHeight);
                break;
            case 2:
                bytes = Rotator.rotateYUV420Degree180(bytes, previewWidth, previewHeight);
                break;
            case 3:
                bytes = Rotator.rotateYUV420Degree270(bytes, previewWidth, previewHeight);
                break;
        }

        if (rotationSteps == 1 || rotationSteps == 3) {
            int tmp = previewHeight;
            previewHeight = previewWidth;
            previewWidth = tmp;
        }

        int format = p.getPreviewFormat();
        new YuvImage(bytes, format, previewWidth, previewHeight, null)
                .compressToJpeg(new Rect(0, 0, previewWidth, previewHeight),
                        100, previewStream);

        setJpegFrame(previewStream);
    }
}
