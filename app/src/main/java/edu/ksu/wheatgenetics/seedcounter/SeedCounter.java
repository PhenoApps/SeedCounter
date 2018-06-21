package edu.ksu.wheatgenetics.seedcounter;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Log;

import org.opencv.android.Utils;
import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfPoint;
import org.opencv.core.Point;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.Imgproc;
import org.opencv.imgproc.Moments;

import java.io.File;
import java.util.ArrayList;
import java.util.concurrent.Semaphore;

/**
 * Created by chaneylc on 8/22/2017.
 */

public class SeedCounter {

    //opencv constants
    private final Scalar mContourColor = new Scalar(255, 0, 0);
    private final Scalar mContourMomentsColor = new Scalar(0, 0, 0);

    //seed counter algorithm variables
    private int mNumSeeds = 0;
    private int mDenoise = 50;
    private int mFrameCount = 0;
    private double mAvgFlowRate = 0;
    private int mAvgFlowCount = 0;
    private double mTotalFlow = 0;
    private double mAvgArea = 0;
    private int mAvgAreaCount = 0;
    private double mTotalArea = 0;
    private double NEW_THRESHOLD = 200;
    private Mat mFirstFrame;
    private ArrayList<Seed> mSeeds;
    private String mFile;

    //seed counter parameters
    private SeedCounterParams mParams;

    //class used to define user-tunable parameters
    static class SeedCounterParams {
        private int areaLow, areaHigh, defaultRate;
        private double sizeLowerBoundRatio, newSeedDistRatio;

        SeedCounterParams(int areaLow, int areaHigh, int defaultRate,
                                 double sizeLowerBoundRatio, double newSeedDistRatio) {
            this.areaLow = areaLow;
            this.areaHigh = areaHigh;
            this.defaultRate = defaultRate;
            this.sizeLowerBoundRatio = sizeLowerBoundRatio;
            this.newSeedDistRatio = newSeedDistRatio;
        }
        int getAreaLow() { return areaLow; }
        int getAreaHigh() { return areaHigh; }
        int getDefaultRate() { return defaultRate; }
        double getSizeLowerBoundRatio() { return sizeLowerBoundRatio; }
        double getNewSeedDistRatio() { return newSeedDistRatio; }
    }

    SeedCounter(SeedCounterParams params, String f) {
        mFirstFrame = null;
        mSeeds = new ArrayList<>();
        mParams = params;
        mFile = f;
    }

