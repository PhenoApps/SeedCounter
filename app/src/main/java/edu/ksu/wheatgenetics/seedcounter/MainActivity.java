package edu.ksu.wheatgenetics.seedcounter;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.annotation.NonNull;
import android.support.design.widget.NavigationView;
import android.support.v4.app.ActivityCompat;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.ActionBarDrawerToggle;
import android.support.v7.app.AppCompatActivity;
import android.view.MenuItem;
import android.view.View;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import org.opencv.videoio.VideoCapture;

import java.util.ArrayList;

import static android.content.pm.PackageManager.PERMISSION_DENIED;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "SeedCounter";

    //android views
    private Button mStartVideoCapture, mStartFileVideoCapture;
    private ActionBarDrawerToggle mDrawerToggle;
    private DrawerLayout mDrawerLayout;
    private NavigationView mNavView;

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

        mNavView = (NavigationView) findViewById(R.id.nvView);
        setupDrawerContent(mNavView);
        setupDrawer();

        mStartVideoCapture =
                (Button) findViewById(R.id.startVideoCapture);

        mStartFileVideoCapture =
                (Button) findViewById(R.id.startFileVideoCapture);

        //disable all buttons and setup listeners
        setupButtons();

        ActivityCompat.requestPermissions(this, SeedCounterConstants.permissions, SeedCounterConstants.PERM_REQUEST);
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

    private void setupButtons() {

        if (mStartVideoCapture != null) {
            mStartVideoCapture.setEnabled(false);
            mStartVideoCapture.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                   // startActivity(new Intent(MainActivity.this, CameraSessionActivity.class));
                    startActivity(new Intent(MainActivity.this, CameraActivity.class));
                }
            });
        }

        if (mStartFileVideoCapture != null) {
            mStartFileVideoCapture.setEnabled(false);
            mStartFileVideoCapture.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    startActivity(new Intent(MainActivity.this, FileVideoCaptureActivity.class));
                }
            });
        }
    }

    private void enableButtons() {

        if (mStartVideoCapture != null)
            mStartVideoCapture.setEnabled(true);
        if (mStartFileVideoCapture != null)
            mStartFileVideoCapture.setEnabled(true);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] result) {

        if (requestCode == SeedCounterConstants.PERM_REQUEST) {
            final int size = permissions.length;
            for (int i = 0; i < size; i = i + 1) {
                //if permissions are denied close and restart the app
                if (result[i] == PERMISSION_DENIED) {
                    Toast.makeText(this, "You must accept all permissions to use SeedCounter", Toast.LENGTH_LONG).show();
                    finish();
                }
            }

            //enable functionality once permissions are granted
            enableButtons();
        }
    }
}