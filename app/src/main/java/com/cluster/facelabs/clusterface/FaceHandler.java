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
import java.io.IOException;
import java.util.List;

public class FaceHandler {

    int mIdx = -1;
    File[] files;
    /*[TODO]
    * Queue up-to 5 instead of just 1
    * */
    int mQueueCounter = 0;
    int mQueueMax = 5;

    private Context mContext;

    public FaceHandler(Context context){
        mContext = context;
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

        /**face detector options*/
        FirebaseVisionFaceDetectorOptions options =
                new FirebaseVisionFaceDetectorOptions.Builder()
                        .setPerformanceMode(FirebaseVisionFaceDetectorOptions.ACCURATE)
                        .setMinFaceSize(0.05f)
                        .build();

        /**get face detector*/
        FirebaseVisionFaceDetector faceDetector = FirebaseVision.getInstance().getVisionFaceDetector(options);

        /**start detection with callbacks*/
        faceDetector.detectInImage(image)
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
            Utils.showToast(mContext, "No faces found!");
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
            //showToast("("+String.valueOf(left) + "," + String.valueOf(top) + "),(" + String.valueOf(width) + "," + String.valueOf(height) + ")");


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
        String inputDirPath = Environment.getExternalStoragePublicDirectory(
                Environment.DIRECTORY_PICTURES)
                + "/Clusterface/Input";

        File inputDir = new File(inputDirPath);
        if(!inputDir.exists()) {
            Utils.showToast(mContext, "ERROR : Input folder not found!");
            return;
        }

        files = inputDir.listFiles();
        if(files == null){
            Utils.showToast(mContext, "ERROR : No files found in the input folder!");
            return;
        }

        /**remove the crops from the crops folder
         * for which the input image is no longer present*/
        File cropsDir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES) + "/Clusterface/Crops");
        if(cropsDir.exists())
        {
            for(File crop : cropsDir.listFiles())
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
        String cropCheckName = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES) + "/Clusterface/Crops/" + fileName + "_0.jpg";
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
