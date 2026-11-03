package com.example.floormouse;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.os.SystemClock;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;

/** Mouse touchpad: one-finger pointer, two-finger scrolling and clickable buttons. */
public final class TouchpadSurfaceView extends View {
    public interface Listener {
        void move(float dx, float dy);
        void wheel(int ticks);
        void click(int button);
        void buttonDown(int button);
        void buttonUp(int button);
    }
    private final Listener listener;
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
    private int mode=0; // 0 none, 1 pointer, 2 scroll, 3 left, 4 right, 5 drag
    private float lastX,lastY, scrollRemainder;
    private float downX,downY;
    private long downTime,lastTapTime;
    private float lastTapX,lastTapY;
    private boolean moved;
    private int downPointerId=-1;
    private final float density;

    public TouchpadSurfaceView(Context c,Listener l){
        super(c);
        listener=l;
        density=getResources().getDisplayMetrics().density;
        setFocusable(true);
        setContentDescription("터치패드. 한 손가락 이동, 두 손가락 스크롤, 아래쪽 왼쪽 및 오른쪽 클릭");
    }
    private void color(int c){paint.setColor(c);paint.setStyle(Paint.Style.FILL);}
    private void round(Canvas c,float a,float b,float d,float e,float r,int paintColor){
        color(paintColor);c.drawRoundRect(new RectF(a,b,d,e),r,r,paint);
    }
    private void label(Canvas c,String text,float size,float x,float y,int color){
        color(color);paint.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));
        paint.setTextSize(size*density);paint.setTextAlign(Paint.Align.CENTER);
        c.drawText(text,x,y,paint);
    }
    private float buttonTop(){return getHeight()-82*density;}
    @Override protected void onDraw(Canvas c){
        super.onDraw(c);
        float w=getWidth(), h=getHeight(), margin=5*density;
        round(c,margin,margin,w-margin,buttonTop()-9*density,23*density,0xFF1A2C44);
        color(0xFF425F80);
        paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(1.5f*density);
        c.drawRoundRect(new RectF(margin,margin,w-margin,buttonTop()-9*density),23*density,23*density,paint);
        label(c,"터치패드",22,w/2,56*density,0xFFE5F0FF);
        label(c,"한 손가락 · 포인터 이동",14,w/2,86*density,0xFFACC4DF);
        label(c,"두 손가락 · 위아래 스크롤",14,w/2,111*density,0xFFACC4DF);
        float y=buttonTop();
        round(c,margin,y,w/2-3*density,h-margin,15*density,
            mode==3?0xFF2563AA:0xFF293B54);
        round(c,w/2+3*density,y,w-margin,h-margin,15*density,
            mode==4?0xFF2563AA:0xFF293B54);
        label(c,"왼쪽 클릭",17,w/4,y+48*density,0xFFF5FAFF);
        label(c,"오른쪽 클릭",17,3*w/4,y+48*density,0xFFF5FAFF);
    }
    private void finish(){
        if(mode==3)listener.buttonUp(1);
        if(mode==4)listener.buttonUp(2);
        if(mode==5)listener.buttonUp(1);
        mode=0;downPointerId=-1;scrollRemainder=0;
        invalidate();
    }
    public void cancelTouch(){
        finish();
    }
    private float pairY(MotionEvent e){
        return (e.getY(0)+e.getY(1))*0.5f;
    }
    @Override public boolean onTouchEvent(MotionEvent e){
        final int action=e.getActionMasked();
        if(action==MotionEvent.ACTION_DOWN){
            getParent().requestDisallowInterceptTouchEvent(true);
            downPointerId=e.getPointerId(0);
            lastX=downX=e.getX();
            lastY=downY=e.getY();
            downTime=SystemClock.uptimeMillis();
            moved=false;
            if(downY>=buttonTop()){
                mode=downX<getWidth()*0.5f?3:4;
                listener.buttonDown(mode==3?1:2);
                performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
            }else{
                boolean doubleTap=(downTime-lastTapTime<290 &&
                    Math.abs(downX-lastTapX)<28*density &&
                    Math.abs(downY-lastTapY)<28*density);
                if(doubleTap){
                    mode=5;listener.buttonDown(1);
                    lastTapTime=0L;
                }else mode=1;
            }
            invalidate();
            return true;
        }
        if(action==MotionEvent.ACTION_POINTER_DOWN && e.getPointerCount()>=2){
            if(mode==5)listener.buttonUp(1);
            if(mode==1||mode==5){
                mode=2;
                lastY=pairY(e);
                scrollRemainder=0;
                moved=true;
            }
            return true;
        }
        if(action==MotionEvent.ACTION_MOVE){
            if(mode==1||mode==5){
                int index=e.findPointerIndex(downPointerId);
                if(index<0)index=0;
                if(index>=e.getPointerCount())return true;
                float x=e.getX(index),y=e.getY(index);
                float dx=x-lastX,dy=y-lastY;
                if(Math.abs(x-downX)+Math.abs(y-downY)>9*density)moved=true;
                if(dx!=0||dy!=0)listener.move(dx,dy);
                lastX=x;lastY=y;
            }else if(mode==2 && e.getPointerCount()>=2){
                float y=pairY(e);
                float units=(lastY-y)/(27*density)+scrollRemainder;
                int ticks=(int)units;
                scrollRemainder=units-ticks;
                ticks=Math.max(-5,Math.min(5,ticks));
                if(ticks!=0)listener.wheel(ticks);
                lastY=y;
            }
            return true;
        }
        if(action==MotionEvent.ACTION_POINTER_UP){
            if(mode==2 && e.getPointerCount()<=2){
                mode=0;scrollRemainder=0;
            }
            return true;
        }
        if(action==MotionEvent.ACTION_UP){
            if(mode==1 && !moved &&
                 SystemClock.uptimeMillis()-downTime<=270){
                listener.click(1);
                lastTapTime=SystemClock.uptimeMillis();
                lastTapX=downX;lastTapY=downY;
                performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
            }
            finish();return true;
        }
        if(action==MotionEvent.ACTION_CANCEL){finish();return true;}
        return true;
    }
}
