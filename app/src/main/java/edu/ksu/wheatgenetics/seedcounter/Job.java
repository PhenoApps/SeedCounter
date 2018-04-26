package edu.ksu.wheatgenetics.seedcounter;

import android.content.ContentUris;
import android.content.Context;
import android.database.Cursor;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Environment;
import android.provider.DocumentsContract;
import android.provider.MediaStore;
import android.util.Log;
import android.widget.ListView;
import android.widget.Toast;

import com.github.hiteshsondhi88.libffmpeg.ExecuteBinaryResponseHandler;
import com.github.hiteshsondhi88.libffmpeg.FFmpeg;
import com.github.hiteshsondhi88.libffmpeg.exceptions.FFmpegCommandAlreadyRunningException;

import java.io.File;

/**
 * Created by chaneylc on 4/26/18.
 */

class Job {

    interface OutputCallback {
        void outputEvent(Progress p);
        void errorEvent(int error);
    }

    private int mPrevFrameCount;

    private SeedCounter mSeedCounter;

    private FFmpeg mMpeg;

    private Progress mProgress;

    OutputCallback callback;

    Job(Progress p, FFmpeg ffmpeg) {

        this.mMpeg = ffmpeg;

        mPrevFrameCount = 0;

        SeedCounter.SeedCounterParams params =
                new SeedCounter.SeedCounterParams(200, 16000, 34,
                        0.25, 4.0);

        mSeedCounter = new SeedCounter(params);

        mProgress = p;

        startProcessing(mProgress.getPath());
    }

    //creates and empties a temporary directory
    private void startProcessing(final String path) {

        if (isExternalStorageWritable()) {

            File vidFile = new File(path);
            final File tempDir = new File(vidFile.getParent() + "temp" + System.nanoTime());

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

                mPrevFrameCount = 0;

                executeProcessing(path, nextFramePath, tempDir);


            } else if (!tempDir.mkdirs()) {
                callback.errorEvent(R.string.error_temp_dir_creation);
            }
        } else {
            callback.errorEvent(R.string.error_storage);
        }
    }


    private void executeProcessing(final String path, String nextFramePath, final File dir) {

        try {
            mMpeg.execute(

                //ffmpeg commands
                new String[]{"-i", path, nextFramePath},

                //class to control messages sent by ffmpeg lib
                new ExecuteBinaryResponseHandler() {

                    @Override
                    public void onSuccess(String msg) {

                        mProgress.setProgress(String.valueOf(
                                mSeedCounter.getNumSeeds()
                        ));

                        callback.outputEvent(mProgress);
                    }

                    @Override
                    public void onProgress(String msg) {

                        //publishProgress(msg);

                        if (msg.contains("frame=")) {
                            int frameCount = Integer.valueOf(
                                    msg.split("frame=\\s+")[1].split(" ")[0]
                            );
                            nextFrame(dir, frameCount);
                        }

                        mProgress.setProgress(String.valueOf(
                                mSeedCounter.getNumSeeds()
                        ));

                        callback.outputEvent(mProgress);

                        Log.d("PROGRESS", msg);
                    }
                }
            );
        } catch (FFmpegCommandAlreadyRunningException e) {
            Log.d("DEBUG", "command already running");
        }

    }

    private void nextFrame(final File dir, final int frameCount) {

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


            }
        }

    }

    private static boolean isExternalStorageWritable() {

        String state = Environment.getExternalStorageState();
        if (Environment.MEDIA_MOUNTED.equals(state)) {
            return true;
        }
        return false;
    }
}
