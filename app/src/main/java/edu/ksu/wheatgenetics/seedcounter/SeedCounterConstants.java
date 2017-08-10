package edu.ksu.wheatgenetics.seedcounter;

import android.Manifest;

/**
 * Created by Chaney on 8/9/2017.
 */

public class SeedCounterConstants {

    final static String[] permissions = new String[] {
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.WRITE_EXTERNAL_STORAGE,
            Manifest.permission.CAMERA
    };

    //requests
    final static int PERM_REQUEST = 101;
    final static int LOAD_REQUEST = 100;
}
