package com.cluster.facelabs.clusterface.Gallery;

import android.content.Intent;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.widget.ImageView;

import com.bumptech.glide.Glide;
import com.cluster.facelabs.clusterface.R;
import com.cluster.facelabs.clusterface.Utils;

import org.apache.commons.io.FilenameUtils;

public class ImageActivity extends AppCompatActivity {

    ImageView imgView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.image);

        imgView = findViewById(R.id.imageView2);
        Intent intent = getIntent();
        String cropPath = intent.getStringExtra("cropPath");
        String[] splits = cropPath.split("/");
        String cropName = splits[splits.length-1];
        String inputName  = Utils.getInputPath() + "/" + cropName.substring(0, cropName.lastIndexOf('_')) + "." + FilenameUtils.getExtension(cropName);

        Glide.with(this).load(inputName).into(imgView);
    }
}
