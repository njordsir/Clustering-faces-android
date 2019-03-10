package com.cluster.facelabs.clusterface;

import android.Manifest;
import android.content.ClipData;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.support.annotation.Nullable;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.widget.ProgressBar;

import java.util.ArrayList;

public class MainActivity extends AppCompatActivity {

    public static ProgressBar faceQueueProgressbar;
    public static ProgressBar faceProgressbar;
    public static ProgressBar encodingQueueProgressBar;
    public static ProgressBar encodingProgressBar;

    TfliteHandler tfliteHandler = null;
    FaceHandler faceHandler = null;
    FirebaseModelHandler fbModelHandler = null;

    private static final int RC_STORAGE_PERMISSION = 0;
    private static final int RC_PHOTO_PICKER = 1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        requestPermissions();

        faceQueueProgressbar = findViewById(R.id.face_queue_pbar);
        faceProgressbar = findViewById(R.id.face_pbar);
        encodingQueueProgressBar = findViewById(R.id.encoding_queue_pbar);
        encodingProgressBar = findViewById(R.id.encoding_pbar);
    }

    private void requestPermissions(){
        boolean readPermissionGranted = ContextCompat.checkSelfPermission(
                this, Manifest.permission.READ_EXTERNAL_STORAGE)
                == PackageManager.PERMISSION_GRANTED;
        boolean writePermissionGranted = ContextCompat.checkSelfPermission(
                this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                == PackageManager.PERMISSION_GRANTED;
        ArrayList<String> permissionsToRequest = new ArrayList<>();
        if(!readPermissionGranted)
            permissionsToRequest.add(Manifest.permission.READ_EXTERNAL_STORAGE);
        if(!writePermissionGranted)
            permissionsToRequest.add(Manifest.permission.WRITE_EXTERNAL_STORAGE);

        if(!permissionsToRequest.isEmpty()){
            ActivityCompat.requestPermissions(this, permissionsToRequest.toArray(new String[0]),
                    RC_STORAGE_PERMISSION);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           String permissions[], int[] grantResults) {
        switch (requestCode) {
            case RC_STORAGE_PERMISSION: {
                // If request is cancelled, the result arrays are empty.
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    Utils.showToast(this ,"Storage permission granted!");
                } else {
                    Utils.showToast(this ,"Storage permission denied!");
                }
                return;
            }
        }
    }

    /**select photos to process from the gallery*/
    public void selectInputPhotos(View view) {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("image/jpeg");
        intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        intent.putExtra(Intent.EXTRA_LOCAL_ONLY, true); //pick from local photos
        startActivityForResult(Intent.createChooser(intent, "Complete action using"), RC_PHOTO_PICKER);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if(requestCode == RC_PHOTO_PICKER){
            if(resultCode == RESULT_OK) {
                if(data.getData() == null){
                    //multiple photos selected
                    ClipData clipdata = data.getClipData();
                    for (int i=0; i<clipdata.getItemCount();i++){
                        Uri imageUri = clipdata.getItemAt(i).getUri();
                        Utils.copyPhotoToInputFolder(this, imageUri);
                    }
                }else{
                    //single photo selected
                    Uri imageUri = data.getData();
                    Utils.copyPhotoToInputFolder(this, imageUri);
                }
            }else{
                Utils.showToast(this, "Unable to pick image from gallery!");
            }
        }
    }

    public void getFaces(View view){
        if(faceHandler == null)
            faceHandler = new FaceHandler(this);
        faceHandler.getCrops();
    }

    public void getEncodings(View view){
        if(tfliteHandler == null)
            tfliteHandler = new TfliteHandler(this, MainActivity.this);
        tfliteHandler.runTfliteInferenceOnAllCrops();
    }

    public void getFBEncodings(View view){
        if(fbModelHandler == null)
            fbModelHandler = new FirebaseModelHandler(this);
        fbModelHandler.runFBModelInferenceOnAllCrops();
    }
}
