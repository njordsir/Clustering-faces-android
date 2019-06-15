package com.cluster.facelabs.clusterface;

import android.content.Context;
import android.os.AsyncTask;
import android.os.Environment;
import android.util.Log;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import com.cluster.facelabs.clusterface.InferenceHelper.Encoding;

import org.apache.commons.io.FileUtils;

/**
 * Created by shankar.g on 3/14/2019.
 */

public class ChineseWhispersHandler {

    float cwThreshold = 30;
    int cwIter = 500;

    private class Edge{
        public int nbr;
        public float weight;
        public Edge(int _nbr, float _weight){
            nbr = _nbr;
            weight = _weight;
        }
    }

    private class Graph{
        ArrayList<Edge>[] adjLists;
        int[] clusters;

        public Graph(int N){
            adjLists = new ArrayList[N];
            clusters = new int[N];
            for(int i = 0; i < N; i++){
                adjLists[i] = new ArrayList<>();
                clusters[i] = i; /**assign to self cluster*/
            }

        }

        public void addEdge(int src, int dest, float weight){
            adjLists[src].add(new Edge(dest, weight));
            adjLists[dest].add(new Edge(src, weight));
        }
    }

    public Graph graph;
    private Context mContext;

    public ChineseWhispersHandler(Context context){
        mContext = context;
        graph = null;
    }

    String[] fileNames;
    float[][] encodings;

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

    private class AsyncMakeGraph extends AsyncTask<Void, Integer, Void>{

        @Override
        protected Void doInBackground(Void... voids) {
            int NUM_ENCODINGS = encodings.length;
            int DIM_ENCODING = encodings[0].length;

            for(int i = 0; i < NUM_ENCODINGS; i++) {
                float[] encoding = encodings[i];

                if(i == NUM_ENCODINGS-1) break; /**if last encoding*/

                for(int j = i+1; j < NUM_ENCODINGS; j++){
                    float[] nbrEncoding = encodings[j];
                    float dist = 0.0f;
                    for(int k = 0; k < DIM_ENCODING; k++)
                        dist += encoding[k]*nbrEncoding[k];
                    if(dist > cwThreshold){
                        graph.addEdge(i, j, dist);
                    }
                }
                publishProgress(i);
            }

            return null;
        }

        @Override
        protected void onProgressUpdate(Integer... values) {
            MainActivity.cwGraphProgressBar.setProgress(values[0]);
        }

        @Override
        protected void onPostExecute(Void aVoid) {
            MainActivity.clusteringProgressBar.setMax(cwIter);
            MainActivity.clusteringProgressBar.setProgress(0);
            new AsyncCWClustering().execute();
        }
    }

    private class AsyncCWClustering extends AsyncTask<Void, Integer, Void>{

        @Override
        protected Void doInBackground(Void... voids) {
            int NUM_ENCODINGS = encodings.length;

            for(int ci = 0; ci < cwIter; ci++){
                /**randomly loop through nodes*/
                for(int i = 0; i < NUM_ENCODINGS; i++) {

                    /**summed weights for the clusters of the nbrs*/
                    HashMap<Integer, Float> clusterWeights = new HashMap<>();

                    /**go through the nbrs and see which clusters they belong to*/
                    for(int j = 0; j < graph.adjLists[i].size(); j++){
                        Edge nbrEdge = graph.adjLists[i].get(j);
                        int nbr = nbrEdge.nbr;
                        float nbrWt = nbrEdge.weight;
                        int nbrCluster = graph.clusters[nbr];
                        if(clusterWeights.containsKey(nbrCluster))
                            clusterWeights.put(nbrCluster, clusterWeights.get(nbrCluster) + nbrWt);
                        else
                            clusterWeights.put(nbrCluster, nbrWt);
                    }

                    int maxCluster = -1;
                    float maxClusterWt = 0.0f;
                    Iterator it = clusterWeights.entrySet().iterator();
                    while (it.hasNext()){
                        Map.Entry pair = (Map.Entry)it.next();
                        int clusterId = (Integer) pair.getKey();
                        float clusterWt = (Float) pair.getValue();
                        if(clusterWt > maxClusterWt){
                            maxClusterWt = clusterWt;
                            maxCluster = clusterId;
                        }
                    }
                    graph.clusters[i] = maxCluster;
                }

                publishProgress(ci);
            }

            return null;
        }

