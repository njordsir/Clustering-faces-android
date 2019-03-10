package com.cluster.facelabs.clusterface;

import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;

public class MainActivity extends AppCompatActivity {

    TfliteHandler tfliteHandler;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        tfliteHandler = new TfliteHandler(this, MainActivity.this);
    }
}
