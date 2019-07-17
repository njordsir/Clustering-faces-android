package com.cluster.facelabs.clusterface.Gallery;

import android.content.Intent;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.GridLayoutManager;
import android.support.v7.widget.RecyclerView;

import com.cluster.facelabs.clusterface.R;
import com.cluster.facelabs.clusterface.Utils;

import java.io.File;
import java.io.FilenameFilter;
import java.util.ArrayList;

public class GalleryActivity extends AppCompatActivity {

    RecyclerView recyclerView;
    GridLayoutManager gridLayoutManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.gallery_layout);

        recyclerView = findViewById(R.id.recyclerView);
        gridLayoutManager = new GridLayoutManager(getApplicationContext(), 4);
        recyclerView.setLayoutManager(gridLayoutManager);

        Intent intent = getIntent();
        if(intent.getIntExtra("mode", 1) == 1)
            showPeopleGallery();
        else
            showImageGallery(intent.getStringExtra("personIdx"));
    }

    private ArrayList<String> preparePeopleList()
    {
        String resultsPath = Utils.getResultsPath();
        File[] people = new File(resultsPath).listFiles();

        ArrayList imageUrlList = new ArrayList<>();
        for(int i = 0; i < people.length; i++)
        {
            if(people[i].getName().equals("-1"))
                continue;
            File[] images = people[i].listFiles(new FilenameFilter() {
                public boolean accept(File dir, String name) {
                    return name.toLowerCase().endsWith(".jpg");
                }
            });
            imageUrlList.add(images[0].getAbsolutePath());
        }
        return imageUrlList;
    }

    private ArrayList<String> prepareImageList(String personIdx)
    {
        String personPath = Utils.getResultsPath() + "/" + personIdx;
        File[] images = new File(personPath).listFiles(new FilenameFilter() {
            public boolean accept(File dir, String name) {
                return name.toLowerCase().endsWith(".jpg");
            }
        });
        ArrayList imageUrlList = new ArrayList<>();
        for(File image : images) {
            //show crop
            imageUrlList.add(image.getAbsolutePath());

            //show image
            //String[] splits = image.getAbsolutePath().split("/");
            //String cropName = splits[splits.length-1];
            //String inputName  = Utils.getInputPath() + "/" + cropName.substring(0, cropName.lastIndexOf('_')) + "." + FilenameUtils.getExtension(image.getName());
            //imageUrlList.add(inputName);
        }
        return imageUrlList;
    }

    private void showPeopleGallery()
    {
        ArrayList<String> peopleList = preparePeopleList();
        DataAdapter dataAdapter = new DataAdapter(getApplicationContext(), peopleList, 1);
        recyclerView.setAdapter(dataAdapter);
    }

    private void showImageGallery(String personIdx)
    {
        ArrayList<String> peopleList = prepareImageList(personIdx);
        DataAdapter dataAdapter = new DataAdapter(getApplicationContext(), peopleList, 2);
        recyclerView.setAdapter(dataAdapter);
    }
}