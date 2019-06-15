package com.cluster.facelabs.clusterface;

import android.app.Activity;
import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.os.AsyncTask;
import android.os.Environment;
import android.util.Log;

import org.tensorflow.lite.Interpreter;

import java.io.File;
import java.io.FileInputStream;
import java.io.FilenameFilter;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.util.HashMap;

public class TfliteHandler
{
    private Context mContext;

    private Interpreter mTfliteIntepreter;
    /**is the tflite model quantized?
     * will change the size of bytebuffer in the method "convertBitmapToByteBuffer"*/
    private final boolean IS_QUANT_MODEL = false;

    /**image dimensions*/
    private final int DIM_X = InferenceHelper.DIM_X;
    private final int DIM_Y = InferenceHelper.DIM_Y;
    private final int DIM_Z = InferenceHelper.DIM_Z;
    /**encoding dimension*/
    private final int DIM_ENCODING = InferenceHelper.DIM_ENCODING;

    /**pre-whiten the images before passing through the network*/
    private final boolean mPreWhiten = true;
    private float mean, std;

    /** Pre-allocated buffers for storing image data*/
    private final int[] mIntValues = new int[DIM_X * DIM_Y];
    /**placeholder for the output encoding of the model*/
    private float [][] mFaceEncodingOutput = null;

    public HashMap<String, InferenceHelper.Encoding> mEncodings;
    public HashMap<String, InferenceHelper.Encoding> prevEncodings;

    /**constructor*/
    public TfliteHandler(Context context, Activity activity){
        mContext = context;
        initTfliteModel(activity);
    }

