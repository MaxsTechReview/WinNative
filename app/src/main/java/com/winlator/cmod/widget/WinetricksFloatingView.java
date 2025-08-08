package com.winlator.cmod.widget;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.PointF;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.preference.PreferenceManager;

import com.winlator.cmod.R;

public class WinetricksFloatingView extends LinearLayout {
    private SharedPreferences preferences;
    private final PointF startPoint = new PointF();
    private boolean isDragging = false;
    private short lastX, lastY;
    private boolean restoreSavedPosition = true;

    // UI references
    private EditText editVerb;
    private TextView textOutput;
    private Button btnExecuteWinetricks;
    private Button btnExecuteWinetricksLatest;
    private Button btnOpenWinetricksFolder;
    private Button btnTransparentToggle;
    private Button btnMinimize;

    // Callbacks
    private WinetricksListener listener;

    public WinetricksFloatingView(Context context) {
        this(context, null);
    }

    public WinetricksFloatingView(Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public WinetricksFloatingView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        setLayoutParams(
                new LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        );
        setOrientation(HORIZONTAL);

        // Inflate your "dialog" layout
        View contentView = LayoutInflater.from(getContext())
                .inflate(R.layout.winetricks_content_dialog, this, false);

        // Because we want to replicate MagnifierView's move handle,
        // we can add a small "title bar" or "move" button at the top or side:
        // Or you can reuse an existing button as a handle for dragging.
        // For example, we might have a Move button in the layout or add it programmatically.
        // For simplicity, let's assume the layout has a "move handle" with ID R.id.btnMove

        // Now find your normal widgets inside that layout
        editVerb = contentView.findViewById(R.id.editWinetricksVerb);
        textOutput = contentView.findViewById(R.id.textWinetricksOutput);
        btnExecuteWinetricks = contentView.findViewById(R.id.btnExecuteWinetricks);
//        btnExecuteWinetricksLatest = contentView.findViewById(R.id.btnExecuteWinetricksLatest);
        btnOpenWinetricksFolder = contentView.findViewById(R.id.btnOpenWinetricksFolder);
        btnTransparentToggle = contentView.findViewById(R.id.btnTransparentToggle);
        btnMinimize = contentView.findViewById(R.id.btnHideWinetricks);
        Button btnRestartWineserver = contentView.findViewById(R.id.btnRestartWineserver);

        // Let's assume we have a <LinearLayout> with ID rightLayout in the XML
        // or we can just add the Minimize button anywhere:
        LinearLayout rightLayout = contentView.findViewById(R.id.rightLayout);



        // Setup dragging on some "move" handle:
        View moveHandle = contentView.findViewById(R.id.btnMove);
        if (moveHandle != null) {
            moveHandle.setOnTouchListener((v, event) -> {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        startPoint.set(event.getX(), event.getY());
                        isDragging = true;
                        break;
                    case MotionEvent.ACTION_MOVE:
                        if (isDragging) {
                            float newX = getX() + (event.getX() - startPoint.x);
                            float newY = getY() + (event.getY() - startPoint.y);
                            movePanel(newX, newY);
                        }
                        break;
                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL:
                        isDragging = false;
                        savePositionIfValid(); // Save position after dragging
                        break;
                }
                return true;
            });
        }


        // Now wire up all your normal Winetricks button clicks
        btnExecuteWinetricks.setOnClickListener(v -> {
            if (listener != null) {
                String verb = editVerb.getText().toString().trim();
                listener.onWinetricksStableClick(verb, textOutput);
            }
        });

//        btnExecuteWinetricksLatest.setOnClickListener(v -> {
//            if (listener != null) {
//                String verb = editVerb.getText().toString().trim();
//                listener.onWinetricksLatestClick(verb, textOutput);
//            }
//        });

        btnOpenWinetricksFolder.setOnClickListener(v -> {
            if (listener != null) {
                listener.onOpenWinetricksFolder(textOutput);
            }
        });

        btnTransparentToggle.setOnClickListener(v -> {
            if (listener != null) {
                listener.onToggleTransparency(WinetricksFloatingView.this);
            }
        });

        btnMinimize.setOnClickListener(v -> {
            setVisibility(View.GONE); // or removeView(...) from parent if you prefer
        });

        btnRestartWineserver.setOnClickListener(v -> {
            if (listener != null) {
                // We can pass the same outputView used for logs
                listener.onRestartWineserverClick(textOutput);
            }
        });

        // (Optional) Load SharedPreferences if you want to store position
        preferences = PreferenceManager.getDefaultSharedPreferences(getContext());

        // Add the inflated content
        addView(contentView);
    }

    private void movePanel(float x, float y) {
        View parent = (View) getParent();
        if (parent == null) return;

        // Get parent dimensions
        int parentWidth = parent.getWidth();
        int parentHeight = parent.getHeight();

        // Get current dimensions of the floating view
        int width = getWidth();
        int height = getHeight();

        // Define margins to keep the floating view within bounds
        final int margin = 16; // Margin in pixels to ensure some padding

        // Clamp position within the parent's bounds
        if (x < margin) x = margin;
        if (y < margin) y = margin;
        if (x + width > parentWidth - margin) x = parentWidth - width - margin;
        if (y + height > parentHeight - margin) y = parentHeight - height - margin;

        // Set new position
        setX(x);
        setY(y);

        // Save the position
        lastX = (short) x;
        lastY = (short) y;
    }


    private void savePositionIfValid() {
        if (lastX > 0 && lastY > 0) {
            preferences.edit().putString("winetricks_view", lastX + "|" + lastY).apply();
        }
        lastX = 0;
        lastY = 0;
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);

        if (changed) {
            View parent = (View) getParent();
            if (parent != null) {
                float x = getX();
                float y = getY();

                // Get parent dimensions
                int parentWidth = parent.getWidth();
                int parentHeight = parent.getHeight();

                // Get current dimensions of the floating view
                int width = getWidth();
                int height = getHeight();

                // Define margins to keep the floating view within bounds
                final int margin = 16;

                // Adjust position if the size change causes it to go out of bounds
                if (x + width > parentWidth - margin) x = parentWidth - width - margin;
                if (y + height > parentHeight - margin) y = parentHeight - height - margin;

                // Set corrected position
                setX(Math.max(x, margin));
                setY(Math.max(y, margin));
            }
        }
    }


    // A small interface to delegate clicks back to your Activity
    public void setWinetricksListener(WinetricksListener listener) {
        this.listener = listener;
    }

    public interface WinetricksListener {
        void onWinetricksStableClick(String verb, TextView outputView);
        void onWinetricksLatestClick(String verb, TextView outputView);
        void onOpenWinetricksFolder(TextView outputView);
        void onToggleTransparency(View floatingView);

        // NEW method for restart
        void onRestartWineserverClick(TextView outputView);
    }

}
