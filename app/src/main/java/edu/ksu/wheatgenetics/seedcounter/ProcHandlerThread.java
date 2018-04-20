package edu.ksu.wheatgenetics.seedcounter;

import android.content.Context;
import android.graphics.BitmapFactory;
import android.os.AsyncTask;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;
import android.util.Log;
import android.util.Pair;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.Toast;

import com.github.hiteshsondhi88.libffmpeg.ExecuteBinaryResponseHandler;
import com.github.hiteshsondhi88.libffmpeg.FFmpeg;
import com.github.hiteshsondhi88.libffmpeg.exceptions.FFmpegCommandAlreadyRunningException;

import java.io.File;

/**
 * Created by chaneylc on 4/20/18.
 */

public class ProcHandlerThread extends HandlerThread {

    private Handler handler;

    private FFmpeg mMpegLib;

    private Context mCtx;

    private int mPrevFrameCount;

    private SeedCounter mSeedCounter;

    private String mLastPath;

    ProcHandlerThread(String name, Context ctx) {

        super(name);

        mCtx = ctx;

        mSeedCounter = new SeedCounter(new SeedCounter.SeedCounterParams(200, 16000, 34, 0.25, 4.0));
    }

    @Override
    protected void onLooperPrepared() {

        mPrevFrameCount = 0;

        mMpegLib = FFmpeg.getInstance(mCtx);

        handler = new Handler(getLooper()) {

            @Override
            public void handleMessage(Message msg) {

                String filepath = (String) msg.getData()
                        .get(SeedCounterConstants.MESSAGE_JOB_FILEPATH);

                //load file using ffmpeg to read number of frames
                firstPassLoad(filepath);
            }
        };
    }

    private static boolean isExternalStorageWritable() {

        String state = Environment.getExternalStorageState();
        if (Environment.MEDIA_MOUNTED.equals(state)) {
            return true;
        }
        return false;
    }

    private void startVideoProcessing(String msg, String filepath) {

        /*String[] time = msg.split("Duration: ")[1].split(",")[0].split(":");
        mTotalSeconds = Integer.valueOf(time[0]) * 360
                + Integer.valueOf(time[1]) * 60
                + Double.valueOf(time[2]);*/

        extractFrames(filepath);

    }

    private void firstPassLoad(final String filepath) {

        try {
            mMpegLib.execute(

                    new String[]{"-i", filepath},

                    new ExecuteBinaryResponseHandler() {

                        @Override
                        public void onProgress(String msg) {

                            Log.d("PROG", msg);

                            if (msg.contains("Duration")) {

                                startVideoProcessing(msg, filepath);

                                mMpegLib.killRunningProcesses();
                            }
                        }
                    });

        } catch(FFmpegCommandAlreadyRunningException e) {
            e.printStackTrace();
        }
    }

    /* ensures a temporary directory is created for file frames,
    begins frame extraction
     */
    private void extractFrames(String filepath) {

        if (isExternalStorageWritable()) {

            File vidFile = new File(filepath);
            final File tempDir = new File(vidFile.getParent() + "temp");

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

                extract(tempDir, filepath);

            } else if (!tempDir.mkdirs())
                Log.d("SeedCounterFS", "failed to create Seedcounter directory");

        } else Toast.makeText(mCtx, "Default storage not writable.",
                Toast.LENGTH_LONG).show();

    }

    private void onProgressUpdate(String... progress) {
        //if (progress[0].contains("time="))
         //   updateFFmpegMsgView(progress[0]);
    }

    private void extract(final File tempDir, String filepath) {

        mLastPath = filepath;
        String nextFramePath = tempDir.getPath() + "/temp%d.jpg";
        try {
            mMpegLib.execute(

                    //ffmpeg commands
                    new String[]{"-i", filepath, nextFramePath},

                    //class to control messages sent by ffmpeg lib
                    new ExecuteBinaryResponseHandler() {

                        @Override
                        public void onSuccess(String msg) {

                            Log.d("SUCCESS", msg);

                        }

                        @Override
                        public void onFailure(String msg) {

                            Log.d("FAILURE", msg);
                        }

                        @Override
                        public void onProgress(String msg) {

                            //publishProgress(msg);

                            if (msg.contains("frame=")) {
                                int frameCount = Integer.valueOf(
                                        msg.split("frame=\\s+")[1].split(" ")[0]
                                );
                                nextFrame(tempDir, frameCount);
                            }

                            Log.d("PROGRESS", msg);
                        }
                    });
        } catch (FFmpegCommandAlreadyRunningException e) {
            Log.d("DEBUG", "command already running");
        }
    }

    private void nextFrame(final File dir, final int frameCount) {

        //create a thread to start and wait until processing finishes

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

                /*mTextView.post(new Runnable() {
                    public void run() {
                        mTextView.setText(String.valueOf(
                                mSeedCounter.getNumSeeds()
                        ));
                    }
                });

                mTextView.postInvalidate();*/
            }
        }
    }

    void enqueue(String filepath) {

        Message msg = new Message();
        msg.getData().putString(
                SeedCounterConstants.MESSAGE_JOB_FILEPATH, filepath);
        this.handler.handleMessage(msg);
    }

    Pair<String, Integer> getValue() {
        return new Pair<>(mLastPath, mSeedCounter.getNumSeeds());
    };
}
