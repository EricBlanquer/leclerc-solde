package com.eric.leclercsolde;

import android.content.Context;
import android.util.AttributeSet;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;

public class MaxHeightRecyclerView extends RecyclerView {

    private static final float MAX_SCREEN_FRACTION = 0.4f;

    public MaxHeightRecyclerView(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    protected void onMeasure(int widthSpec, int heightSpec) {
        int maxHeight = (int) (getResources().getDisplayMetrics().heightPixels * MAX_SCREEN_FRACTION);
        int mode = MeasureSpec.getMode(heightSpec);
        if (mode == MeasureSpec.UNSPECIFIED) {
            super.onMeasure(widthSpec, MeasureSpec.makeMeasureSpec(maxHeight, MeasureSpec.AT_MOST));
        } else {
            super.onMeasure(widthSpec, MeasureSpec.makeMeasureSpec(Math.min(maxHeight, MeasureSpec.getSize(heightSpec)), mode));
        }
    }
}
