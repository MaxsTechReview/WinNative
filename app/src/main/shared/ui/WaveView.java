package com.winlator.cmod.shared.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.util.AttributeSet;
import android.view.View;

public class WaveView extends View {
  private Paint paint;
  private Path path;
  private float phase = 0f;
  private float amplitude = 100f; // Height of the wave based on speed
  private float frequency = 0.01f;

  public WaveView(Context context) {
    super(context);
    init();
  }

  public WaveView(Context context, AttributeSet attrs) {
    super(context, attrs);
    init();
  }

  public WaveView(Context context, AttributeSet attrs, int defStyleAttr) {
    super(context, attrs, defStyleAttr);
    init();
  }

  private void init() {
    paint = new Paint();
    paint.setColor(Color.parseColor("#1E88E5"));
    paint.setStyle(Paint.Style.FILL);
    paint.setAntiAlias(true);
    path = new Path();
  }

  public void setSpeed(float speed) {
    // Speed from 0 to 100 Mbps
    amplitude = 50f + (speed * 2f); // Max 250f
    invalidate();
  }

  @Override
  protected void onDraw(Canvas canvas) {
    super.onDraw(canvas);
    int width = getWidth();
    int height = getHeight();

    path.reset();

    // Base line of the wave (starts higher up for higher speed)
    float baseHeight = height - (amplitude * 1.5f) - 50f;

    path.moveTo(width, height);
    path.lineTo(0, height);
    path.lineTo(0, baseHeight);

    for (int x = 0; x <= width; x += 10) {
      float y = (float) Math.sin(x * frequency + phase) * amplitude + baseHeight;
      path.lineTo(x, y);
    }

    path.lineTo(width, height);
    path.close();

    canvas.drawPath(path, paint);

    // Move wave right to left
    phase -= 0.1f;
    postInvalidateDelayed(16);
  }
}
