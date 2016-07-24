package com.arktronic.cameraserve;

import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.graphics.YuvImage;
import android.hardware.Camera;
import android.media.CamcorderProfile;
import android.os.Bundle;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.Display;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.Menu;
import android.view.MenuItem;
import android.view.WindowManager;
import android.widget.Toast;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.List;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class MainActivity extends AppCompatActivity implements SurfaceHolder.Callback, Camera.PreviewCallback {

    private SurfaceHolder holder;
    private Camera camera;
    private boolean previewRunning = false;
    private int camId = 0;
    private ByteArrayOutputStream previewStream = new ByteArrayOutputStream();
    private Thread serverThread = new Thread(new MjpegServer());

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

    private static void setJpegFrame(ByteArrayOutputStream stream) {
        try {
            frameLock.writeLock().lock();
            jpegFrame = stream.toByteArray();
        } finally {
            frameLock.writeLock().unlock();
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        FloatingActionButton fab = (FloatingActionButton) findViewById(R.id.fab);
        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                int cams = Camera.getNumberOfCameras();
                camId++;
                if (camId > cams - 1) camId = 0;
                if (previewRunning) stopPreview();
                if (camera != null) camera.release();
                camera = Camera.open(camId);
                startPreview();

                Toast.makeText(MainActivity.this, "Cam " + (camId + 1),
                        Toast.LENGTH_SHORT).show();
            }
        });

        SurfaceView cameraView = (SurfaceView) findViewById(R.id.surfaceView);
        holder = cameraView.getHolder();
        holder.addCallback(this);
        holder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);

        getWindow().addFlags(WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON);
    }

    @Override
    protected void onResume() {
        super.onResume();

        if (camera == null) camera = Camera.open(camId);
        startPreview();

        getWindow().addFlags(WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        if (id == R.id.action_settings) {
            startActivity(new Intent(this, SettingsActivity.class));
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);

        startPreview();
    }

    private void startPreview() {
        if (previewRunning) stopPreview();

        Display display = ((WindowManager)getSystemService(WINDOW_SERVICE)).getDefaultDisplay();

        if(display.getRotation() == Surface.ROTATION_0)
        {
            camera.setDisplayOrientation(90);
        }
        else if(display.getRotation() == Surface.ROTATION_270)
        {
            camera.setDisplayOrientation(180);
        }
        else {
            camera.setDisplayOrientation(0);
        }

        Camera.Parameters p = camera.getParameters();
        Camera.Size size = p.getSupportedPreviewSizes().get(0);
        p.setPreviewSize(size.width, size.height);
        camera.setParameters(p);

        try {
            camera.setPreviewDisplay(holder);
            camera.setPreviewCallback(this);
        } catch (IOException e) {
            e.printStackTrace();
        }

        camera.startPreview();

        holder.addCallback(this);

        if(!serverThread.isAlive()) serverThread.start();

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

        camera = Camera.open(camId);
        startPreview();
    }

    @Override
    public void surfaceChanged(SurfaceHolder surfaceHolder, int format, int width, int height) {
        startPreview();
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

//        Bitmap bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
//        Bitmap scaled = Bitmap.createScaledBitmap(bmp, previewHeight, previewWidth, true);
//        int w = scaled.getWidth();
//        int h = scaled.getHeight();
//        Matrix mtx = new Matrix();
//        mtx.postRotate(90);
//        // Rotating Bitmap
//        bmp = Bitmap.createBitmap(scaled, 0, 0, w, h, mtx, true);
//        bmp.compress(Bitmap.CompressFormat.JPEG, 100, previewStream);


        int format = p.getPreviewFormat();
        new YuvImage(bytes, format, p.getPreviewSize().width, p.getPreviewSize().height, null)
                .compressToJpeg(new Rect(0, 0, p.getPreviewSize().width, p.getPreviewSize().height),
                        100, previewStream);


//        int format = p.getPreviewFormat();
//        new YuvImage(bytes, format, previewWidth, previewHeight, null)
//                .compressToJpeg(new Rect(0, 0, previewWidth, previewHeight),
//                        100, previewStream);
//
//        Bitmap bmp = BitmapFactory.decodeByteArray(previewStream.toByteArray(), 0, previewStream.size());
//        Bitmap scaled = Bitmap.createScaledBitmap(bmp, previewHeight, previewWidth, true);
//        int w = scaled.getWidth();
//        int h = scaled.getHeight();
//        Matrix mtx = new Matrix();
//        mtx.postRotate(90);
//        // Rotating Bitmap
//        bmp = Bitmap.createBitmap(scaled, 0, 0, w, h, mtx, true);
//        previewStream.reset();
//        bmp.compress(Bitmap.CompressFormat.JPEG, 100, previewStream);

        setJpegFrame(previewStream);
    }
}
