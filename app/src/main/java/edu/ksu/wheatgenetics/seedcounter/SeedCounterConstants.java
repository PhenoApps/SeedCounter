package edu.ksu.wheatgenetics.seedcounter;

import android.Manifest;

/**
 * Created by Chaney on 8/9/2017.
 */

class SeedCounterConstants {

    final static String[] permissions = new String[] {
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.WRITE_EXTERNAL_STORAGE,
            Manifest.permission.CAMERA
    };

    //requests
    final static int PERM_REQUEST = 101;
    final static int LOAD_REQUEST = 100;
    final static int CAMERA_FILE_REQUEST = 102;

    //results
    final static int CAMERA_RESULT_SUCCESS = 200;

    //extras
    final static String FILE_PATH_EXTRA = "edu.ksu.wheatgenetics.seedcounter.FILE_URI_EXTRA";

    //Messages
    static final String MESSAGE_JOB_FILEPATH = "edu.ksu.wheatgenetics.seedcounter.MESSAGE_JOB_FILEPATH";
}
