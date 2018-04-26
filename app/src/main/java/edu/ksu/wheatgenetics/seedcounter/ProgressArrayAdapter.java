package edu.ksu.wheatgenetics.seedcounter;

import android.content.Context;
import android.support.annotation.NonNull;
import android.widget.ArrayAdapter;

import java.util.List;

/**
 * Created by chaneylc on 4/26/18.
 */

public class ProgressArrayAdapter extends ArrayAdapter {

    public ProgressArrayAdapter(@NonNull Context context, int resource, List<Progress> list) {
        super(context, resource, list);
    }
}
