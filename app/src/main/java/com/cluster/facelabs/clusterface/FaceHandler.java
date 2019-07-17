package com.cluster.facelabs.clusterface;

import android.content.Context;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Environment;
import android.support.annotation.NonNull;
import android.util.Log;

import com.bumptech.glide.Glide;
import com.bumptech.glide.request.target.SimpleTarget;
import com.bumptech.glide.request.transition.Transition;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.firebase.ml.vision.FirebaseVision;
import com.google.firebase.ml.vision.common.FirebaseVisionImage;
import com.google.firebase.ml.vision.face.FirebaseVisionFace;
import com.google.firebase.ml.vision.face.FirebaseVisionFaceDetector;
import com.google.firebase.ml.vision.face.FirebaseVisionFaceDetectorOptions;

import org.apache.commons.io.FilenameUtils;

import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.util.List;

import static com.cluster.facelabs.clusterface.MainActivity.faceDetectModeSwitch;
import static com.cluster.facelabs.clusterface.MainActivity.minFaceSizeSeekbar;

public class FaceHandler {

    int mIdx = -1;
    File[] files;
    /*[TODO]
     * Queue up-to 5 instead of just 1
     * */
    int mQueueCounter = 0;
    int mQueueMax = 5;

    private Context mContext;
    FirebaseVisionFaceDetector mFaceDetector;

    public FaceHandler(Context context){
        mContext = context;

        /**face detector options*/
        float minFaceSize = 0.05f*(1+minFaceSizeSeekbar.getProgress());
        int performanceMode;
        if(faceDetectModeSwitch.isEnabled())
            performanceMode = FirebaseVisionFaceDetectorOptions.FAST;
        else
            performanceMode = FirebaseVisionFaceDetectorOptions.ACCURATE;

        FirebaseVisionFaceDetectorOptions options =
                new FirebaseVisionFaceDetectorOptions.Builder()
                        .setPerformanceMode(performanceMode)
                        .setMinFaceSize(minFaceSize)
                        .build();

        /**get face detector*/
        mFaceDetector = FirebaseVision.getInstance().getVisionFaceDetector(options);
    }

    /**get an instance of FirebaseVisionImage from imagepath or bitmap*/
    private FirebaseVisionImage getFirebaseVisionImage(Uri imagePath, Bitmap bitmap){
        FirebaseVisionImage image = null;
        if(imagePath != null){
            try {
                image = FirebaseVisionImage.fromFilePath(mContext, imagePath);
            } catch (IOException e) {
                e.printStackTrace();
                Utils.showToast(mContext, "Unable to create FireBase Image from uri!");
            }
        }else{
            image = FirebaseVisionImage.fromBitmap(bitmap);
        }
        return image;
    }

    /**get faces from an image*/
    private void runFaceRecognition(final Uri imagePath,
                                    final Bitmap bitmap,
                                    final String imageName){
        FirebaseVisionImage image = getFirebaseVisionImage(imagePath, bitmap);
        /**start detection with callbacks*/
        mFaceDetector.detectInImage(image)
                .addOnSuccessListener(
                        new OnSuccessListener<List<FirebaseVisionFace>>() {
                            @Override
                            public void onSuccess(List<FirebaseVisionFace> firebaseVisionFaces) {
                                processFaceRecognitionResult(firebaseVisionFaces,
                                        imagePath, bitmap, imageName);

                                next();

                            }
                        })
                .addOnFailureListener(
                        new OnFailureListener() {
                            @Override
                            public void onFailure(@NonNull Exception e) {
                                // Task failed with an exception
                                e.printStackTrace();
                                Utils.showToast(mContext, "Face detector failed!");
                                next();
                            }
                        });
    }

