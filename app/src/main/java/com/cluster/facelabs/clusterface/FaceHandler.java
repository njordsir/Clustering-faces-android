package com.cluster.facelabs.clusterface;

import android.content.Context;
import android.graphics.Bitmap;
import android.net.Uri;
import android.support.annotation.NonNull;
import android.util.Log;

import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.firebase.ml.vision.FirebaseVision;
import com.google.firebase.ml.vision.common.FirebaseVisionImage;
import com.google.firebase.ml.vision.face.FirebaseVisionFace;
import com.google.firebase.ml.vision.face.FirebaseVisionFaceDetector;
import com.google.firebase.ml.vision.face.FirebaseVisionFaceDetectorOptions;

import java.io.IOException;
import java.util.List;

public class FaceHandler {

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
                            }
                        })
                .addOnFailureListener(
                        new OnFailureListener() {
                            @Override
                            public void onFailure(@NonNull Exception e) {
                                // Task failed with an exception
                                e.printStackTrace();
                                Utils.showToast(mContext, "Face detector failed!");
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
                String savedPath = Utils.saveImage(croppedFace, imageName, String.valueOf(i++));
            }catch (IllegalArgumentException e){
                Log.d("finding", "Illegal argument to crop!");
            }
        }
    }

}
