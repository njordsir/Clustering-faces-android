package com.cluster.facelabs.clusterface;

import android.content.Context;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Log;
import android.widget.Toast;

import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import com.cluster.facelabs.clusterface.InferenceHelper.Encoding;

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

    public static void copyPhotoToInputFolder(Context context, Uri sourceUri){
        String destPath = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
                + "/Clusterface/Input/" + sourceUri.getLastPathSegment() + ".jpg";

        try {
            InputStream inputStream = context.getContentResolver().openInputStream(sourceUri);
            FileUtils.copyInputStreamToFile(inputStream, new File(destPath));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void createResultsFolder(ClusteringHandler clHandler,
                                           HashMap<String, Encoding> Encodings){
        String resultsDirPath = Environment.getExternalStoragePublicDirectory(
                Environment.DIRECTORY_PICTURES) + "/Clusterface/Results";
        File resultsDir = new File(resultsDirPath);

        /**create results folder*/
        if(resultsDir.exists()) try {
            FileUtils.deleteDirectory(resultsDir);
        } catch (IOException e) {
            e.printStackTrace();
            Log.e("save_debug", "Could not delete existing results folder!");
            return;
        }

        boolean success = resultsDir.mkdirs();
        if(!success){Log.d("save_debug", "Could not create results folder!");return;}

        /**create folders for the clusters*/
        int num_clusters;
        if(MainActivity.clusterMethod == "DBScan")
            num_clusters = clHandler.mDBClusters.size();
        else if(MainActivity.clusterMethod == "KMeans")
            num_clusters = clHandler.bestKMeans.size();
        else
            return;

        /**create folders for the clusters*/
        for(int i = -1; i < num_clusters; i++){
            String clusterDirPath = resultsDirPath + "/" + i;
            File clusterDir = new File(clusterDirPath);
            success = true;
            if (!clusterDir.exists()) success = clusterDir.mkdirs();
            if(!success){Log.d("save_debug", "Could not create clusters folder!");return;}
        }

        /**get the crops directory path
         * images will be loaded from here and saved to the results folder*/
        String cropsDirPath = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES) + "/Clusterface/Crops";

        Iterator it = Encodings.entrySet().iterator();
        MainActivity.saveResultsProgressBar.setMax(Encodings.size());
        MainActivity.saveResultsProgressBar.setProgress(0);
        while (it.hasNext()) {
            Map.Entry pair = (Map.Entry) it.next();
            String fileName = pair.getKey().toString();
            Encoding encoding =
                    (Encoding) pair.getValue();

            /**get the cluster id for this encoding*/
            int clusterIdx;
            if(MainActivity.clusterMethod == MainActivity.dbscan)
                clusterIdx = clHandler.getDBScanClusterIdx(encoding);
            else if(MainActivity.clusterMethod == MainActivity.kmeans)
                clusterIdx = clHandler.getKMeansClusterIdx(encoding);
            else
                return;

            String sourcePath = cropsDirPath + "/" + fileName;
            String destPath = resultsDirPath + "/" + clusterIdx + "/" +  fileName;

            File source = new File(sourcePath);
            File dest = new File(destPath);
            try {
                FileUtils.copyFile(source, dest);
            } catch (IOException e) {
                e.printStackTrace();
                Log.d("save_debug", "Unable to copy image to results folder!");
            }
            MainActivity.saveResultsProgressBar.incrementProgressBy(1);
        }
    }

    //TODO : use asynctask for saving files

    public static void saveEncodings(Context context, HashMap<String, Encoding> Encodings){
        String encPath = Environment.getExternalStoragePublicDirectory(
                Environment.DIRECTORY_PICTURES) + "/Clusterface/encodings.enc";
        File file = new File(encPath);
        ObjectOutputStream outputStream = null;
        try {
            outputStream = new ObjectOutputStream(new FileOutputStream(file));
            outputStream.writeObject(Encodings);
            outputStream.flush();
            outputStream.close();
        } catch (IOException e) {
            e.printStackTrace();
            showToast(context, "Unable to save encodings!");
            Log.e("saveEncodings", "Unable to save encodings!");
        }
    }

    public static HashMap<String, Encoding> loadEncodings(){
        String encPath = Environment.getExternalStoragePublicDirectory(
                Environment.DIRECTORY_PICTURES) + "/Clusterface/encodings.enc";
        File file = new File(encPath);

        if(!file.exists())
            return null;

        HashMap<String, Encoding> encodings = null;
        ObjectInputStream inputStream = null;
        try {
            inputStream = new ObjectInputStream(new FileInputStream(file));
            encodings = (HashMap<String, Encoding>) inputStream.readObject();
        } catch (IOException e) {
            e.printStackTrace();
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        }
        return encodings;
    }
}
