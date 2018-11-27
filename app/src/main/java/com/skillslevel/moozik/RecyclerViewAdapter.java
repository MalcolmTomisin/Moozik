package com.skillslevel.moozik;

import android.content.Context;
import android.provider.ContactsContract;
import android.support.v7.widget.CardView;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import java.util.Collections;
import java.util.List;

public class RecyclerViewAdapter extends RecyclerView.Adapter<RecyclerViewAdapter.MyViewHolder> {

    List <Audio> list = Collections.emptyList();
    Context context;

    public static class MyViewHolder extends RecyclerView.ViewHolder {
        // each data item is just a string in this case
        public View view;
        TextView title;
        ImageView play_pause;
        CardView cv;

        public MyViewHolder(View v) {
            super(v);
            view = v;
            title = (TextView) itemView.findViewById(R.id.text_view);
            play_pause = (ImageView) itemView.findViewById(R.id.play);
            cv = (CardView) itemView.findViewById(R.id.card_view);
        }
    }

    public RecyclerViewAdapter(List<Audio> list, Context context){
        this.list = list;
        this.context = context;
    }


    @Override
    public RecyclerViewAdapter.MyViewHolder onCreateViewHolder( ViewGroup parent, int i) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_layout, parent, false);
        MyViewHolder viewHolder = new MyViewHolder(v);
        return viewHolder;
    }

    @Override
    public void onBindViewHolder( MyViewHolder viewHolder, int position) {
        viewHolder.title.setText(list.get(position).getTitle());
        //holder.description.setText(list.get(position).description);
        // holder.imageView.setImageResource(list.get(position).imageId);
    }

    @Override
    public int getItemCount() {
        return list.size();
    }

    @Override
    public void onAttachedToRecyclerView(RecyclerView recyclerView) {
        super.onAttachedToRecyclerView(recyclerView);
    }

    // Insert a new item to the RecyclerView on a predefined position
    public void insert(int position, Audio data) {
        list.add(position, data);
        notifyItemInserted(position);
    }
    // Remove a RecyclerView item containing a specified Data object
    public void remove(Audio data) {
        int position = list.indexOf(data);
        list.remove(position);
        notifyItemRemoved(position);
    }
}
