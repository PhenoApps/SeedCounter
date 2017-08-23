package edu.ksu.wheatgenetics.seedcounter;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraConstrainedHighSpeedCaptureSession;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.MediaRecorder;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.support.annotation.NonNull;
import android.support.annotation.RequiresApi;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.util.Range;
import android.util.Size;
import android.util.SparseIntArray;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

/**
 * Created by chaneylc on 8/16/2017.
 */

@RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
public class CameraSessionActivity extends AppCompatActivity {

    private CameraManager mCamManager;
    private CameraDevice mCamera;
    private CameraConstrainedHighSpeedCaptureSession mHighSpeedSession;
    private CaptureRequest.Builder mPreviewBuilder;
    private Handler mBackgroundHandler;
    private HandlerThread mBackgroundThread;

    private Button startSession;
    private TextureView textureView;
    private TextView textView;

    List<Surface> surfaces = new ArrayList<Surface>();

    private Size mVideoSize, mPreviewSize;

    private String mVideoAbsPath;

    private Semaphore mCameraSemaphore = new Semaphore(1);
    private CameraCaptureSession mPreviewSession;
    private MediaRecorder mMediaRecorder;
    private boolean isRecording = false;

    private static final SparseIntArray ORIENTATIONS = new SparseIntArray();
    static {
        ORIENTATIONS.append(Surface.ROTATION_0, 90);
        ORIENTATIONS.append(Surface.ROTATION_90, 0);
        ORIENTATIONS.append(Surface.ROTATION_180, 270);
        ORIENTATIONS.append(Surface.ROTATION_270, 180);
    }

    final CameraDevice.StateCallback mStateCallback = new CameraDevice.StateCallback() {
        @RequiresApi(api = Build.VERSION_CODES.M)
        @Override
        public void onOpened(@NonNull CameraDevice camera) {
            mCameraSemaphore.release();
            mCamera = camera;
            try {
                startPreview();
            } catch (CameraAccessException e) {
                e.printStackTrace();
            }

        }

        @Override
        public void onDisconnected(@NonNull CameraDevice camera) {
            mCameraSemaphore.release();
            mCamera.close();
            mCamera = null;
        }

        @Override
        public void onError(@NonNull CameraDevice camera, int error) {
            mCameraSemaphore.release();
            mCamera.close();
            mCamera = null;
            finish();
        }
    };

    final TextureView.SurfaceTextureListener mSurfaceViewTextureListener = new TextureView.SurfaceTextureListener() {
        @RequiresApi(api = Build.VERSION_CODES.M)
        @Override
        public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
            openCamera(1280, 720);
            startSession.setEnabled(true);
        }

        @Override
        public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {

        }

