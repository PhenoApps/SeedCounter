package edu.ksu.wheatgenetics.seedcounter;

import android.content.ContentUris;
import android.content.Context;
import android.database.Cursor;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Environment;
import android.provider.DocumentsContract;
import android.provider.MediaStore;
import android.util.Log;
import android.widget.ListView;
import android.widget.Toast;

import com.github.hiteshsondhi88.libffmpeg.ExecuteBinaryResponseHandler;
import com.github.hiteshsondhi88.libffmpeg.FFmpeg;
import com.github.hiteshsondhi88.libffmpeg.LoadBinaryResponseHandler;
import com.github.hiteshsondhi88.libffmpeg.exceptions.FFmpegCommandAlreadyRunningException;
import com.github.hiteshsondhi88.libffmpeg.exceptions.FFmpegNotSupportedException;

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

    private Context ctx;

    private Progress mProgress;

    private int mFrameCount;

    OutputCallback callback;

    Job(Progress p, Context ctx, OutputCallback outputCallback) {

        this.ctx = ctx;

        mFrameCount = 0;

        mPrevFrameCount = 0;

        SeedCounter.SeedCounterParams params =
                new SeedCounter.SeedCounterParams(200, 16000, 34,
                        0.25, 4.0);

        mSeedCounter = new SeedCounter(params);

        mProgress = p;

        callback = outputCallback;

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


    private void executeProcessing(final String path, final String nextFramePath, final File dir) {

        mProgress.setProgress("SLICING");

        callback.outputEvent(mProgress);

        final FFmpeg mMpeg = FFmpeg.getInstance(ctx);

        try {
            mMpeg.loadBinary(new LoadBinaryResponseHandler() {

                @Override
                public void onStart() {}

                @Override
                public void onFailure() {}

                @Override
                public void onSuccess() {
                    try {
                        mMpeg.execute(

                                //ffmpeg commands
                                new String[]{"-i", path, nextFramePath},

                                //class to control messages sent by ffmpeg lib
                                new ExecuteBinaryResponseHandler() {

                                    @Override
                                    public void onSuccess(String msg) {

                                        mProgress.setProgress("COUNTING");

                                        callback.outputEvent(mProgress);

                                        nextFrame(dir, mFrameCount);

                                    }

                                    @Override
                                    public void onProgress(String msg) {

                                        //publishProgress(msg);

                                        if (msg.contains("frame=")) {
                                            mFrameCount = Integer.valueOf(
                                                    msg.split("frame=\\s+")[1].split(" ")[0]
                                            );
                                        }

                                    }
                                }
                        );
                    } catch (FFmpegCommandAlreadyRunningException e) {
                        Log.d("DEBUG", "command already running");
                    }
                }

                @Override
                public void onFinish() {}
            });
        } catch (FFmpegNotSupportedException e) {
            // Handle if FFmpeg is not supported by device
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


            Thread t = new Thread(new Runnable() {
                @Override
                public void run() {
                    for (int i = 0; i < nextFrames.length; i++) {

                        mSeedCounter.process(
                                BitmapFactory.decodeFile(
                                        nextFrames[i].getPath()));

                    }
                    mProgress.setProgress(String.valueOf(
                            mSeedCounter.getNumSeeds()
                    ));

                    callback.outputEvent(mProgress);
                }
            });

            Log.d("THREAD", "START");
            t.start();



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
