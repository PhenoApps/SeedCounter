package edu.ksu.wheatgenetics.seedcounter;

import android.content.ContentUris;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.provider.DocumentsContract;
import android.provider.MediaStore;
import android.support.annotation.NonNull;
import android.support.design.widget.NavigationView;
import android.support.v4.app.ActivityCompat;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.ActionBarDrawerToggle;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.github.hiteshsondhi88.libffmpeg.ExecuteBinaryResponseHandler;
import com.github.hiteshsondhi88.libffmpeg.FFmpeg;
import com.github.hiteshsondhi88.libffmpeg.LoadBinaryResponseHandler;
import com.github.hiteshsondhi88.libffmpeg.exceptions.FFmpegCommandAlreadyRunningException;
import com.github.hiteshsondhi88.libffmpeg.exceptions.FFmpegNotSupportedException;

import org.opencv.android.BaseLoaderCallback;
import org.opencv.android.LoaderCallbackInterface;
import org.opencv.android.OpenCVLoader;

import java.io.File;
import java.util.Stack;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.LinkedBlockingQueue;

import static android.content.pm.PackageManager.PERMISSION_DENIED;

public class FileVideoCaptureActivity extends AppCompatActivity {

    private static final String TAG = "SeedCounter";

    static {System.loadLibrary("opencv_java3");}

    private double mTotalSeconds;

    private BlockingQueue mQueue;

    private int mPrevFrameCount;

    //seed counter utility
    private SeedCounter mSeedCounter;

    //android views
    private TextView mTextView;
    private ActionBarDrawerToggle mDrawerToggle;
    private DrawerLayout mDrawerLayout;
    private NavigationView nvDrawer;

    private BaseLoaderCallback mLoaderCallback = new BaseLoaderCallback(this) {
        @Override
        public void onManagerConnected(int status) {
            switch (status) {
                case LoaderCallbackInterface.SUCCESS: {
                    Log.i(TAG, "OpenCV loaded successfully");
                }
                break;
                default: {
                    super.onManagerConnected(status);
                }
                break;
            }
        }
    };

    public FileVideoCaptureActivity() {
        Log.i(TAG, "Instantiated new " + this.getClass());
    }