        @Override
        protected void onProgressUpdate(Integer... values) {
            MainActivity.clusteringProgressBar.setProgress(values[0]);
        }

        @Override
        protected void onPostExecute(Void aVoid) {
            printClusterSizes();
        }
    }

    private void printClusterSizes(){
        HashMap<Integer, Integer> clusterSizes = new HashMap<>();
        for(int i = 0; i < graph.clusters.length; i++){
            if(clusterSizes.containsKey(graph.clusters[i]))
                clusterSizes.put(graph.clusters[i], clusterSizes.get(graph.clusters[i]) + 1);
            else
                clusterSizes.put(graph.clusters[i], 1);
        }
        Log.d("ChineseWhispersHandler", String.valueOf(clusterSizes.size()));

        String clusterOutputString = "Cluster counts : ";
        Iterator it = clusterSizes.entrySet().iterator();
        while(it.hasNext()){
            Map.Entry pair = (Map.Entry)it.next();
            int clusterId = (Integer) pair.getKey();
            int size = (Integer) pair.getValue();
            Log.d("ChineseWhispersHandler", String.valueOf(clusterId) + ":" + String.valueOf(size));
            clusterOutputString += (String.valueOf(size) + " ");
        }
        MainActivity.clusterResultsText.setText(clusterOutputString);
    }

    public void performClustering(HashMap<String, Encoding> Encodings){
        cwThreshold = Float.parseFloat(MainActivity.cwThreshText.getText().toString());

        getFloatEncodings(Encodings);

        int NUM_ENCODINGS = Encodings.size();

        graph = new Graph(NUM_ENCODINGS);

        /**CREATE GRAPH*/
        MainActivity.cwGraphProgressBar.setMax(NUM_ENCODINGS);
        MainActivity.cwGraphProgressBar.setProgress(0);
        new AsyncMakeGraph().execute();
    }

    public void saveResults(){

        if(graph == null){
            Utils.showToast(mContext, "No results to save!");
        }

        /**Save results*/
        String cropsDirPath = Utils.getCropsPath();
        String resultsDirPath = Utils.getResultsPath();
        File resultsDir = new File(resultsDirPath);

        try {FileUtils.deleteDirectory(resultsDir);}
        catch (IOException e) {
            e.printStackTrace();
            Log.d("ChineseWhispersHandler", "Could not delete previous results!");
        }

        boolean success = resultsDir.mkdirs();
        if(!success){
            Utils.showToast(mContext, "Could not create results folder!");
            return;
        }

        /*add the .nomedia file to each cluster folder*/
        for(int i = 0; i < graph.clusters.length; i++)
        {
            File clusterFolder = new File(resultsDirPath + "/" + graph.clusters[i]);
            clusterFolder.mkdirs();
            File noMedia = new File(resultsDirPath+"/"+graph.clusters[i]+"/.nomedia");
            if(!noMedia.exists()) {
                try {
                    noMedia.createNewFile();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }

        /*dummy progress bar
        * TODO add async saving*/
        MainActivity.saveResultsProgressBar.setMax(1);
        MainActivity.saveResultsProgressBar.setProgress(0);

        for(int i = 0; i < graph.clusters.length; i++){
            String sourcePath = cropsDirPath + "/" + fileNames[i];
            String destPath = resultsDirPath + "/" + graph.clusters[i] + "/" +  fileNames[i];
            File source = new File(sourcePath);
            File dest = new File(destPath);
            try {
                FileUtils.copyFile(source, dest);
                Log.d("ChineseWhispersHandler", "Copied result!");
            } catch (IOException e) {
                e.printStackTrace();
                Log.d("ChineseWhispersHandler", "Unable to copy image to results folder!");
            }
        }
        MainActivity.saveResultsProgressBar.setProgress(1);
    }
}
