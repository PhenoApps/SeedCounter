package edu.ksu.wheatgenetics.seedcounter;

import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.view.MenuItem;

public class SettingsActivity extends AppCompatActivity {

    public static String DEBUG_MODE = "edu.ksu.wheatgenetics.seedcounter.DEBUG_MODE";
    public static String TUTORIAL_MODE = "edu.ksu.wheatgenetics.seedcounter.TUTORIAL_MODE";
    public static String PARAM_AREA_LOW = "edu.ksu.wheatgenetics.seedcounter.AREA_LOW";
    public static String PARAM_AREA_HIGH = "edu.ksu.wheatgenetics.seedcounter.AREA_HIGH";
    public static String PARAM_DEFAULT_RATE = "edu.ksu.wheatgenetics.seedcounter.DEFAULT_RATE";
    public static String PARAM_SIZE_LOWER_BOUND_RATIO =
            "edu.ksu.wheatgenetics.seedcounter.SIZE_LOWER_BOUND_RATIO";
    public static String PARAM_NEW_SEED_DIST_RATIO =
            "edu.ksu.wheatgenetics.seedcounter.NEW_SEED_DIST_RATIO";

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if(getSupportActionBar() != null){
            getSupportActionBar().setTitle(null);
            getSupportActionBar().getThemedContext();
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setHomeButtonEnabled(true);
        }

        getFragmentManager().beginTransaction()
                .replace(android.R.id.content, new SettingsFragment())
                .commit();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {

        switch (item.getItemId()) {
            case android.R.id.home:
                setResult(RESULT_OK);
                finish();
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }
}