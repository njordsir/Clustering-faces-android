package com.cluster.facelabs.clusterface;

import android.content.Context;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Log;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;

public class Utils
{
    public static void showToast(Context context,  String message) {
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show();
    }

    public static Bitmap getBitmapFromUri(Context context, Uri uri) throws IOException {
        return MediaStore.Images.Media.getBitmap(context.getContentResolver(), uri);
    }

    public static String saveImage(Bitmap bitmap, String origFileName, String faceIdx){
        Log.d("finding faces", "Saving crop..");
        String savedImagePath = null;

        // Create the new file in the external storage
        String imageFileName = origFileName + "_" + faceIdx + ".jpg";
        File storageDir = new File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
                        + "/Clusterface/Crops");
        boolean success = true;
        if (!storageDir.exists()) {
            success = storageDir.mkdirs();
        }

        // Save the new Bitmap
        if (success) {
            File imageFile = new File(storageDir, imageFileName);
            savedImagePath = imageFile.getAbsolutePath();
            try {
                OutputStream fOut = new FileOutputStream(imageFile);
                bitmap.compress(Bitmap.CompressFormat.JPEG, 100, fOut);
                fOut.close();
            } catch (Exception e) {
                e.printStackTrace();
                Log.d("finding faces", "Failed to write to output stream!");
            }
            Log.d("finding faces", "Saved crop to " + savedImagePath);
            //galleryAddPic(savedImagePath);
        }else{
            Log.d("finding faces", "Failed to save image!");
        }

        return savedImagePath;
    }
}