        @Override
        public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
            return false;
        }

        @Override
        public void onSurfaceTextureUpdated(SurfaceTexture surface) {

        }
    };

    @Override
    public void onCreate(Bundle savedInstance) {
        super.onCreate(savedInstance);

        setContentView(R.layout.activity_camera_session);

        final HandlerThread backgroundThread = new HandlerThread("Camera Session thread");
        backgroundThread.start();
        mBackgroundHandler = new Handler(backgroundThread.getLooper());

        startSession = (Button) findViewById(R.id.takePicture);
        startSession.setEnabled(false);
        startSession.setOnClickListener(new View.OnClickListener() {
            @RequiresApi(api = Build.VERSION_CODES.M)
            @Override
            public void onClick(View v) {

                try {
                    if (!isRecording) {
                        startVideoCapture();
                        startSession.setText(getString(R.string.button_stop));
                    } else {
                        stopVideoCapture();
                        startSession.setText(getString(R.string.button_start));
                    }
                } catch (CameraAccessException e) {
                    e.printStackTrace();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        });

        textView = (TextView) findViewById(R.id.textView);

        textureView = (TextureView) findViewById(R.id.textureView);
        textureView.setSurfaceTextureListener(mSurfaceViewTextureListener);

        mCamManager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
    }

    @RequiresApi(api = Build.VERSION_CODES.M)
    @Override
    public void onPause() {
        isRecording = false;
        closeCamera();
        stopBackgroundThread();
        super.onPause();
    }

    @Override
    public void onResume() {
        super.onResume();
        startBackgroundThread();

        if (textureView.isAvailable()) {
            openCamera(1280, 720);
        } else {
            textureView.setSurfaceTextureListener(mSurfaceViewTextureListener);
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.M)
    private void closeCamera() {
        try {
            startSession.setText(getString(R.string.button_start));
            mCameraSemaphore.acquire();
            if (mPreviewSession != null) {
                mPreviewSession.close();
                mPreviewSession = null;
            }
            if (mHighSpeedSession != null) {
                mHighSpeedSession.close();
                mHighSpeedSession = null;
            }
            if (mCamera != null) {
                mCamera.close();
                mCamera = null;
            }
        } catch (InterruptedException e) {
            throw new RuntimeException("Interrupted while trying to lock camera closing", e);
        } finally {
            mCameraSemaphore.release();
        }
    }

    private void stopBackgroundThread() {

        mBackgroundThread.quitSafely();
        try {
            mBackgroundThread.join();
            mBackgroundThread = null;
            mBackgroundHandler = null;
        } catch(InterruptedException e) {
            e.printStackTrace();
        }
    }

    private void startBackgroundThread() {

        mBackgroundThread = new HandlerThread("Camera");
        mBackgroundThread.start();
        mBackgroundHandler = new Handler(mBackgroundThread.getLooper());
    }

    @RequiresApi(api = Build.VERSION_CODES.M)
    void startPreview() throws CameraAccessException {

        if (mCamera == null || !textureView.isAvailable()) return;

        surfaces.clear();
        SurfaceTexture texture = textureView.getSurfaceTexture();
        texture.setDefaultBufferSize(1280, 720);
        mPreviewBuilder = mCamera.createCaptureRequest(CameraDevice.TEMPLATE_RECORD);

        final Surface previewSurface = new Surface(texture);
        surfaces.add(previewSurface);
        mPreviewBuilder.addTarget(previewSurface);

        mCamera.createCaptureSession(surfaces,
                new CameraCaptureSession.StateCallback() {

                    @Override
                    public void onConfigured(@NonNull CameraCaptureSession session) {
                        mPreviewSession = session;
                        try {
                            updatePreview();
                        } catch (CameraAccessException e) {
                            e.printStackTrace();
                        }
                    }

                    @Override
                    public void onConfigureFailed(@NonNull CameraCaptureSession session) {

                    }
                }, mBackgroundHandler);
    }

    @RequiresApi(api = Build.VERSION_CODES.M)
    void updatePreview() throws CameraAccessException {

        if (mCamera == null) return;

        if (isRecording && mHighSpeedSession != null) {
            mPreviewBuilder.set(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE, new Range<>(120, 120));
            List<CaptureRequest> highSpeedRequest =
                    mHighSpeedSession.createHighSpeedRequestList(mPreviewBuilder.build());
            mHighSpeedSession.setRepeatingBurst(highSpeedRequest, null, mBackgroundHandler);
        } else {
           // mPreviewBuilder.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO);
            mPreviewSession.setRepeatingRequest(mPreviewBuilder.build(), null, mBackgroundHandler);

        }
    }

    @RequiresApi(api = Build.VERSION_CODES.M)
    void startVideoCapture() throws CameraAccessException, IOException {

        isRecording = true;
        surfaces.clear();

        initMediaRecorder();

        SurfaceTexture texture = textureView.getSurfaceTexture();
        mPreviewBuilder = mCamera.createCaptureRequest(CameraDevice.TEMPLATE_RECORD);

        final Surface previewSurface = new Surface(texture);
        surfaces.add(previewSurface);
        mPreviewBuilder.addTarget(previewSurface);

        final Surface recorderSurface = mMediaRecorder.getSurface();
        surfaces.add(recorderSurface);
        mPreviewBuilder.addTarget(recorderSurface);

        mCamera.createConstrainedHighSpeedCaptureSession(surfaces, new CameraCaptureSession.StateCallback() {
            @Override
            public void onConfigured(@NonNull CameraCaptureSession session) {
                mPreviewSession = session;
                mHighSpeedSession = (CameraConstrainedHighSpeedCaptureSession) mPreviewSession;
                try {
                    updatePreview();
                } catch (CameraAccessException e) {
                    e.printStackTrace();
                }

            }

            @Override
            public void onConfigureFailed(@NonNull CameraCaptureSession session) {

            }
        }, mBackgroundHandler);

        mMediaRecorder.start();

    }

    @RequiresApi(api = Build.VERSION_CODES.M)
    void stopVideoCapture() throws CameraAccessException {

        isRecording = false;

        mHighSpeedSession.stopRepeating();

        mMediaRecorder.stop();
        mMediaRecorder.reset();

        //startPreview();

        //send intent broadcast to index new video file
        final File scanFile = new File(mVideoAbsPath);
        final Uri contentUri = Uri.fromFile(scanFile);
        final Intent mediaScanIntent = new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE);
        mediaScanIntent.setData(contentUri);
        this.sendBroadcast(mediaScanIntent);

        //start activity to process file
        final Intent startProcessingFromFile = new Intent(this, FileVideoCaptureActivity.class);
        startProcessingFromFile.putExtra(SeedCounterConstants.FILE_PATH_EXTRA, mVideoAbsPath);
        startActivity(startProcessingFromFile);
    }

    void initMediaRecorder() throws IOException {

        mMediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
        mMediaRecorder.setVideoSource(MediaRecorder.VideoSource.SURFACE);
        mMediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.WEBM);

        mVideoAbsPath = getVideoFilePath();

        Log.d("FILE PATH", mVideoAbsPath);

        mMediaRecorder.setOutputFile(mVideoAbsPath);
        mMediaRecorder.setVideoEncodingBitRate(20000000);
        mMediaRecorder.setVideoFrameRate(120);
        mMediaRecorder.setVideoSize(1280, 720);
        mMediaRecorder.setVideoEncoder(MediaRecorder.VideoEncoder.VP8);
        mMediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
        int rotation = getWindowManager().getDefaultDisplay().getRotation();
        int orientation = ORIENTATIONS.get(rotation);
        mMediaRecorder.setOrientationHint(orientation);

        mMediaRecorder.prepare();

    }

    private String getVideoFilePath() {

        final File dir = this.getExternalFilesDir("DCIM");
        return new File(getExternalFilesDir("DCIM"), "VID_" + System.currentTimeMillis() + ".webm").getAbsolutePath();
        //dir == null ? "" : (dir.getAbsolutePath() + "/"))
           //     + System.currentTimeMillis() + ".webm";
    }

    void openCamera(int width, int height) {
        try {
            final String cameraId = mCamManager.getCameraIdList()[0];
            CameraCharacteristics chars = mCamManager.getCameraCharacteristics(cameraId);
            StreamConfigurationMap configs = chars.get(
                    CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP
            );

            mVideoSize = chooseVideoSize(configs.getOutputSizes(MediaRecorder.class));
            mPreviewSize = chooseOptimalSize(configs.getOutputSizes(SurfaceTexture.class),
                    width, height, mVideoSize);

            mMediaRecorder = new MediaRecorder();
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                return;
            }
            try {
                if (!mCameraSemaphore.tryAcquire(2500, TimeUnit.MILLISECONDS)) {
                    throw new RuntimeException("Time out while waiting to lock camera opening.");
                }
                mCamManager.openCamera(mCamManager.getCameraIdList()[0], mStateCallback, null);
            } catch (InterruptedException e) {
                throw new RuntimeException("Interrupted while trying to lock camera opening.", e);
            }
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    private static Size chooseVideoSize(Size[] choices) {
        for (Size size : choices) {
            if (size.getWidth() == size.getHeight() * 4 / 3 && size.getWidth() <= 1080) {
                return size;
            }
        }
        return choices[choices.length - 1];
    }

    private static Size chooseOptimalSize(Size[] choices, int width, int height, Size aspectRatio) {
        // Collect the supported resolutions that are at least as big as the preview Surface
        List<Size> bigEnough = new ArrayList<>();
        int w = aspectRatio.getWidth();
        int h = aspectRatio.getHeight();
        for (Size option : choices) {
            if (option.getHeight() == option.getWidth() * h / w &&
                    option.getWidth() >= width && option.getHeight() >= height) {
                bigEnough.add(option);
            }
        }

        // Pick the smallest of those, assuming we found any
        if (bigEnough.size() > 0) {
            return Collections.min(bigEnough, new CompareSizesByArea());
        } else {
            return choices[0];
        }
    }

    static class CompareSizesByArea implements Comparator<Size> {

        @Override
        public int compare(Size lhs, Size rhs) {
            // We cast here to ensure the multiplications won't overflow
            return Long.signum((long) lhs.getWidth() * lhs.getHeight() -
                    (long) rhs.getWidth() * rhs.getHeight());
        }

    }

    public boolean isExternalStorageWritable() {

        String state = Environment.getExternalStorageState();
        if (Environment.MEDIA_MOUNTED.equals(state)) {
            return true;
        }
        return false;
    }

}
