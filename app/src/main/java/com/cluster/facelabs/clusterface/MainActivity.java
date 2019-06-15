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
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    public static ProgressBar faceQueueProgressbar;
    public static ProgressBar faceProgressbar;
    public static ProgressBar encodingQueueProgressBar;
    public static ProgressBar encodingProgressBar;
    public static ProgressBar saveResultsProgressBar;
    public static ProgressBar clusteringProgressBar;
    public static ProgressBar cwGraphProgressBar;

    public static EditText dBScanEpsText;
    public static EditText dBScanMinPtsText;
    public static EditText kmeansKText;
    public static EditText cwThreshText;

    public static Spinner clusterTypeSpinner;
    public static String clusterMethod;
    public static final String dbscan = "DBScan";
    public static final String kmeans = "KMeans";
    public static final String cw = "ChineseWhispers";

    private TextView kDesc, epsDesc, minPtsDesc, cwThreshDesc, cwGraphProgressDesc;

    public static TextView clusterResultsText;

    TfliteHandler tfliteHandler = null;
    FaceHandler faceHandler = null;
    FirebaseModelHandler fbModelHandler = null;
    ClusteringHandler clusteringHandler = null;
    ChineseWhispersHandler cwHandler = null;

    HashMap<String, InferenceHelper.Encoding> Encodings;

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
        saveResultsProgressBar = findViewById(R.id.save_results_pbar);
        clusteringProgressBar = findViewById(R.id.clustering_pbar);
        cwGraphProgressBar = findViewById(R.id.cw_graph_pbar);

        kDesc = findViewById(R.id.kmeans_cluster_count_desc);
        epsDesc = findViewById(R.id.dbscan_eps_desc);
        minPtsDesc = findViewById(R.id.dbscan_min_count_desc);
        cwThreshDesc = findViewById(R.id.cw_threshold_desc);
        cwGraphProgressDesc = findViewById(R.id.cw_graph_pbar_desc);

        dBScanEpsText = findViewById(R.id.dbscan_eps);
        dBScanMinPtsText = findViewById(R.id.dbscan_min_count);
        kmeansKText = findViewById(R.id.kmeans_cluster_count);
        cwThreshText = findViewById(R.id.cw_threshold);

        clusterResultsText = findViewById(R.id.cluster_output_text);

        final ArrayList<View> dbscanViews = new ArrayList<>();
        dbscanViews.add(epsDesc);
        dbscanViews.add(dBScanEpsText);
        dbscanViews.add(minPtsDesc);
        dbscanViews.add(dBScanMinPtsText);

        final ArrayList<View> kmeansViews = new ArrayList<>();
        kmeansViews.add(kDesc);
        kmeansViews.add(kmeansKText);

        final ArrayList<View> cwViews = new ArrayList<>();
        cwViews.add(cwThreshDesc);
        cwViews.add(cwThreshText);
        cwViews.add(cwGraphProgressDesc);
        cwViews.add(cwGraphProgressBar);

        clusterTypeSpinner = findViewById(R.id.cluster_type_spinner);
        List<String> categories = new ArrayList<>();
        categories.add(dbscan);
        categories.add(kmeans);
        categories.add(cw);
        ArrayAdapter<String> spinnerAdapter = new ArrayAdapter<String>(this, R.layout.spinner_item, categories);
        spinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        clusterTypeSpinner.setAdapter(spinnerAdapter);
        clusterTypeSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                clusterMethod = parent.getItemAtPosition(position).toString();
                if(clusterMethod.equals(dbscan)){
                    for(int i = 0; i < dbscanViews.size(); i++)
                        dbscanViews.get(i).setVisibility(View.VISIBLE);
                    for(int i = 0; i < kmeansViews.size(); i++)
                        kmeansViews.get(i).setVisibility(View.GONE);
                    for(int i = 0; i < cwViews.size(); i++)
                        cwViews.get(i).setVisibility(View.GONE);
                }
                else if(clusterMethod.equals(kmeans)){
                    for(int i = 0; i < dbscanViews.size(); i++)
                        dbscanViews.get(i).setVisibility(View.GONE);
                    for(int i = 0; i < kmeansViews.size(); i++)
                        kmeansViews.get(i).setVisibility(View.VISIBLE);
                    for(int i = 0; i < cwViews.size(); i++)
                        cwViews.get(i).setVisibility(View.GONE);
                }
                else if(clusterMethod.equals(cw)){
                    for(int i = 0; i < dbscanViews.size(); i++)
                        dbscanViews.get(i).setVisibility(View.GONE);
                    for(int i = 0; i < kmeansViews.size(); i++)
                        kmeansViews.get(i).setVisibility(View.GONE);
                    for(int i = 0; i < cwViews.size(); i++)
                        cwViews.get(i).setVisibility(View.VISIBLE);
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {

            }
        });

        Encodings = null;
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
        else
            Utils.createInputAndCropsFolder();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           String permissions[], int[] grantResults) {
        switch (requestCode) {
            case RC_STORAGE_PERMISSION: {
                // If request is cancelled, the result arrays are empty.
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    Utils.showToast(this ,"Storage permission granted!");
                    Utils.createInputAndCropsFolder();
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
        Encodings = tfliteHandler.mEncodings;
    }

    /*
    public void getFBEncodings(View view){
        if(fbModelHandler == null)
            fbModelHandler = new FirebaseModelHandler(this);
        fbModelHandler.runFBModelInferenceOnAllCrops();
        Encodings = fbModelHandler.mEncodings;
    }*/

    public void getClusters(View view){
        if(Encodings == null){
            Utils.showToast(this, "No encodings to cluster!");
            return;
        }
        if(clusterMethod.equals(cw)){
            if(cwHandler == null)
                cwHandler = new ChineseWhispersHandler(this);

            cwHandler.performClustering(Encodings);
        }else {
            if (clusteringHandler == null)
                clusteringHandler = new ClusteringHandler();

            if (clusterMethod.equals(dbscan))
                clusteringHandler.DBScanClustering(Encodings);
            else if (clusterMethod.equals(kmeans))
                clusteringHandler.KMeansClustering(Encodings);
        }
    }

    public void getResults(View view){
        if(clusterMethod.equals(cw)){
            if(cwHandler == null){
                Utils.showToast(this, "No results to save!");
                return;
            }
            cwHandler.saveResults();
        }else {
            if(clusteringHandler == null){
                Utils.showToast(this, "No results to save!");
                return;
            }
            Utils.createResultsFolder(clusteringHandler, Encodings);
        }
    }
}
