package com.cluster.facelabs.clusterface.Gallery;

import android.content.Context;
import android.content.Intent;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import com.bumptech.glide.Glide;
import com.cluster.facelabs.clusterface.R;
import com.cluster.facelabs.clusterface.Utils;

import java.io.File;
import java.util.ArrayList;

public class DataAdapter extends RecyclerView.Adapter<DataAdapter.ViewHolder> {
    private ArrayList<String> imageUrls;
    private Context context;
    private int mode;

    public DataAdapter(Context context, ArrayList<String> imageUrls, int mode) {
        this.context = context;
        this.imageUrls = imageUrls;
        this.mode = mode;
    }

    @Override
    public DataAdapter.ViewHolder onCreateViewHolder(ViewGroup viewGroup, int i) {
        View view = LayoutInflater.from(viewGroup.getContext()).inflate(R.layout.gallery_item_layout, viewGroup, false);
        return new ViewHolder(view);
    }

    /**
     * gets the image url from adapter and passes to Glide API to load the image
     *
     * @param viewHolder
     * @param i
     */
    @Override
    public void onBindViewHolder(final ViewHolder viewHolder, final int i) {
        Glide.with(context).load(imageUrls.get(i)).into(viewHolder.img);

        if(mode == 1) {
            String[] splits = imageUrls.get(i).split("/");
            final String personIdx = splits[splits.length-2];

            int count = new File(Utils.getResultsPath()+"/"+personIdx).listFiles().length - 1;
            viewHolder.txtView.setText(String.valueOf(count) + "photos");

            viewHolder.img.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    Intent intent = new Intent(v.getContext(), GalleryActivity.class);
                    intent.putExtra("mode", 2);
                    intent.putExtra("personIdx", personIdx);
                    v.getContext().startActivity(intent);
                }
            });
        }
        else {
            viewHolder.img.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    Intent intent = new Intent(v.getContext(), ImageActivity.class);
                    intent.putExtra("cropPath", imageUrls.get(i));
                    v.getContext().startActivity(intent);
                }
            });
        }
    }

    @Override
    public int getItemCount() {
        return imageUrls.size();
    }

    public class ViewHolder extends RecyclerView.ViewHolder {
        ImageView img;
        TextView txtView;

        public ViewHolder(View view) {
            super(view);
            img = view.findViewById(R.id.imageView);
            txtView = view.findViewById(R.id.textView);
        }
    }
}
