package edu.ksu.wheatgenetics.seedcounter;

import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.BitmapFactory;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.preference.PreferenceManager;
import android.support.annotation.NonNull;
import android.support.design.widget.NavigationView;
import android.support.v4.app.ActivityCompat;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.ActionBarDrawerToggle;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ListAdapter;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.github.hiteshsondhi88.libffmpeg.ExecuteBinaryResponseHandler;
import com.github.hiteshsondhi88.libffmpeg.FFmpeg;
import com.github.hiteshsondhi88.libffmpeg.exceptions.FFmpegCommandAlreadyRunningException;

import org.opencv.android.BaseLoaderCallback;
import org.opencv.android.LoaderCallbackInterface;
import org.opencv.videoio.VideoCapture;
import org.w3c.dom.Text;

import java.io.File;
import java.util.ArrayList;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

import static android.content.pm.PackageManager.PERMISSION_DENIED;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "SeedCounter";

    //android views
    private ActionBarDrawerToggle mDrawerToggle;
    private DrawerLayout mDrawerLayout;
    private NavigationView mNavView;

    private int mPrevFrameCount;

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

    private FFmpeg mMpeg;

    final SeedCounter.SeedCounterParams params =
            new SeedCounter.SeedCounterParams(200, 16000, 34, 0.25, 4.0);

    final private SeedCounter mSeedCounter = new SeedCounter(params);

    @Override
    public void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_main);

        initializeUI();

        mMpeg = FFmpeg.getInstance(this);

        ActivityCompat.requestPermissions(this, SeedCounterConstants.permissions, SeedCounterConstants.PERM_REQUEST);
    }


    private void initializeUI() {

        mDrawerLayout = (DrawerLayout) findViewById(R.id.drawer_layout);

        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle(null);
            getSupportActionBar().getThemedContext();
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setHomeButtonEnabled(true);
        }

        mNavView = (NavigationView) findViewById(R.id.nvView);
        setupDrawerContent(mNavView);
        setupDrawer();

        ((Button) findViewById(R.id.newJobButton)).setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View view) {
                AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this);

                builder.setTitle(R.string.choose_job_type);

                builder.setPositiveButton("File", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        startActivity(new Intent(MainActivity.this, FileVideoCaptureActivity.class));
                    }
                });

                builder.setNegativeButton("Camera", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        startActivityForResult(
                                new Intent(MainActivity.this, CameraActivity.class),
                                SeedCounterConstants.CAMERA_FILE_REQUEST);
                    }
                });

                builder.show();
            }
        });

        ((ListView) findViewById(R.id.listView)).setAdapter(new ArrayAdapter<String>(this, R.layout.row));
    }


    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent intent) {

        if (resultCode == SeedCounterConstants.CAMERA_RESULT_SUCCESS) {
            enqueue((String) intent.getExtras()
                    .get(SeedCounterConstants.FILE_PATH_EXTRA));
        }
    }

    private void enqueue(String filePath) {

        ListView list = (ListView) findViewById(R.id.listView);
        ArrayAdapter<String> updatedAdapter = new ArrayAdapter<>(this, R.layout.row);
        final int oldSize = list.getAdapter().getCount();

        for (int i = 0; i < oldSize; i++) {
            final String temp = (String) list.getAdapter().getItem(i);
            updatedAdapter.add(temp);
        }
        updatedAdapter.add(filePath);
        list.setAdapter(updatedAdapter);

        startProcessing(filePath);
    }

    private void setupDrawer() {

        mDrawerToggle = new ActionBarDrawerToggle(this, mDrawerLayout,
                R.string.drawer_open, R.string.drawer_close) {

            public void onDrawerOpened(View drawerView) {
                View view = MainActivity.this.getCurrentFocus();
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
                final Intent settings_intent = new Intent(this, SettingsActivity.class);
                startActivity(settings_intent);
                break;
            case R.id.nav_export:
                break;
            case R.id.nav_about:
                Toast.makeText(this, "About", Toast.LENGTH_SHORT).show();
                break;
            case R.id.nav_intro:
                final Intent intro_intent = new Intent(MainActivity.this, IntroActivity.class);
                runOnUiThread(new Runnable() {
                    @Override public void run() {
                        startActivity(intro_intent);
                    }
                });
                break;
        }

        mDrawerLayout.closeDrawers();
    }


    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] result) {

        if (requestCode == SeedCounterConstants.PERM_REQUEST) {
            final int size = permissions.length;
            for (int i = 0; i < size; i = i + 1) {
                //if permissions are denied close and restart the app
                if (result[i] == PERMISSION_DENIED) {
                    Toast.makeText(this,
                            "You must accept all permissions to use SeedCounter", Toast.LENGTH_LONG).show();
                    finish();
                }
            }
        }
    }

    private void startProcessing(final String path) {

        //first get fps and time information from the input file.
        try {
            mMpeg.execute(

                    new String[]{"-i", path},

                    new ExecuteBinaryResponseHandler() {

                        @Override
                        public void onProgress(String msg) {

                            Log.d("PROG", msg);

                            if (msg.contains("Duration")) {

                                parseVideoInfo(msg);

                                new ExtractFramesTask().execute(path);

                                mMpeg.killRunningProcesses();
                            }
                        }
                    });

        } catch(FFmpegCommandAlreadyRunningException e) {
            e.printStackTrace();
        }

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

                    for (int i = 0; i < nextFrames.length; i++) {

                        mSeedCounter.process(
                                BitmapFactory.decodeFile(
                                        nextFrames[i].getPath()));

                        final TextView tv = (TextView) findViewById(R.id.textView);

                        tv.post(new Runnable() {
                            public void run() {
                                tv.setText(String.valueOf(
                                        mSeedCounter.getNumSeeds()
                                ));
                            }
                        });

                        tv.postInvalidate();
                    }
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

    /*private void updateFFmpegMsgView(final String progress) {
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
    }*/

    private void parseVideoInfo(String msg) {

        /*String[] time = msg.split("Duration: ")[1].split(",")[0].split(":");
        mTotalSeconds = Integer.valueOf(time[0]) * 360
                + Integer.valueOf(time[1]) * 60
                + Double.valueOf(time[2]);*/

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

                if (tempDir.exists()) {

                    String nextFramePath = tempDir.getPath() + "/temp%d.jpg";
                    try {
                        mMpeg.execute(

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

            } else Toast.makeText(MainActivity.this,
                    "Default storage not writable.", Toast.LENGTH_LONG).show();

            return null;
        }

        /*protected void onProgressUpdate(String... progress) {
            if (progress[0].contains("time="))
                updateFFmpegMsgView(progress[0]);
        }*/

    }

    private static boolean isExternalStorageWritable() {

        String state = Environment.getExternalStorageState();
        if (Environment.MEDIA_MOUNTED.equals(state)) {
            return true;
        }
        return false;
    }

}