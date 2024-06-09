package com.example.shoprecog;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.util.AttributeSet;
import android.view.MotionEvent;

import androidx.appcompat.widget.AppCompatImageButton;
import androidx.camera.core.ExperimentalGetImage;
import androidx.core.content.ContextCompat;

@ExperimentalGetImage public class CustomImageButton extends AppCompatImageButton {

    private static final int COLOR_PRESSED = Color.GREEN; // Color when button is pressed
    private static final int COLOR_DEFAULT = Color.parseColor("#3D6EBF");

    private MainActivity mainActivity;

    public CustomImageButton(Context context) {
        super(context);
        init(context);
    }

    public CustomImageButton(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    public CustomImageButton(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context);
    }

    private void init(Context context) {
        if (context instanceof MainActivity) {
            mainActivity = (MainActivity) context;
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {

        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                // Handle touch down event
                // Change background color when pressed
                setBackgroundTintList(ColorStateList.valueOf(COLOR_PRESSED));
                mainActivity.startSpeechRecognition();
                return true; // Consume the event
            case MotionEvent.ACTION_UP:
                // Revert back to original color when released or canceled
                setBackgroundTintList(ColorStateList.valueOf(COLOR_DEFAULT));
                // Handle touch up event
                mainActivity.stopSpeechRecognition();
                return true; // Consume the event
            default:
                return super.onTouchEvent(event); // Let other events pass through
        }

    }
}