    /**process the output of "runFaceRecognition"*/
    private void processFaceRecognitionResult(List<FirebaseVisionFace> faces,
                                              Uri imagePath, Bitmap bitmap, String imageName){
        if(faces.size() == 0){
            Log.d("finding faces", "No faces found!");
            return;
        }

        /**get the image from which crops will be extracted
         * based on identified bounding boxes*/
        Bitmap inputImg;
        if(imagePath != null) {
            try {
                inputImg = Utils.getBitmapFromUri(mContext, imagePath);
            } catch (IOException e) {
                e.printStackTrace();
                Utils.showToast(mContext, "Face : Cannot load image from uri!");
                return;
            }
        }else{
            inputImg = bitmap;
        }

        int i = 0;
        for(FirebaseVisionFace face : faces){
            /**get bounding box details*/
            int top = face.getBoundingBox().top;
            int left = face.getBoundingBox().left;
            int width = face.getBoundingBox().width();
            int height = face.getBoundingBox().height();

            /**create a bitmap for identified face*/
            try {
                Bitmap croppedFace = Bitmap.createBitmap(inputImg, left, top, width, height);
                Utils.saveImage(croppedFace, imageName, String.valueOf(i++));
            }catch (IllegalArgumentException e){
                Log.d("finding", "Illegal argument to crop!");
            }
        }

        MainActivity.faceProgressbar.incrementProgressBy(1);
    }

    /**find faces for all images in the input directory*/
    public void getCrops(){
        String inputDirPath = Utils.getInputPath();

        File inputDir = new File(inputDirPath);
        if(!inputDir.exists()) {
            Utils.showToast(mContext, "ERROR : Input folder not found!");
            return;
        }

        files = inputDir.listFiles(new FilenameFilter() {
            @Override
            public boolean accept(File dir, String name) {
                return !name.equals(".nomedia");
            }
        });
        if(files == null){
            Utils.showToast(mContext, "ERROR : No files found in the input folder!");
            return;
        }

        /**remove the crops from the crops folder
         * for which the input image is no longer present*/
        File cropsDir = new File(Utils.getCropsPath());
        if(cropsDir.exists())
        {
            for(File crop : cropsDir.listFiles(new FilenameFilter() {
                @Override
                public boolean accept(File dir, String name) {
                    return !name.equals(".nomedia");
                }
            }))
            {
                String cropName = FilenameUtils.removeExtension(crop.getName());
                String possibleInputName  = cropName.substring(0, cropName.lastIndexOf('_')) + "." + FilenameUtils.getExtension(crop.getName());
                File possibleInput = new File(inputDirPath + "/" + possibleInputName);
                if(!possibleInput.exists())
                    crop.delete();
            }
        }

        MainActivity.faceQueueProgressbar.setMax(files.length);
        MainActivity.faceQueueProgressbar.setProgress(0);
        MainActivity.faceProgressbar.setMax(files.length);
        MainActivity.faceProgressbar.setProgress(0);

        next();
    }

    private void next(){
        mIdx++;
        if(mIdx >= files.length) return;
        final String fileName = FilenameUtils.removeExtension(files[mIdx].getName());
        /**if crops for this input image have been already found, skip*/
        String cropCheckName = Utils.getCropsPath() + "/" + fileName + "_0.jpg";
        File cropCheck = new File(cropCheckName);
        Log.e("finding faces", cropCheckName);
        if(cropCheck.exists()){
            MainActivity.faceQueueProgressbar.incrementProgressBy(1);
            MainActivity.faceProgressbar.incrementProgressBy(1);
            Log.d("finding faces", "Crop exists!");
            next();
        }else{
            Log.d("finding faces", "Processing...");

            /**runFaceRecognition(Uri.fromFile(files[i]), null);
             * this gives error and loads images with the wrong orientation etc
             * cropping seems to always fix this
             * using glide to load bitmap from file with center cropping*/
            Glide.with(mContext).asBitmap().load(files[mIdx])
                    .into(new SimpleTarget<Bitmap>() {
                        @Override
                        public void onResourceReady(Bitmap resource, Transition<? super Bitmap> transition) {
                            runFaceRecognition(null, resource, fileName);}});
            MainActivity.faceQueueProgressbar.incrementProgressBy(1);
        }
    }

}