    void process(File[] frames) {



        for (File f: frames) {

            Mat frame = new Mat();
            Utils.bitmapToMat(BitmapFactory.decodeFile(
                    f.getPath()), frame);

            int videoWidth = frame.width();
            int videoHeight = frame.height();
            int downLimit = (int) (0.9 * videoHeight);

            //Log.d("SIZE", String.valueOf(videoWidth) + "X" + String.valueOf(videoHeight));


            if (videoWidth < videoHeight) Core.rotate(frame, frame, 270);

            Mat gray = new Mat();
            //convert to grayscale
            Imgproc.cvtColor(frame, gray, Imgproc.COLOR_BGR2GRAY);

            //small Gaussian blur to reduce noise
            Imgproc.GaussianBlur(gray, gray, new Size(5, 5), 0);

            //if this is the first frame, just initialize firstFrame and continue
            if (mFirstFrame == null) {
                mFirstFrame = gray;
                continue;
            }

            //otherwise, compute the absolute difference between the current and first frame
            Mat frame2 = new Mat();
            Core.absdiff(mFirstFrame, gray, frame2);

            //compute binary threshold, bits 0-denoise are black, (denoise+1) to 255 are white
            Mat thresh1 = new Mat();
            final double retVal =
                    Imgproc.threshold(frame2, thresh1, mDenoise, 255.0, Imgproc.THRESH_BINARY);

            Mat opening = new Mat();
            Imgproc.morphologyEx(thresh1, opening, Imgproc.MORPH_OPEN, Mat.ones(5, 5, CvType.CV_8U));

            //find contours and draw contour of each seed
            ArrayList<MatOfPoint> contours = new ArrayList<>();

            //detect contours
            Imgproc.findContours(opening, contours, new Mat(), Imgproc.RETR_TREE, Imgproc.CHAIN_APPROX_SIMPLE);

            //compute predicted seed locations based on previous location and average flow rate
            for (Seed s : mSeeds) {
                double px = s.cx;
                double py = s.cy;
                if (mAvgFlowRate > 0)
                    py += mAvgFlowRate;
                else
                    py += 200; //this value could be estimated based on previous runs, or just use 1
                s.px = px;
                s.py = py;
                if (!s.done) {
                    s.ageOne();
                    //if seed has aged too much while not being assigned to a contour,
                    //then kill it and remove it from the count
                    //s.age is the number of frames since the seed was assigned, and s.updates is
                    //the number of times assigned

                    if ((s.age >= s.maxAge) && (s.age > s.updates + 32)
                            && (s.updates < s.maxAge)) {
                        mNumSeeds--;
                        s.setDone();
                    }
                }
            }

            //for each contour in the current frame
            Imgproc.drawContours(frame, contours, -1, mContourColor, 1);


            //Imgcodecs.imwrite(new File(mFile).getParent() + "temp_contour" + System.nanoTime() + ".jpeg", frame);
            //Log.d("FILECHECK", new File(mFile).getParent() + "temp_contour" + System.nanoTime() + ".jpeg");

            //TODO CHECK
            if (mAvgFlowRate > 0) NEW_THRESHOLD = 4.0 * mAvgFlowRate;


            for (MatOfPoint mp : contours) {

                //get the area of every contour, that is the area of the seed
                final double area = Imgproc.contourArea(mp);

                //get the moments to compute centroid
                final Moments moments = Imgproc.moments(mp);

                Log.d("AREA", String.valueOf(area));

                //if the area is too small or too large, skip the contour
                if (area < 300 || area > 1000000) {
                    continue;
                }

                //if the object fit our requirement then draw center of contour in black
                int cx = (int) (moments.get_m10() / moments.get_m00());
                int cy = (int) (moments.get_m01() / moments.get_m00());
                Imgproc.circle(frame, new Point(cx, cy), 10, mContourMomentsColor, -1);

                //treat as a new seed unless it follows an existing seed
                boolean newSeed = true;


                //TODO CHECK
                //initialize the minimum distance as width + height, more than any Euclidean distance
                double minDist = videoWidth + videoHeight;


                //initialize the closest seed to null
                Seed minSeed = null;

                //determine if seed is new, within 5 added
                for (Seed s : mSeeds) {

                    //if seed as been removed, don't consider it
                    if (s.done) continue;

                    //if contour is below or nearly below current seed and
                    //seed is not done and not marked (s.fc ==fc) then consider it
                    if (cy > s.cy - 10 && s.state < 3 && s.fc < mFrameCount) {
                        long dst = (long) Math.sqrt((s.px - cx) * (s.px - cx)
                                + (s.py - cy) * (s.py - cy));
                        if (dst < minDist) {
                            minDist = dst;
                            minSeed = s;
                            newSeed = false;
                        }
                    }
                }

                if (minDist > NEW_THRESHOLD) newSeed = true;

                if (!newSeed) {

                    //mark the seed as assigned to a contour
                    minSeed.fc = mFrameCount;

                    mAvgFlowCount += 1;

                    //distance traveled
                    mTotalFlow += (cy - minSeed.cy);
                    mAvgFlowRate = mTotalFlow / mAvgFlowCount;
                    mAvgAreaCount += 1;
                    mTotalArea += (int) area;
                    mAvgArea = mTotalArea / mAvgAreaCount;

                    Log.d("FLOW", String.valueOf(mAvgFlowRate));
                    Log.d("AVG", String.valueOf(mAvgArea));

                    //set the current x and y values of the seed
                    minSeed.updateCoords(cx, cy);
                }
                if (newSeed && cy <= downLimit) {

                    Seed s = new Seed(mSeeds.size(), cx, cy, 64, mFrameCount);
                    s.px = cx;
                    s.py = cy;
                    //append new seed to list
                    mSeeds.add(mSeeds.size(), s);

                    mNumSeeds++;
                }

                //output current seed count and seed positions
                for (Seed s : mSeeds) {
                    s.state = 1;
                    if (s.fc < mFrameCount) {
                        double px, py;
                        if (s.cy <= downLimit && s.cy > 0 && s.cx > 0) {
                            px = s.cx;
                            py = s.cy + 1;
                            s.updatePredictions(px, py);
                            s.fc = mFrameCount;
                            s.state = 2;
                        } else {
                            px = s.cx;
                            py = videoHeight - 1;
                            s.updatePredictions(px, py);
                            s.setDone();
                            s.state = 3;
                        }
                    }
                }

                for (Seed s : mSeeds) {
                    int[] baseLine = new int[1];
                    final Size textSize = Imgproc.getTextSize(String.valueOf(s.sid),
                            Core.FONT_HERSHEY_SIMPLEX, 0.5, 1, baseLine);
                    double px, py;
                    px = s.px;
                    py = s.py;
                    if (s.state < 2) {
                        Imgproc.putText(frame, String.valueOf(s.sid),
                                new Point(s.cx - textSize.width / 2, s.cy + textSize.height / 2),
                                Core.FONT_HERSHEY_SIMPLEX, 0.5, new Scalar(0, 255, 0), 1, Imgproc.LINE_AA, true);
                    }
                }

        /*for(Seed s : mSeeds) {
            Size size = Imgproc.getTextSize(String.valueOf(s.sid), Core.FONT_HERSHEY_SIMPLEX, 0.5, 1, null);
            Imgproc.putText(frame, String.valueOf(s.sid), new Point(s.cx-size.width/2, s.cy+size.height/2), Core.FONT_HERSHEY_SIMPLEX, 0.5, new Scalar(100,100,0), 1);
        }*/

                final String strDown2 = "Frame: " + String.valueOf(mFrameCount) + ", Count: " + String.valueOf(mNumSeeds) + ", Ave Flow Rate: "
                        + String.valueOf(mAvgFlowRate) + ", Ave Area: " + String.valueOf(mAvgArea);
                Imgproc.line(frame, new Point(0, downLimit), new Point(videoWidth, downLimit),
                        new Scalar(0, 255, 0), 2);
                Imgproc.putText(frame, strDown2, new Point(10, downLimit - 20), Core.FONT_HERSHEY_SIMPLEX, 0.8, new Scalar(255, 0, 0), 1);

            }

            Log.d("Frame", String.valueOf(mFrameCount));
            mFrameCount = mFrameCount + 1;

            Imgcodecs.imwrite(new File(mFile).getParent() + "temp_contour" + System.nanoTime() + ".jpeg", frame);

            //return frame;
        }

    }

    /*Bitmap process(Bitmap inputBitmap) {

        Mat frame = new Mat();
        Utils.bitmapToMat(inputBitmap, frame);
        frame = process(frame);

        //Core.rotate(frame, frame, 270);

        Utils.matToBitmap(frame, inputBitmap);
        return inputBitmap.copy(inputBitmap.getConfig(), true);
    }*/

    /*void process(Bitmap[] frames) {

        /*Mat[] inputFrames = new Mat[frames.length];
        for (int i = 0; i < frames.length; i++) {
            Mat frame = new Mat();
            Utils.bitmapToMat(frames[i], frame);
            inputFrames[i] = frame;
        }

        process(frames);
        //frame = process(frame);

        //Core.rotate(frame, frame, 270);

        /*Utils.matToBitmap(frame, inputBitmap);
        return inputBitmap.copy(inputBitmap.getConfig(), true);
    }*/

    public int getNumSeeds() {
        return this.mNumSeeds;
    }
}
