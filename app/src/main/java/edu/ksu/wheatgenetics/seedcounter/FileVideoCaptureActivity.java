package edu.ksu.wheatgenetics.seedcounter;

import android.content.ContentUris;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Matrix;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.preference.PreferenceManager;
import android.provider.DocumentsContract;
import android.provider.MediaStore;
import android.provider.Settings;
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
import org.opencv.android.Utils;
import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfPoint;
import org.opencv.core.Point;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;
import org.opencv.imgproc.Moments;

import java.io.File;
import java.util.ArrayList;
import java.util.Queue;
import java.util.Timer;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import static android.content.pm.PackageManager.PERMISSION_DENIED;

public class FileVideoCaptureActivity extends AppCompatActivity {

    private static final String TAG = "SeedCounter";

    static {System.loadLibrary("opencv_java3");}

    final ScheduledExecutorService mScheduler =
            Executors.newScheduledThreadPool(1);

    private Bitmap mBitmap;

    //seed counter utility
    private SeedCounter mSeedCounter;

    //android views
    private ImageView mSurfaceView;
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
        mSeedCounter = new SeedCounter(params);

        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        mSurfaceView = (ImageView) findViewById(R.id.surfaceView);
        mSurfaceView.setEnabled(true);
        mSurfaceView.setVisibility(ImageView.VISIBLE);

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
                    final String filePath = callingIntent.getStringExtra(SeedCounterConstants.FILE_PATH_EXTRA);
                    scheduleFFmpeg(filePath, true);
                } else {
                    final Intent i = new Intent(Intent.ACTION_GET_CONTENT);
                    i.setType("*/*");
                    startActivityForResult(Intent.createChooser(i, "Choose file to import."),
                            SeedCounterConstants.LOAD_REQUEST);
                }
            }

        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent intent) {
        super.onActivityResult(requestCode, resultCode, intent);

        if (requestCode == SeedCounterConstants.LOAD_REQUEST) {
            if (resultCode == RESULT_OK) {

                String path = getPath(intent.getData());

                scheduleFFmpeg(path, false);
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

    public void nextFrame(Bitmap inputFrame, boolean fromCall) {

        final Bitmap retBitmap;

        if (fromCall) {
            //Matrix matrix = new Matrix();
           // matrix.postRotate(-90);

            retBitmap = mSeedCounter.process(
                    Bitmap.createBitmap(inputFrame, 0, 0,
                            inputFrame.getWidth(), inputFrame.getHeight(), null, true));

        } else retBitmap = mSeedCounter.process(inputFrame);

        this.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mTextView.setText(String.valueOf(mSeedCounter.getNumSeeds()));
                mSurfaceView.setImageBitmap(retBitmap);
                mSurfaceView.invalidate();
            }
        });
    }

    private void scheduleFFmpeg(final String path, final boolean fromCall) {

        if (isExternalStorageWritable()) {

            final File seedCounterDir = new File(Environment.getExternalStorageDirectory(), "SeedCounter");

            final File[] children = seedCounterDir.listFiles();
            for (File f : children) f.delete();

            FFmpeg ffmpeg = FFmpeg.getInstance(this);

            if (seedCounterDir.exists()) {
                final String nextFramePath = seedCounterDir.getPath() + "/temp.bmp";
                final ArrayList<String> cmd = new ArrayList<>();
                cmd.add(cmd.size(), "-i");
                cmd.add(cmd.size(), path);
                cmd.add(cmd.size(), "-vf");
                cmd.add(cmd.size(), "fps=60");
                cmd.add(cmd.size(), "-updatefirst");
                cmd.add(cmd.size(), "1");
                cmd.add(cmd.size(), nextFramePath);
                final String[] arrayCommands = cmd.toArray(new String[]{});
                try {
                    ffmpeg.execute(
                            arrayCommands,
                            new ExecuteBinaryResponseHandler() {
                                @Override
                                public void onSuccess(String msg) {
                                    Log.d("DEBUG", msg);
                                }

                                @Override
                                public void onFailure(String msg) {
                                   // Log.d("DEBUG", msg);
                                }

                                @Override
                                public void onProgress(String msg) {
                                   // Log.d("DEBUG", msg);
                                }
                            });
                } catch (FFmpegCommandAlreadyRunningException e) {
                    Log.d("DEBUG", "command already running");
                }

                mScheduler.scheduleAtFixedRate(new Runnable() {
                    @Override
                    public void run() {
                        final File f = new File(nextFramePath);
                        //Log.d("FLENGTH", String.valueOf(f.getFreeSpace()) + ":" + String.valueOf(f.getTotalSpace()) + ":" + String.valueOf(f.getUsableSpace()) + ":" + String.valueOf(f.length()));
                        if (f.exists()) {
                            mBitmap = BitmapFactory.decodeFile(f.getPath());

                            if (mBitmap != null) {

                                //naive check ensuring mBitmap is not currently being
                                //written too (checks if most of the first 16 pixels are black)
                                int[] isCorrupted = new int[16];
                                int total = 0;
                                for (int i = 0; i < 16; i = i + 1) {
                                    if (mBitmap.getPixel(i, 0) == Color.BLACK)
                                        isCorrupted[i] = 1;
                                }
                                for (int i : isCorrupted)
                                    total += i;
                                if (total < 8) {
                                    final Bitmap newBitmap =
                                            Bitmap.createBitmap(mBitmap);
                                    nextFrame(newBitmap, fromCall);
                                }
                            }
                        }
                    }
                }, 0, 1, TimeUnit.MICROSECONDS);

            } else if (!seedCounterDir.mkdirs())
                Log.d("SeedCounterFS", "failed to create Seedcounter directory");
        } else Toast.makeText(this, "Default storage not writable.", Toast.LENGTH_LONG).show();
    }

    public boolean isExternalStorageWritable() {

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