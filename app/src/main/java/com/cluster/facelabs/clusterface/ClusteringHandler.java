package com.cluster.facelabs.clusterface;

import android.os.AsyncTask;
import android.util.Log;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import com.cluster.facelabs.clusterface.InferenceHelper.Encoding;

import org.apache.commons.math3.ml.clustering.Cluster;
import org.apache.commons.math3.ml.clustering.DBSCANClusterer;
import org.apache.commons.math3.ml.clustering.DoublePoint;

import static com.cluster.facelabs.clusterface.InferenceHelper.DIM_ENCODING;

public class ClusteringHandler {

    public List<Cluster<DoublePoint>> mDBClusters;
    private float mDBScanEps = 7;
    private int mDBScanMinPts = 30;

    String[] fileNames;
    float[][] encodings;
    List<KMeans.Mean> bestKMeans;
    /**no. of iterations to run the kmeans clustering for*/
    private static final int mClusterIter = 100;

    private class AsyncKmeans extends AsyncTask<Void, Integer, Void>{

        @Override
        protected Void doInBackground(Void... voids) {
            KMeans kmeans = new KMeans();

            /**get the number of desired cluster from user input*/
            int k = Integer.parseInt(MainActivity.kmeansKText.getText().toString());

            /**perform the clustering multiple times and choose the one with max score*/
            double bestScore = 0;
            bestKMeans = null;

            for(int km = 0; km < mClusterIter; km ++) {
                List<KMeans.Mean> means = kmeans.predict(k, encodings);
                double score = KMeans.score(means);
                if (score > bestScore) {
                    bestKMeans = means;
                    bestScore = score;
                }
                publishProgress(km);
            }

            return null;
        }

        @Override
        protected void onProgressUpdate(Integer... values) {
            MainActivity.clusteringProgressBar.setProgress(values[0]);
        }

        @Override
        protected void onPostExecute(Void aVoid) {
            showKMeansOutput();
        }
    }

    /**get all encodings from the hashmap*/
    private void getFloatEncodings(HashMap<String, Encoding> Encodings){
        int NUM_ENCODINGS = Encodings.size();
        int DIM_ENCODING = InferenceHelper.DIM_ENCODING;
        fileNames = new String[NUM_ENCODINGS];
        encodings = new float[NUM_ENCODINGS][DIM_ENCODING];
        Iterator it = Encodings.entrySet().iterator();
        int e = 0;
        while (it.hasNext()) {
            Map.Entry pair = (Map.Entry)it.next();
            String fileName = pair.getKey().toString();
            fileNames[e] = fileName;
            Encoding encoding = (Encoding) pair.getValue();
            System.arraycopy(encoding.enc, 0, encodings[e++], 0, DIM_ENCODING);
        }
    }

    public void KMeansClustering(HashMap<String, Encoding> Encodings){
        getFloatEncodings(Encodings);

        MainActivity.clusteringProgressBar.setMax(mClusterIter);
        MainActivity.clusteringProgressBar.setProgress(0);

        new AsyncKmeans().execute();
    }

    public void showKMeansOutput(){
        /**print the cluster for each crop*/
        int[] clusterSizes = new int[bestKMeans.size()]; //+1?

        for(int i = 0; i < encodings.length; i++){
            String fileName = fileNames[i];
            KMeans.Mean nearestMean = KMeans.nearestMean(encodings[i], bestKMeans);
            int clusterIdx = bestKMeans.indexOf(nearestMean);
            clusterSizes[clusterIdx] += 1;
            Log.d("cluster", fileName + " : " + clusterIdx);
        }

        String clusterOutputString = "Cluster counts : ";
        for(int i = 0; i < bestKMeans.size(); i++)
            clusterOutputString += (clusterSizes[i] + " ");
        MainActivity.clusterResultsText.setText(clusterOutputString);
        Log.d("cluster_debug", clusterOutputString);
    }

    int getKMeansClusterIdx(Encoding encoding){
        KMeans.Mean nearestMean = KMeans.nearestMean(encoding.enc, bestKMeans);
        return bestKMeans.indexOf(nearestMean);
    }

    public void DBScanClustering(HashMap<String, Encoding> Encodings){
        /**get all encodings as double point vectors*/
        List<DoublePoint> dEncodings = new ArrayList<>();
        Iterator it = Encodings.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry pair = (Map.Entry)it.next();
            Encoding encoding = (Encoding) pair.getValue();

            /**convert encodings to DoublePoint*/
            DoublePoint dbPoint;
            double[] p = new double[DIM_ENCODING];
            for(int i = 0; i < DIM_ENCODING; i++)
                p[i] = encoding.enc[i];
            dbPoint = new DoublePoint(p);
            dEncodings.add(dbPoint);
        }

        /**get clusters*/
        mDBScanEps = Float.parseFloat(MainActivity.dBScanEpsText.getText().toString());
        mDBScanMinPts = Integer.parseInt(MainActivity.dBScanMinPtsText.getText().toString());

        DBSCANClusterer dbscan = new DBSCANClusterer(mDBScanEps, mDBScanMinPts);

        mDBClusters = dbscan.cluster(dEncodings);
        Log.d("cluster_debug", String.valueOf(mDBClusters.size()));

        showDBScanOutput(Encodings.size());
    }

    void showDBScanOutput(int numPoints){
        /**inspect cluster distribution*/
        int clusteredPhotos = 0;
        String clusterOutputString = "";
        for(int i = 0; i < mDBClusters.size(); i++){
            int csize = mDBClusters.get(i).getPoints().size();
            clusteredPhotos += csize;
            clusterOutputString += (csize + "");
        }
        /**photos that are set to cluster -1*/
        int unclusteredPhotos = numPoints - clusteredPhotos;
        clusterOutputString = "Cluster counts : " + String.valueOf(unclusteredPhotos) + " " + clusterOutputString;

        MainActivity.clusterResultsText.setText(clusterOutputString);
        Log.d("cluster_debug", clusterOutputString);
    }

    /**get the cluster that the encoding belongs to*/
    int getDBScanClusterIdx(Encoding encoding){

        DoublePoint dbpointEncoding;
        double[] p = new double[DIM_ENCODING];
        for(int i = 0; i < DIM_ENCODING; i++)
            p[i] = encoding.enc[i];
        dbpointEncoding = new DoublePoint(p);

        for(int i = 0; i < mDBClusters.size(); i++) {
            List<DoublePoint> clusterPoints = mDBClusters.get(i).getPoints();
            for(int j = 0; j < clusterPoints.size(); j++)
                if(dbpointEncoding.equals(clusterPoints.get(j)))
                    return i;
        }
        return -1;
    }
}