    /**
     * Called when the activity is first created.
     */
    @Override
    public void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_file_video_capture);

        mDrawerLayout = (DrawerLayout) findViewById(R.id.drawer_layout);

        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle(null);
            getSupportActionBar().getThemedContext();
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setHomeButtonEnabled(true);
        }

        nvDrawer = (NavigationView) findViewById(R.id.nvView);
        mTextView = (TextView) findViewById(R.id.textView);

        setupDrawerContent(nvDrawer);
        setupDrawer();

        final SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(this);
        final boolean tutorialMode = sharedPref.getBoolean(SettingsActivity.TUTORIAL_MODE, true);

        final boolean debugMode = sharedPref.getBoolean(SettingsActivity.DEBUG_MODE, false);
       // if (tutorialMode)
           // launchIntro();

        final int areaLow = Integer.valueOf(sharedPref.getString(SettingsActivity.PARAM_AREA_LOW, "200"));
        final int areaHigh = Integer.valueOf(sharedPref.getString(SettingsActivity.PARAM_AREA_HIGH, "160000"));
        final int defaultRate = Integer.valueOf(sharedPref.getString(SettingsActivity.PARAM_DEFAULT_RATE, "34"));
        final double sizeLowerBoundRatio = Double.valueOf(sharedPref.getString(SettingsActivity.PARAM_SIZE_LOWER_BOUND_RATIO, "0.25"));
        final double newSeedDistRatio = Double.valueOf(sharedPref.getString(SettingsActivity.PARAM_NEW_SEED_DIST_RATIO, "4.0"));

        final SeedCounter.SeedCounterParams params = new SeedCounter.SeedCounterParams(areaLow, areaHigh, defaultRate, sizeLowerBoundRatio, newSeedDistRatio);
        //mSeedCounter = new SeedCounter(params);

        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        mPrevFrameCount = 0;

        mQueue = new LinkedBlockingQueue();

        ActivityCompat.requestPermissions(this, SeedCounterConstants.permissions, SeedCounterConstants.PERM_REQUEST);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] result) {

        boolean noneDenied = true;
        if (requestCode == SeedCounterConstants.PERM_REQUEST) {
            final int size = permissions.length;
            for (int i = 0; i < size; i = i + 1) {
                if (result[i] == PERMISSION_DENIED) {
                    Toast.makeText(this, "You must accept all permissions to use SeedCounter", Toast.LENGTH_LONG).show();
                    noneDenied = false;
                    finish();
                }
            }

            if (noneDenied) {
                final Intent callingIntent = getIntent();
                if (callingIntent.hasExtra(SeedCounterConstants.FILE_PATH_EXTRA)) {
                    String path = callingIntent.getStringExtra(SeedCounterConstants.FILE_PATH_EXTRA);
                    startProcessing(path);
                } else {
                    final Intent i = new Intent(Intent.ACTION_GET_CONTENT);
                    i.setType("*/*");
                    startActivityForResult(Intent.createChooser(i, "Choose file to import."),
                            SeedCounterConstants.LOAD_REQUEST);
                }
            }

        }
    }

    private void startProcessing(final String path) {

        final FFmpeg ffmpeg = FFmpeg.getInstance(FileVideoCaptureActivity.this);

        //first get fps and time information from the input file.
        try {
            ffmpeg.execute(
                    new String[]{"-i", path},
                    new ExecuteBinaryResponseHandler() {

                        @Override
                        public void onProgress(String msg) {

                            Log.d("PROG", msg);

                            if (msg.contains("Duration")) {

                                parseVideoInfo(msg);

                                new ExtractFramesTask().execute(path);

                                ((ProgressBar) findViewById(R.id.progressBar))
                                        .setVisibility(View.VISIBLE);

                                mTextView.setText("Extracting frames from high speed video.");

                                ffmpeg.killRunningProcesses();
                            }
                        }
                    });

        } catch(FFmpegCommandAlreadyRunningException e) {
            e.printStackTrace();
        }

    }

    protected void onActivityResult(int requestCode, int resultCode, Intent intent) {
        super.onActivityResult(requestCode, resultCode, intent);

        if (requestCode == SeedCounterConstants.LOAD_REQUEST) {
            if (resultCode == RESULT_OK) {

                String path = getPath(intent.getData());

                startProcessing(path);
            }
        }
    }

    @Override
    public void onResume() {
        super.onResume();

        FFmpeg ffmpeg = FFmpeg.getInstance(this);
        try {
            ffmpeg.loadBinary(new LoadBinaryResponseHandler() {

            });
        } catch (FFmpegNotSupportedException e) {
            e.printStackTrace();
        }

        if (!OpenCVLoader.initDebug()) {
            Log.d(TAG, "Internal OpenCV library not found. Using OpenCV Manager for initialization");
            OpenCVLoader.initAsync(OpenCVLoader.OPENCV_VERSION_3_2_0, this, mLoaderCallback);
        } else {
            Log.d(TAG, "OpenCV library found inside package. Using it!");
            mLoaderCallback.onManagerConnected(LoaderCallbackInterface.SUCCESS);
        }
    }

    private void setupDrawer() {

        mDrawerToggle = new ActionBarDrawerToggle(this, mDrawerLayout,
                R.string.drawer_open, R.string.drawer_close) {

            public void onDrawerOpened(View drawerView) {
                View view = FileVideoCaptureActivity.this.getCurrentFocus();
                if (view != null) {
                    InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
                    imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
                }
            }

            public void onDrawerClosed(View view) {
            }

         };

        mDrawerToggle.setDrawerIndicatorEnabled(true);
        mDrawerLayout.addDrawerListener(mDrawerToggle);
    }

    private void setupDrawerContent(NavigationView navigationView) {

        navigationView.setNavigationItemSelectedListener(
                new NavigationView.OnNavigationItemSelectedListener() {
                    @Override
                    public boolean onNavigationItemSelected(@NonNull MenuItem menuItem) {
                        selectDrawerItem(menuItem);
                        return true;
                    }
                });
    }

    public void selectDrawerItem(MenuItem menuItem) {
        switch (menuItem.getItemId()) {

            case R.id.nav_import:

                break;
            case R.id.nav_settings:

                break;
            case R.id.nav_export:
                break;
            case R.id.nav_about:
                Toast.makeText(this, "About", Toast.LENGTH_SHORT).show();
                break;
            case R.id.nav_intro:
                final Intent intro_intent = new Intent(FileVideoCaptureActivity.this, IntroActivity.class);
                  runOnUiThread(new Runnable() {
                        @Override public void run() {
                            startActivity(intro_intent);
                        }
                  });
                break;
        }

        mDrawerLayout.closeDrawers();
    }

    public void launchIntro() {

        new Thread(new Runnable() {
            @Override
            public void run() {

                //  Launch app intro
                final Intent i = new Intent(FileVideoCaptureActivity.this, IntroActivity.class);

                runOnUiThread(new Runnable() {
                    @Override public void run() {
                        startActivity(i);
                    }
                });


            }
        }).start();
    }

    public void nextFrame(final File dir, final int frameCount) {

      //create a thread to start and wait until processing finishes
        Thread t = new Thread(new Runnable() {

            public void run() {

                if (dir.isDirectory()) {

                    final File[] children = dir.listFiles();
                    final File[] nextFrames = new File[frameCount - mPrevFrameCount];

                    int j = 0;
                    for (int i = mPrevFrameCount; i < frameCount; i++) {
                        nextFrames[j++] = children[i];
                    }

                    mPrevFrameCount = frameCount;

                    /*for (int i = 0; i < nextFrames.length; i++) {

                        mSeedCounter.process(
                                BitmapFactory.decodeFile(
                                        nextFrames[i].getPath()));

                        mTextView.post(new Runnable() {
                            public void run() {
                                mTextView.setText(String.valueOf(
                                        mSeedCounter.getNumSeeds()
                                ));
                            }
                        });

                        mTextView.postInvalidate();
                    }*/
                }
            }
        });

        //actually begin and wait until thread is finished counting the given frames
        try {
            t.start();
            t.join();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

    }

    private void updateFFmpegMsgView(final String progress) {
        this.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                ((TextView) findViewById(R.id.progressView))
                        .setText(progress);

                String[] time = progress.split("time=")[1].split(" ")[0].split(":");
                double current = Integer.valueOf(time[0]) * 360
                        + Integer.valueOf(time[1]) * 60
                        + Double.valueOf(time[2]);

                ((ProgressBar) findViewById(R.id.progressBar))
                        .setProgress((int) (100.0 * (current / mTotalSeconds)));
            }
        });
    }

    private void parseVideoInfo(String msg) {

        String[] time = msg.split("Duration: ")[1].split(",")[0].split(":");
        mTotalSeconds = Integer.valueOf(time[0]) * 360
                + Integer.valueOf(time[1]) * 60
                + Double.valueOf(time[2]);

    }

    private class ExtractFramesTask extends AsyncTask<String, String, Void> {

        @Override
        protected Void doInBackground(String... strings) {

            final String path = strings[0];

            if (isExternalStorageWritable()) {

                File vidFile = new File(path);
                final File tempDir = new File(vidFile.getParent() + "temp");

                if (tempDir.mkdir()) {
                    Log.d("TempFolder", "Created successfully.");
                } else Log.d("TempFolder", "Not created successfully.");


                File[] children = tempDir.listFiles();
                for (File f : children) {
                    if(f.delete()) {
                        Log.d("TempFolder", "Deleted successfully.");
                    } else Log.d("TempFolder", "Not deleted successfully.");
                }

                final FFmpeg ffmpeg = FFmpeg.getInstance(FileVideoCaptureActivity.this);

                if (tempDir.exists()) {

                    String nextFramePath = tempDir.getPath() + "/temp%d.jpg";
                    try {
                        ffmpeg.execute(

                                //ffmpeg commands
                                new String[]{"-i", path, nextFramePath},

                                //class to control messages sent by ffmpeg lib
                                new ExecuteBinaryResponseHandler() {

                                    @Override
                                    public void onSuccess(String msg) {

                                        Log.d("SUCCESS", msg);
                                        //nextFrame(tempDir, 0);

                                    }

                                    @Override
                                    public void onFailure(String msg) {

                                        publishProgress(msg);

                                        Log.d("FAILURE", msg);
                                    }

                                    @Override
                                    public void onProgress(String msg) {

                                        publishProgress(msg);

                                        if (msg.contains("frame=")) {
                                            int frameCount = Integer.valueOf(
                                                    msg.split("frame=\\s+")[1].split(" ")[0]
                                            );
                                            nextFrame(tempDir, frameCount);
                                        }

                                        Log.d("PROGRESS", msg);
                                    }
                                });
                    } catch (FFmpegCommandAlreadyRunningException e) {
                        Log.d("DEBUG", "command already running");
                    }

                } else if (!tempDir.mkdirs())
                    Log.d("SeedCounterFS", "failed to create Seedcounter directory");

            } else Toast.makeText(FileVideoCaptureActivity.this,
                    "Default storage not writable.", Toast.LENGTH_LONG).show();

            return null;
        }

        protected void onProgressUpdate(String... progress) {
            if (progress[0].contains("time="))
                updateFFmpegMsgView(progress[0]);
        }

    }

    private static boolean isExternalStorageWritable() {

        String state = Environment.getExternalStorageState();
        if (Environment.MEDIA_MOUNTED.equals(state)) {
            return true;
        }
        return false;
    }

    //based on https://github.com/iPaulPro/aFileChooser/blob/master/aFileChooser/src/com/ipaulpro/afilechooser/utils/FileUtils.java
    public String getPath(Uri uri) {

        // DocumentProvider
        if (DocumentsContract.isDocumentUri(this, uri)) {
            // LocalStorageProvider
            if ("com.android.externalstorage.documents".equals(uri.getAuthority())) {
                final String docId = DocumentsContract.getDocumentId(uri);
                final String[] split = docId.split(":");
                final String type = split[0];

                if ("primary".equalsIgnoreCase(type)) {
                    return Environment.getExternalStorageDirectory() + "/" + split[1];
                }

                // TODO handle non-primary volumes
            }
            // DownloadsProvider
            else if ("com.android.providers.downloads.documents".equals(uri.getAuthority())) {

                final String id = DocumentsContract.getDocumentId(uri);
                final Uri contentUri = ContentUris.withAppendedId(
                        Uri.parse("content://downloads/public_downloads"), Long.valueOf(id));

                return getDataColumn(this, contentUri, null, null);
            }
            // MediaProvider
            else if ("com.android.providers.media.documents".equals(uri.getAuthority())) {
                final String docId = DocumentsContract.getDocumentId(uri);
                final String[] split = docId.split(":");
                final String type = split[0];

                Uri contentUri = null;
                if ("image".equals(type)) {
                    contentUri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI;
                } else if ("video".equals(type)) {
                    contentUri = MediaStore.Video.Media.EXTERNAL_CONTENT_URI;
                } else if ("audio".equals(type)) {
                    contentUri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;
                }

                final String selection = "_id=?";
                final String[] selectionArgs = new String[] {
                        split[1]
                };

                return getDataColumn(this, contentUri, selection, selectionArgs);
            }
        }
        // MediaStore (and general)
        else if ("content".equalsIgnoreCase(uri.getScheme())) {

            // Return the remote address
            if ("com.google.android.apps.photos.content".equals(uri.getAuthority()))
                return uri.getLastPathSegment();

            return getDataColumn(this, uri, null, null);
        }
        // File
        else if ("file".equalsIgnoreCase(uri.getScheme())) {
            return uri.getPath();
        } else if ("com.estrongs.files".equals(uri.getAuthority())) {
            return uri.getPath();
        }
        return null;
    }

    public static String getDataColumn(Context context, Uri uri, String selection, String[] selectionArgs) {

        Cursor cursor = null;
        final String column = "_data";
        final String[] projection = { column };
        try {
            cursor = context.getContentResolver().query(uri, projection, selection, selectionArgs, null);
            if (cursor != null && cursor.moveToFirst()) {
                final int index = cursor.getColumnIndexOrThrow(column);
                return cursor.getString(index);
            }
        } finally {
            if (cursor != null)
                cursor.close();
        }
        return null;
    }
}