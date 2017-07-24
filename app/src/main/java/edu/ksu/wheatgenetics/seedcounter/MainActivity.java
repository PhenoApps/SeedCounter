package edu.ksu.wheatgenetics.seedcounter;

import org.opencv.android.BaseLoaderCallback;
import org.opencv.android.CameraBridgeViewBase.CvCameraViewFrame;
import org.opencv.android.LoaderCallbackInterface;
import org.opencv.android.OpenCVLoader;
import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.android.CameraBridgeViewBase;
import org.opencv.android.CameraBridgeViewBase.CvCameraViewListener2;
import org.opencv.core.MatOfPoint;
import org.opencv.core.Point;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;
import org.opencv.imgproc.Moments;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.annotation.NonNull;
import android.support.design.widget.NavigationView;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.ActionBarDrawerToggle;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.util.Pair;
import android.view.Menu;
import android.view.MenuItem;
import android.view.SurfaceView;
import android.view.View;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.Toast;

import java.util.ArrayList;

public class MainActivity extends AppCompatActivity implements CvCameraViewListener2 {
    private static final String TAG = "OCVSample::Activity";

    private Mat firstFrame;

    Mat mCrossPattern;

    final Scalar mCircleColor = new Scalar(100,0,100);
    final Scalar mContourColor = new Scalar(255, 0, 0);
    final Scalar mContourMomentsColor = new Scalar(0, 0, 0);

    private int numSeeds = 0;
    private int areaL = 808;
    private int areaH = 7272;
    private int fc = 0;
    private int maxSeedsNum = 5000;
    private int avgFlowRate = 0;
    private int avgFlowCount = 0;
    private int totalFlow = 0;
    private int avgArea = 0;
    private int avgAreaCount = 0;
    private int totalArea = 0;

    ArrayList<Seed> mSeeds;

    private CameraBridgeViewBase mOpenCvCameraView;
    private boolean mIsJavaCamera = true;
    private MenuItem mItemSwitchCamera = null;

    private ActionBarDrawerToggle mDrawerToggle;
    private DrawerLayout mDrawerLayout;
    NavigationView nvDrawer;

    private BaseLoaderCallback mLoaderCallback = new BaseLoaderCallback(this) {
        @Override
        public void onManagerConnected(int status) {
            switch (status) {
                case LoaderCallbackInterface.SUCCESS: {
                    Log.i(TAG, "OpenCV loaded successfully");
                    mOpenCvCameraView.enableView();
                    mCrossPattern = Imgproc.getStructuringElement(Imgproc.MORPH_CROSS, new Size(5, 5));
                }
                break;
                default: {
                    super.onManagerConnected(status);
                }
                break;
            }
        }
    };

    public MainActivity() {
        Log.i(TAG, "Instantiated new " + this.getClass());
    }

