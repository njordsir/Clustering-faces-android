package com.cluster.facelabs.clusterface;

import java.io.Serializable;

/**
 * Created by shankar.g on 3/13/2019.
 */

public class InferenceHelper {

    public static final String LOCAL_MODEL_ASSET = "sandberg.tflite";
    /**image dimensions*/
    public static final int DIM_X = 160;
    public static final int DIM_Y = 160;
    public static final int DIM_Z = 3;
    /**encoding dimension*/
    public static final int DIM_ENCODING = 128;

    public static class Encoding implements Serializable{
        float[] enc;

        Encoding(float[] values){
            enc = new float[DIM_ENCODING];
            System.arraycopy(values, 0, enc, 0, DIM_ENCODING);
        }
    }
}
