package com.example.floormouse;

/** Filters gyroscope angular velocity into smooth relative mouse reports. */
final class GyroPointer {
    static final class Delta {
        final int dx, dy;
        Delta(int dx, int dy){this.dx=dx;this.dy=dy;}
    }
    private long lastNs;
    private float filteredX,filteredY,remainderX,remainderY;

    void reset(){
        lastNs=0;
        filteredX=filteredY=remainderX=remainderY=0f;
    }

    Delta update(float angularX,float angularY,long timestampNs,
                 float pointerSpeed,float gyroSensitivity,
                 boolean reverseX,boolean reverseY) {
        long old=lastNs;lastNs=timestampNs;
        if(old==0L)return new Delta(0,0);
        float elapsed=(timestampNs-old)*1e-9f;
        if(elapsed<0.003f||elapsed>0.10f){
            filteredX=filteredY=0;return new Delta(0,0);
        }
        // Screen portrait: side-to-side wrist rotation maps to horizontal.
        float vx=-angularY;
        float vy=-angularX;
        float deadZone=0.035f;
        if(Math.abs(vx)<deadZone)vx=0f;
        if(Math.abs(vy)<deadZone)vy=0f;
        if(reverseX)vx=-vx;
        if(reverseY)vy=-vy;
        filteredX=filteredX*0.48f+vx*0.52f;
        filteredY=filteredY*0.48f+vy*0.52f;
        if(vx==0f && Math.abs(filteredX)<0.007f)filteredX=0f;
        if(vy==0f && Math.abs(filteredY)<0.007f)filteredY=0f;

        float scale=1000f*gyroSensitivity*Math.max(0.2f,pointerSpeed)/2.5f;
        float x=filteredX*elapsed*scale+remainderX;
        float y=filteredY*elapsed*scale+remainderY;
        int dx=Math.max(-127,Math.min(127,Math.round(x)));
        int dy=Math.max(-127,Math.min(127,Math.round(y)));
        remainderX=x-dx;
        remainderY=y-dy;
        return new Delta(dx,dy);
    }
}