    /**
     * Called when the activity is first created.
     */
    @Override
    public void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_main);

        mDrawerLayout = (DrawerLayout) findViewById(R.id.drawer_layout);

        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle(null);
            getSupportActionBar().getThemedContext();
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setHomeButtonEnabled(true);
        }

        nvDrawer = (NavigationView) findViewById(R.id.nvView);

        setupDrawerContent(nvDrawer);
        setupDrawer();

        final SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(this);
        final boolean tutorialMode = sharedPref.getBoolean(SettingsActivity.TUTORIAL_MODE, true);

        if (tutorialMode)
            launchIntro();

        firstFrame = null;
        mSeeds = new ArrayList<>();

        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        mOpenCvCameraView = (CameraBridgeViewBase) findViewById(R.id.surfaceView);
        mOpenCvCameraView.setMaxFrameSize(320, 440);
        mOpenCvCameraView.setVisibility(SurfaceView.VISIBLE);
        mOpenCvCameraView.setCvCameraViewListener(this);
    }

    @Override
    public void onPause() {
        super.onPause();
        if (mOpenCvCameraView != null)
            mOpenCvCameraView.disableView();
    }

    @Override
    public void onResume() {
        super.onResume();
        if (!OpenCVLoader.initDebug()) {
            Log.d(TAG, "Internal OpenCV library not found. Using OpenCV Manager for initialization");
            OpenCVLoader.initAsync(OpenCVLoader.OPENCV_VERSION_3_0_0, this, mLoaderCallback);
        } else {
            Log.d(TAG, "OpenCV library found inside package. Using it!");
            mLoaderCallback.onManagerConnected(LoaderCallbackInterface.SUCCESS);
        }
    }

    public void onDestroy() {
        super.onDestroy();
        if (mOpenCvCameraView != null)
            mOpenCvCameraView.disableView();
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

    public void launchIntro() {

        new Thread(new Runnable() {
            @Override
            public void run() {

                //  Launch app intro
                final Intent i = new Intent(MainActivity.this, IntroActivity.class);

                runOnUiThread(new Runnable() {
                    @Override public void run() {
                        startActivity(i);
                    }
                });


            }
        }).start();
    }

    public void onCameraViewStarted(int width, int height) {
    }

    public void onCameraViewStopped() {
    }

    public Mat onCameraFrame(CvCameraViewFrame inputFrame) {

        try {
            Mat frame = inputFrame.gray();

            Imgproc.GaussianBlur(frame, frame, new Size(5, 5), 0);

            if (firstFrame == null) firstFrame = frame;

            Core.absdiff(firstFrame, frame, frame);

            final double retVal = Imgproc.threshold(frame, frame, 50.0, 255.0, Imgproc.THRESH_BINARY);

            //Mat.ones(3, 3, CvType.CV_8U);

            Imgproc.morphologyEx(frame, frame, Imgproc.MORPH_OPEN, mCrossPattern);

            ArrayList<MatOfPoint> contours = new ArrayList<>();
            Imgproc.findContours(frame, contours, new Mat(), Imgproc.RETR_TREE, Imgproc.CHAIN_APPROX_SIMPLE);

            for(Seed s : mSeeds) {
                double px = s.cx;
                double py = s.cy + 34;
                if (avgFlowRate > 0)
                    py = s.cy + 0.8 * avgFlowRate;
                s.px = px;
                s.py = py;
                if (!s.done) {
                    s.ageOne();
                    if (s.age == s.maxAge) {
                        numSeeds -= 1;
                        s.done = true;
                    }
                }
            }

            Imgproc.drawContours(frame, contours, -1, mContourColor, 1);

            for (MatOfPoint mp : contours) {
                final double area = Imgproc.contourArea(mp);
                if (area < areaL || area > areaH) continue;
                final Moments moments = Imgproc.contourMoments(mp);
                final double cx = moments.get_m10() / moments.get_m00();
                final double cy = moments.get_m01() / moments.get_m00();
                Imgproc.circle(frame, new Point(cx, cy), 12, mContourMomentsColor, -1);

                boolean newSeed = true;
                double minDist = 9999.0;
                Seed minSeed = null;

                for (Seed s : mSeeds) {
                    if (cy > s.cy - 10 && s.state<3 && s.fc < fc) {
                        double dst = Math.sqrt((s.px-cx)*(s.px-cx)+(s.py-cy)*(s.py-cy));
                        if (dst < minDist) {
                            minDist = dst;
                            minSeed = s;
                            newSeed = false;
                        }
                    }
                }

                if (minDist > 120) newSeed = true;

                if (!newSeed) {
                    minSeed.fc = fc;
                    avgFlowCount += 1;
                    totalFlow += cy - minSeed.cy;
                    avgFlowRate = totalFlow / avgFlowCount;
                    avgAreaCount += 1;
                    totalArea += area;
                    avgArea = totalArea / avgAreaCount;

                    minSeed.updateCoords(cx, cy);
                    minSeed.px = cx;
                    minSeed.py = cy;

                }
                if (newSeed && cy <= 600) {
                    mSeeds.add(mSeeds.size(),
                            new Seed(mSeeds.size(), cx, cy, 16, fc));
                    numSeeds++;
                }

            for (Seed s : mSeeds) {
                s.state = 1;
                if (s.fc < fc) {
                    double px, py;
                    if (s.cy < 600 && s.cx < 2000 && s.cy > 0 && s.cx > 0) {
                        px = s.px;
                        py = s.py;
                        s.cx = px;
                        s.cy = py;

                        s.fc = fc;
                        s.state = 2;

                        Imgproc.circle(frame, new Point(s.cx, s.cy), 10, mCircleColor, -1);
                    } else {
                        px = s.cx;
                        py = frame.height() - 1;
                        s.updatePredictions(px, py);
                        s.done = true;
                        s.state = 3;
                    }
                }
            }

            /*for(Seed s : mSeeds) {
                Size size = Imgproc.getTextSize(String.valueOf(s.sid), Core.FONT_HERSHEY_SIMPLEX, 0.5, 1, null);
                Imgproc.putText(frame, String.valueOf(s.sid), new Point(s.cx-size.width/2, s.cy+size.height/2), Core.FONT_HERSHEY_SIMPLEX, 0.5, new Scalar(100,100,0), 1);
            }
            Imgproc.putText(frame, "Count: " + numSeeds, new Point(10, 600-20), Core.FONT_HERSHEY_SIMPLEX, 0.8, new Scalar(255,0,0), 1);*/

            }
            return frame;
        } catch (Exception e) {
            e.printStackTrace();
        }

        return inputFrame.rgba();
    }

    private class Seed {

        ArrayList<Pair<Double, Double>> tracks;
        int sid, state, fc, age, updates, maxAge;
        double cx, cy, px, py;
        boolean done;

        public Seed(int sid, double cx, double cy, int maxAge, int fc) {

            tracks = new ArrayList<>();
            this.px = 0;
            this.py = 0;
            this.sid = sid;
            this.cx = cx;
            this.cy = cy;
            this.state = 0;
            this.maxAge = maxAge;
            this.fc = fc;
            this.age = 0;
            this.updates = 0;
            this.done = false;
        }

        public void updateCoords(double xn, double yn) {

            this.age = 0;
            this.tracks.add(tracks.size(), new Pair<Double, Double>(cx, cy));
            this.px = this.cx;
            this.py = this.cy;
            this.cx = xn;
            this.cy = yn;
            this.updates += 1;
        }

        public void updatePredictions(double xn, double yn) {
            tracks.add(new Pair<Double, Double>(this.cx, this.cy));
            this.px = this.cx;
            this.py = this.cy;
            this.cx = xn;
            this.cy = yn;
        }

        public boolean ageOne() {
            this.age += 1;
            if (age > maxAge) done = true;
            return true;
        }
    }
}