    /** Memory-map the model file in Assets. */
    private MappedByteBuffer loadModelFile(Activity activity) throws IOException {
        AssetFileDescriptor fileDescriptor = activity.getAssets().openFd(InferenceHelper.LOCAL_MODEL_ASSET);
        FileInputStream inputStream = new FileInputStream(fileDescriptor.getFileDescriptor());
        FileChannel fileChannel = inputStream.getChannel();
        long startOffset = fileDescriptor.getStartOffset();
        long declaredLength = fileDescriptor.getDeclaredLength();
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength);
    }

    /**load the model into memory*/
    private void initTfliteModel(Activity activity){
        try {
            mTfliteIntepreter = new Interpreter(loadModelFile(activity));
        } catch (IOException e) {
            e.printStackTrace();
            Utils.showToast(mContext, "Unable to load model!");
            return;
        }
        mFaceEncodingOutput = new float[1][DIM_ENCODING];
        prevEncodings = Utils.loadEncodings();
        mEncodings = new HashMap<>();
        Utils.showToast(mContext, "Loaded model!");
    }

    private void findMeanAndStd(){
        /**probably very inefficient to calculate mean and std this way*/
        int size = DIM_X*DIM_Y*DIM_Z;

        int pixel = 0;
        int sum = 0;
        for (int i = 0; i < DIM_X; ++i) {
            for (int j = 0; j < DIM_Y; ++j) {
                final int val = mIntValues[pixel++];
                sum += Color.red(val);
                sum += Color.green(val);
                sum += Color.blue(val);
            }
        }
        mean = (float)sum/size;

        pixel = 0;
        float var = 0;
        for (int i = 0; i < DIM_X; ++i) {
            for (int j = 0; j < DIM_Y; ++j) {
                final int val = mIntValues[pixel++];
                var += Math.pow(Color.red(val)-mean, 2);
                var += Math.pow(Color.green(val)-mean, 2);
                var += Math.pow(Color.blue(val)-mean, 2);
            }
        }
        var /= size;
        std = (float)Math.sqrt(var);
        std = Math.max(std, 1.f/((float)Math.sqrt(size)));

        Log.d("encoding", "Mean : " + String.valueOf(mean) + " Std : " + String.valueOf(std));
    }

    /**the tflite model accepts image as ByteBuffer*/
    private synchronized ByteBuffer convertBitmapToByteBuffer(Bitmap bitmap) {
        /**initialize buffer for image data*/
        ByteBuffer imgData =
                ByteBuffer.allocateDirect(1 * DIM_Z * DIM_X * DIM_Y * (IS_QUANT_MODEL?1:4));
        imgData.order(ByteOrder.nativeOrder());
        Bitmap scaledBitmap = Bitmap.createScaledBitmap(bitmap, DIM_X, DIM_Y, true);
        imgData.rewind();
        scaledBitmap.getPixels(mIntValues, 0, scaledBitmap.getWidth(), 0, 0,
                scaledBitmap.getWidth(), scaledBitmap.getHeight());

        // Convert the image to floating point/byte
        int pixel = 0;
        if(IS_QUANT_MODEL){
            for (int i = 0; i < DIM_X; ++i) {
                for (int j = 0; j < DIM_Y; ++j) {
                    final int val = mIntValues[pixel++];
                    imgData.put((byte) ((val >> 16) & 0xFF));
                    imgData.put((byte) ((val >> 8) & 0xFF));
                    imgData.put((byte) (val & 0xFF));
                }
            }
        }else{
            /**default values for mean and std*/
            mean = 128.0f;
            std = 128.0f;
            /**calculate mean and std needed for pre-whitening*/
            if(mPreWhiten)
                findMeanAndStd();
            for (int i = 0; i < DIM_X; ++i) {
                for (int j = 0; j < DIM_Y; ++j) {
                    final int val = mIntValues[pixel++];
                    imgData.putFloat((byte) (((val >> 16) & 0xFF) -mean )/ (std));
                    imgData.putFloat((byte) (((val >> 8) & 0xFF) - mean)/ (std));
                    imgData.putFloat((byte) ((val & 0xFF)-mean) / (std));
                }
            }
        }
        return imgData;
    }

    private void runTfliteInference(Bitmap bmap, String fileName){
        if(bmap == null){
            Log.d("encoding", "ERROR : Trying to run inference on null bitmap!");
        }

        if(mTfliteIntepreter == null) {
            Utils.showToast(mContext,"ERROR : Intepreter not initialized!");
            return;
        }

        ByteBuffer imgData;
        try {
            imgData = convertBitmapToByteBuffer(bmap);
        }catch (Exception e) {
            e.printStackTrace();
            Utils.showToast(mContext, "ERROR : Could not create ByteBuffer for image!");
            return;
        }

        /**if all is as expected, run the inference*/
        try {
            mTfliteIntepreter.run(imgData, mFaceEncodingOutput);
        }catch (Exception e){
            e.printStackTrace();
            Utils.showToast(mContext,"ERROR : Could not run inference!");
            return;
        }

        mEncodings.put(fileName, new InferenceHelper.Encoding(mFaceEncodingOutput[0]));
    }

    private class AsycTfliteInferenceAll extends AsyncTask<File, Integer, Void> {
        @Override
        protected Void doInBackground(File... files) {
            for(int i = 0; i < files.length; i++){
                String fname = files[i].getName();
                /**if encoding for this crop already exists, skip*/
                if(prevEncodings != null && prevEncodings.containsKey(fname)){
                    Log.e("savedEncodings", "Encoding already exists");
                    mEncodings.put(fname, prevEncodings.get(fname));
                    publishProgress(i);
                    continue;
                }

                Bitmap bm = BitmapFactory.decodeFile(files[i].getAbsolutePath());
                runTfliteInference(bm, files[i].getName());
                publishProgress(i);
            }
            return null;
        }

        @Override
        protected void onProgressUpdate(Integer... values) {
            MainActivity.encodingProgressBar.setProgress(values[0]);
        }

        @Override
        protected void onPostExecute(Void aVoid) {
            Utils.saveEncodings(mContext, mEncodings);
        }
    }

    public void runTfliteInferenceOnAllCrops(){
        String cropsDirPath = Utils.getCropsPath();
        File cropsDir = new File(cropsDirPath);

        File[] files = cropsDir.listFiles(new FilenameFilter() {
            @Override
            public boolean accept(File dir, String name) {
                return !name.equals(".nomedia");
            }
        });
        if(files == null){
            Utils.showToast(mContext, "No crops found!");
            return;
        }


        MainActivity.encodingProgressBar.setMax(files.length + 1);

        new AsycTfliteInferenceAll().execute(files);
    }
}
