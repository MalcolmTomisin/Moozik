package com.skillslevel.moozik;

import android.view.View;

public interface RecyclerViewClickListener {
    public void onClick(View view, int position);
    public void onLongClick(View view, int position);
}