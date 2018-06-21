package edu.ksu.wheatgenetics.seedcounter;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.AsyncTask;
import android.os.Environment;
import android.util.Log;

import com.github.hiteshsondhi88.libffmpeg.ExecuteBinaryResponseHandler;
import com.github.hiteshsondhi88.libffmpeg.FFmpeg;
import com.github.hiteshsondhi88.libffmpeg.LoadBinaryResponseHandler;
import com.github.hiteshsondhi88.libffmpeg.exceptions.FFmpegCommandAlreadyRunningException;
import com.github.hiteshsondhi88.libffmpeg.exceptions.FFmpegNotSupportedException;

import java.io.File;

/**
 * Created by chaneylc on 4/26/18.
 */

class Job extends AsyncTask<Void, String, Void> {

    //async background function for loading ffmpeg binary and splitting file into frames
    @Override
    protected Void doInBackground(Void... voids) {

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

                                //ffmpeg commands, slices a video and interpolates to 60 fps
                                //new String[] {"-y", "-i", mProgress.getPath(), "-filter_complex", "[0:v]setpts=1.5*PTS[v]", "-map", "[v]", "-b:v", "2097k", "-r", "60", mTempDir.getPath() + "/temp%d.jpg"},
                                //new String[] {"-i", mProgress.getPath(), "-filter", "minterpolate='mi_mode=mci:mc_mode=aobmc:vsbmc=1:fps=120'", mTempDir.getPath() + "/temp%d.jpg"},
                                new String[]{"-i", mProgress.getPath(), "-r", "30", mTempDir.getPath() + "/temp%d.jpg"},

                                //class to control messages sent by ffmpeg lib
                                new ExecuteBinaryResponseHandler() {

                                    @Override
                                    public void onSuccess(String msg) {

                                        nextFrame(mTempDir, mFrameCount);

                                    }

                                    @Override
                                    public void onProgress(String msg) {

                                        //publishProgress(msg);
                                        Log.d("Slice progress", msg);
                                        if (msg.contains("frame=")) {
                                            mFrameCount = Integer.valueOf(
                                                    msg.split("frame=\\s+")[1].split(" ")[0]
                                            );

                                            onProgressUpdate(String.valueOf(mFrameCount));
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
        return null;
    }


    //updates the progress for loading/slicing a video
    @Override
    protected void onProgressUpdate(String... prog) {

         mProgress.setProgress("Loading frame: " + prog[0]);

         callback.outputEvent(mProgress);
    }

    interface OutputCallback {
        void outputEvent(Progress p);
        void errorEvent(int error);
    }

    private SeedCounter mSeedCounter;

    private Context ctx;

    private Progress mProgress;

    private File mExperimentDir;

    private File mTempDir;

    private int mFrameCount;

    OutputCallback callback;

    Job(Progress p, Context ctx, File dir, OutputCallback outputCallback) {

        this.ctx = ctx;

        mExperimentDir = dir;

        mFrameCount = 0;

        SeedCounter.SeedCounterParams params =
                new SeedCounter.SeedCounterParams(300, 1000000, 34,
                        0.25, 4.0);

        mSeedCounter = new SeedCounter(params, p.getPath());

        mProgress = p;

        callback = outputCallback;

        if (isExternalStorageWritable()) {

            File vidFile = new File(mProgress.getPath());

            String tempFolderName = "temp" + System.nanoTime();
            final File tempDir = new File(mExperimentDir, tempFolderName);

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

                mTempDir = tempDir;

            } else if (!tempDir.mkdirs()) {
                callback.errorEvent(R.string.error_temp_dir_creation);
            }
        } else {
            callback.errorEvent(R.string.error_storage);
        }

    }

    @SuppressLint("StaticFieldLeak")
    private void nextFrame(final File dir, final int frameCount) {

        if (dir.isDirectory()) {

            new AsyncTask<String, String, Void>() {

                @Override
                protected Void doInBackground(String... strings) {

                    final File[] children = dir.listFiles();

                    //Bitmap[] frames = new Bitmap[children.length];

                    mSeedCounter.process(children);

                    /*for (int i = 0; i < children.length; i++) {

                        frames[i] = BitmapFactory.decodeFile(
                                children[i].getPath()
                        );

                        /*mSeedCounter.process(
                                BitmapFactory.decodeFile(
                                        f.getPath()));

                        //onProgressUpdate(String.valueOf(mSeedCounter.getNumSeeds()));


                    }

                    mSeedCounter.process(frames);*/

                    return null;
                }

                @Override
                protected void onProgressUpdate(String... prog) {

                    mProgress.setProgress("Current count: " + String.valueOf(
                            mSeedCounter.getNumSeeds()
                    ));

                    callback.outputEvent(mProgress);

                }

                @Override
                protected void onPostExecute(Void v) {

                    mProgress.setProgress("Final count: " + String.valueOf(
                            mSeedCounter.getNumSeeds()
                    ));

                    callback.outputEvent(mProgress);
                }

            }.execute();
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
