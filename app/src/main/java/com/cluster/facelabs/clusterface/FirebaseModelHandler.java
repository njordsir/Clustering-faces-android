package com.cluster.facelabs.clusterface;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.os.Environment;
import android.support.annotation.NonNull;
import android.util.Log;

import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.firebase.ml.common.FirebaseMLException;
import com.google.firebase.ml.custom.FirebaseModelDataType;
import com.google.firebase.ml.custom.FirebaseModelInputOutputOptions;
import com.google.firebase.ml.custom.FirebaseModelInputs;
import com.google.firebase.ml.custom.FirebaseModelInterpreter;
import com.google.firebase.ml.custom.FirebaseModelManager;
import com.google.firebase.ml.custom.FirebaseModelOptions;
import com.google.firebase.ml.custom.FirebaseModelOutputs;
import com.google.firebase.ml.custom.model.FirebaseLocalModelSource;

import java.io.File;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public class FirebaseModelHandler {

    private Context mContext;
    private FirebaseModelInterpreter mInterpreter;
    private FirebaseModelInputOutputOptions mInputOutputOptions;

    /**file name of the tflite model*/
    private final String LOCAL_MODEL_ASSET = "sandberg.tflite";

    /**is the tflite model quantized?
     * will change the size of bytebuffer in the method "convertBitmapToByteBuffer"*/
    private final boolean IS_QUANT_MODEL = false;

    /**image dimensions*/
    private final int DIM_BATCH = 1;
    private final int DIM_X = 160;
    private final int DIM_Y = 160;
    private final int DIM_Z = 3;
    /**encoding dimension*/
    private static final int DIM_ENCODING = 128;

    /**pre-whiten the images before passing through the network*/
    private final boolean mPreWhiten = true;
    private float mean, std;

    /** Pre-allocated buffers for storing image data*/
    private final int[] mIntValues = new int[DIM_X * DIM_Y];
    /**placeholder for the output encoding of the model*/
    private float [][] mFaceEncodingOutput = null;

    public FirebaseModelHandler(Context context){
        mContext = context;
        initFBModel();
    }

    private void initFBModel(){
        int[] inputDims = {DIM_BATCH, DIM_X, DIM_Y, DIM_Z};
        int[] outputDims = {DIM_BATCH, DIM_ENCODING};

        try {
            mInputOutputOptions = new FirebaseModelInputOutputOptions.Builder()
                                    .setInputFormat(0,
                                            FirebaseModelDataType.FLOAT32, inputDims)
                                    .setOutputFormat(0,
                                            FirebaseModelDataType.FLOAT32, outputDims)
                                    .build();

            FirebaseLocalModelSource localSource =
                    new FirebaseLocalModelSource.Builder("asset")
                            .setAssetFilePath(LOCAL_MODEL_ASSET).build();

            FirebaseModelManager manager = FirebaseModelManager.getInstance();
            manager.registerLocalModelSource(localSource);

            FirebaseModelOptions modelOptions =
                    new FirebaseModelOptions.Builder()
                            .setLocalModelName("asset")
                            .build();

            mInterpreter = FirebaseModelInterpreter.getInstance(modelOptions);

        } catch (FirebaseMLException e) {
            e.printStackTrace();
        }

        mFaceEncodingOutput = new float[1][DIM_ENCODING];
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
        mean = sum/size;

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
            if(mPreWhiten){
                findMeanAndStd();
                for (int i = 0; i < DIM_X; ++i) {
                    for (int j = 0; j < DIM_Y; ++j) {
                        final int val = mIntValues[pixel++];
                        imgData.putFloat((byte) (((val >> 16) & 0xFF) -mean )/ (std));
                        imgData.putFloat((byte) (((val >> 8) & 0xFF) - mean)/ (std));
                        imgData.putFloat((byte) ((val & 0xFF)-mean) / (std));
                    }
                }
            }else{
                for (int i = 0; i < DIM_X; ++i) {
                    for (int j = 0; j < DIM_Y; ++j) {
                        final int val = mIntValues[pixel++];
                        imgData.putFloat((byte) (((val >> 16) & 0xFF) - 128 )/ (128.0f));
                        imgData.putFloat((byte) (((val >> 8) & 0xFF) - 128)/ (128.0f));
                        imgData.putFloat((byte) ((val & 0xFF) - 128) / (128.0f));
                    }
                }
            }
        }
        return imgData;
    }

    private void runFBModelInference(Bitmap bmap, String fileName) {
        if(bmap == null){
            Utils.showToast(mContext, "ERROR : Trying to run inference on null bitmap!");
            return;
        }

        if (mInterpreter == null) {
            Utils.showToast(mContext,"ERROR : Firebase Interpreter not initialized!");
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

        try {
            FirebaseModelInputs inputs = new FirebaseModelInputs.Builder().
                                                add(imgData).build();

            mInterpreter.run(inputs, mInputOutputOptions)
                    .addOnFailureListener(new OnFailureListener() {
                        @Override
                        public void onFailure(@NonNull Exception e) {
                            e.printStackTrace();
                            Utils.showToast(mContext, "Model inference failed!");
                        }
                    })
                    .addOnSuccessListener(new OnSuccessListener<FirebaseModelOutputs>() {
                        @Override
                        public void onSuccess(FirebaseModelOutputs firebaseModelOutputs) {
                            mFaceEncodingOutput = firebaseModelOutputs.getOutput(0);
                            Log.d("FB encoding", "Encoding successful " + String.valueOf(mFaceEncodingOutput[0][0]));
                            MainActivity.encodingProgressBar.incrementProgressBy(1);
                        }
                    });

        } catch (FirebaseMLException e) {
            e.printStackTrace();
            Utils.showToast(mContext, "Model inference exception!");
        }
    }

    public void runFBModelInferenceOnAllCrops(){
        String cropsDirPath = Environment.getExternalStoragePublicDirectory(
                Environment.DIRECTORY_PICTURES) + "/Clusterface/Crops";
        File cropsDir = new File(cropsDirPath);

        File[] files = cropsDir.listFiles();
        if(files == null){
            Utils.showToast(mContext, "No crops found!");
            return;
        }

        MainActivity.encodingProgressBar.setMax(files.length);
        MainActivity.encodingProgressBar.setProgress(0);

        for (int i = 0; i < files.length; i++){
            Log.d("encoding", files[i].getName());
            Bitmap bm = BitmapFactory.decodeFile(files[i].getAbsolutePath());
            runFBModelInference(bm, files[i].getName());
        }
    }

}
