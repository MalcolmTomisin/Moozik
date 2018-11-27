package com.skillslevel.moozik;

import android.content.Context;
import android.support.v7.widget.RecyclerView;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;

public class CustomTouchListener implements RecyclerView.OnItemTouchListener {

    GestureDetector gestureDetector;
    private RecyclerViewClickListener clickListener;

    public CustomTouchListener(Context context, final RecyclerView recyclerView, final RecyclerViewClickListener
            clickListener) {
        this.clickListener = clickListener;
        gestureDetector = new GestureDetector(context, new GestureDetector.SimpleOnGestureListener() {

            @Override
            public boolean onSingleTapUp(MotionEvent e) {
                return true;
            }


            @Override
            public void onLongPress(MotionEvent e) {
                View child = recyclerView.findChildViewUnder(e.getX(), e.getY());
                if (child != null && clickListener != null) {
                    clickListener.onLongClick(child, recyclerView.getChildLayoutPosition(child));
                }
            }
        });

    }
    @Override
    public boolean onInterceptTouchEvent( RecyclerView recyclerView,  MotionEvent e) {

        View child = recyclerView.findChildViewUnder(e.getX(), e.getY());

        if (child != null && clickListener != null && gestureDetector.onTouchEvent(e)){
            clickListener.onClick(child, recyclerView.getChildLayoutPosition(child));
        }
        return false;
    }

    @Override
    public void onTouchEvent(RecyclerView recyclerView,  MotionEvent e) {

    }

    @Override
    public void onRequestDisallowInterceptTouchEvent(boolean b) {

    }
}
