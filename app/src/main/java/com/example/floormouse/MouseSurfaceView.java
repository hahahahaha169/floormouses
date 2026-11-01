package com.example.floormouse;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;

/**
 * A physical-mouse-style control surface. The camera supplies pointer motion;
 * this view supplies mouse buttons and wheel gestures.
 */
public final class MouseSurfaceView extends View {
    public interface Listener {
        void buttonDown(int button);
        void buttonUp(int button);
        void wheel(int ticks);
    }

    private final Listener listener;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private int active = 0;
    private float downY, lastWheelY;
    private boolean wheelScrolled;
    private boolean leftHeld, rightHeld, wheelHeld;

    public MouseSurfaceView(Context context, Listener listener) {
        super(context);
        this.listener = listener;
        setLayerType(View.LAYER_TYPE_SOFTWARE, null);
        setContentDescription("실제 마우스 모양. 왼쪽과 오른쪽을 누르고, 가운데 휠을 쓸어 스크롤하세요.");
    }

    private void fill(int color) {
        paint.reset();
        paint.setAntiAlias(true);
        paint.setColor(color);
        paint.setStyle(Paint.Style.FILL);
    }

    private void line(int color, float width) {
        fill(color);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(width);
    }

    private void text(Canvas canvas, String label, int size, int color, float cx, float y) {
        fill(color);
        paint.setTextAlign(Paint.Align.CENTER);
        paint.setTypeface(android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL));
        paint.setTextSize(size);
        canvas.drawText(label, cx, y, paint);
    }

    private void body(Canvas c) {
        path.reset();
        path.moveTo(160, 13);
        path.cubicTo(245, 13, 301, 79, 301, 175);
        path.lineTo(301, 294);
        path.cubicTo(301, 397, 246, 439, 160, 439);
        path.cubicTo(74, 439, 19, 397, 19, 294);
        path.lineTo(19, 175);
        path.cubicTo(19, 79, 75, 13, 160, 13);
        path.close();
        fill(Color.rgb(17, 24, 38));
        paint.setShadowLayer(16, 0, 11, Color.argb(160, 0, 0, 0));
        c.drawPath(path, paint);
        paint.clearShadowLayer();
        paint.setShader(new LinearGradient(18, 50, 307, 440,
            new int[]{0xFF3B4658, 0xFF222C3B, 0xFF131C2A}, null, Shader.TileMode.CLAMP));
        c.drawPath(path, paint);
        paint.setShader(null);
        line(0xFF627287, 2.4f);
        c.drawPath(path, paint);
    }

    private void button(Canvas c, boolean left, boolean held) {
        path.reset();
        if (left) {
            path.moveTo(151, 25);
            path.cubicTo(87, 29, 34, 83, 32, 171);
            path.lineTo(32, 203);
            path.quadTo(92, 211, 152, 209);
            path.close();
        } else {
            path.moveTo(169, 25);
            path.cubicTo(233, 29, 286, 83, 288, 171);
            path.lineTo(288, 203);
            path.quadTo(228, 211, 168, 209);
            path.close();
        }
        fill(held ? 0xFF2F6DBF : 0xFF2D3747);
        c.drawPath(path, paint);
        line(held ? 0xFF8BC4FF : 0xFF465366, held ? 3 : 1.5f);
        c.drawPath(path, paint);
    }

    @Override protected void onDraw(Canvas real) {
        super.onDraw(real);
        float scale = Math.min(getWidth() / 320f, getHeight() / 455f);
        float tx = (getWidth() - 320 * scale) / 2;
        float ty = (getHeight() - 455 * scale) / 2;
        real.save();
        real.translate(tx, ty);
        real.scale(scale, scale);

        fill(0x4B6E85A8);
        real.drawOval(new RectF(38, 423, 282, 452), paint);
        body(real);
        button(real, true, leftHeld);
        button(real, false, rightHeld);

        line(0xFF708198, 2);
        real.drawLine(160, 25, 160, 213, paint);
        line(0xFF64748B, 3);
        real.drawLine(34, 204, 123, 213, paint);
        real.drawLine(197, 213, 286, 204, paint);

        fill(0xFF101A29);
        real.drawRoundRect(new RectF(145, 83, 175, 179), 15, 15, paint);
        fill(wheelHeld ? 0xFF8ACDFF : 0xFF677D96);
        real.drawRoundRect(new RectF(150, 88, 170, 169), 9, 9, paint);
        line(0xFF273D50, 2);
        for (int y = 98; y <= 158; y += 9) real.drawLine(155, y, 165, y, paint);

        text(real, "왼쪽", 17, leftHeld ? 0xFFFFFFFF : 0xFFDAE7F9, 93, 161);
        text(real, "오른쪽", 17, rightHeld ? 0xFFFFFFFF : 0xFFDAE7F9, 225, 161);
        text(real, "위아래로 쓸기", 13, 0xFF9AAEC6, 160, 258);

        fill(0xFF162333);
        real.drawRoundRect(new RectF(78, 310, 242, 374), 32, 32, paint);
        fill(0xFF4BA8FF);
        real.drawCircle(110, 342, 6, paint);
        text(real, "바닥 움직임 인식", 14, 0xFFD9EAFD, 174, 347);

        real.restore();
    }

    private int hit(float x, float y) {
        float scale = Math.min(getWidth() / 320f, getHeight() / 455f);
        if (scale <= 0) return 0;
        float a = (x - (getWidth() - 320 * scale) / 2) / scale;
        float b = (y - (getHeight() - 455 * scale) / 2) / scale;
        if (b < 55 || b > 225 || a < 29 || a > 291) return 0;
        if (a >= 135 && a <= 185 && b >= 75 && b <= 193) return 4;
        return a < 160 ? 1 : 2;
    }

    @Override public boolean onTouchEvent(MotionEvent event) {
        if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
            getParent().requestDisallowInterceptTouchEvent(true);
            active = hit(event.getX(), event.getY());
            downY = event.getY();
            lastWheelY = downY;
            wheelScrolled = false;
            if (active == 1 || active == 2) {
                listener.buttonDown(active);
                leftHeld = active == 1;
                rightHeld = active == 2;
                performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
            } else if (active == 4) {
                wheelHeld = true;
                performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
            }
            invalidate();
            return true;
        }
        if (event.getActionMasked() == MotionEvent.ACTION_MOVE) {
            if (active == 4) {
                float distance = event.getY() - lastWheelY;
                int steps = (int) (distance / (getResources().getDisplayMetrics().density * 23));
                if (steps != 0) {
                    listener.wheel(-steps);
                    wheelScrolled = true;
                    lastWheelY += steps * getResources().getDisplayMetrics().density * 23;
                    performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
                }
            }
            return true;
        }
        if (event.getActionMasked() == MotionEvent.ACTION_UP ||
            event.getActionMasked() == MotionEvent.ACTION_CANCEL) {
            if (active == 1 || active == 2) listener.buttonUp(active);
            if (active == 4 && !wheelScrolled &&
                Math.abs(event.getY()-downY) < getResources().getDisplayMetrics().density*14 &&
                event.getActionMasked() == MotionEvent.ACTION_UP) {
                listener.buttonDown(4);
                postDelayed(() -> listener.buttonUp(4), 90);
            }
            active = 0;
            leftHeld = false; rightHeld = false; wheelHeld = false;
            invalidate();
            return true;
        }
        return true;
    }
}
