package edu.ksu.wheatgenetics.seedcounter;

/**
 * Created by chaneylc on 4/26/18.
 */

class Progress {

    private String mProgress;
    private String mId;
    private String mPath;

    Progress(String path) {

        this.mPath = path;
        this.mId = String.valueOf(System.nanoTime());
        this.mProgress = "0";
    }

    public void updateProgress(String progress) { mProgress = progress; }
    public String getProgress() { return mProgress; }
    public void setProgress(String progress) { this.mProgress = progress; }
    public String getId() { return this.mId; }
    public String getPath() { return this.mPath; }

    @Override
    public String toString() { return this.mId + "\t" + this.mProgress; }
}