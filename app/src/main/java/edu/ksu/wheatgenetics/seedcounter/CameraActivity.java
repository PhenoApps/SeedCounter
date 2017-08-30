package edu.ksu.wheatgenetics.seedcounter;

import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;

/**
 * Created by chaneylc on 8/24/2017.
 */

public class CameraActivity extends AppCompatActivity {

    @Override
    public void onCreate(Bundle savedInstance) {
        super.onCreate(savedInstance);

        setContentView(R.layout.activity_camera);

        getFragmentManager().beginTransaction()
                .replace(R.id.container, Camera2BasicFragment.newInstance())
                .commit();
    }
}
