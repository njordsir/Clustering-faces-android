package com.cluster.facelabs.clusterface;

import android.util.Log;

import java.util.ArrayList;
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
    private String mClusterOutputString;
    private float mDBScanEps = 7;
    private int mDBScanMinPts = 30;

    public void DBScanClustering(FirebaseModelHandler fbHandler){
        /**get all encodings as double point vectors*/
        List<DoublePoint> dEncodings = new ArrayList<>();
        Iterator it = fbHandler.mEncodings.entrySet().iterator();
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

        /**inspect cluster distribution*/
        int clusteredPhotos = 0;
        mClusterOutputString = "";
        for(int i = 0; i < mDBClusters.size(); i++){
            int csize = mDBClusters.get(i).getPoints().size();
            clusteredPhotos += csize;
            mClusterOutputString += (csize + "");
        }
        /**photos that are set to cluster -1*/
        int unclusteredPhotos = fbHandler.mEncodings.size() - clusteredPhotos;
        mClusterOutputString = "Cluster counts : " + String.valueOf(unclusteredPhotos) + " " + mClusterOutputString;

        MainActivity.clusterResultsText.setText(mClusterOutputString);
        Log.d("cluster_debug", mClusterOutputString);
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
