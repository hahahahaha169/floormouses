package com.example.floormouse;

/**
 * Estimates whether a usable textured surface is facing the rear camera.
 * This is a confidence heuristic, not semantic identification of a floor.
 */
final class FloorSurfaceDetector {
    static final int WIDTH = MotionTracker.WIDTH;
    static final int HEIGHT = MotionTracker.HEIGHT;

    static final class Reading {
        final boolean locked;
        final String description;
        final float texture;
        final int brightness;
        Reading(boolean locked, String description, float texture, int brightness) {
            this.locked=locked;
            this.description=description;
            this.texture=texture;
            this.brightness=brightness;
        }
    }
    private boolean locked;
    private int goodStreak, badStreak;

    void reset() {
        locked=false;
        goodStreak=0;
        badStreak=0;
    }

    Reading evaluate(byte[] pixels, float upZ, int sensitivity) {
        if (pixels==null || pixels.length!=WIDTH*HEIGHT) {
            locked=false;
            return new Reading(false,"카메라 영상 대기",0,0);
        }
        long sum=0;
        long edgeSum=0;
        int count=0;
        for(int y=7;y<HEIGHT-7;y+=2) {
            for(int x=7;x<WIDTH-7;x+=2) {
                int p=y*WIDTH+x;
                int center=pixels[p]&255;
                sum+=center;
                edgeSum+=Math.abs(center-(pixels[p+1]&255));
                edgeSum+=Math.abs(center-(pixels[p+WIDTH]&255));
                count++;
            }
        }
        int brightness=count==0?0:(int)(sum/count);
        float texture=count==0?0f:(float)edgeSum/(2f*count);

        // In "easy" mode allow very subtle textures, but still avoid a dark
        // or completely saturated image from generating random pointer motion.
        final float minTexture=sensitivity==0?0.7f:sensitivity==2?2.5f:1.25f;
        final int minBrightness=sensitivity==0?12:19;
        final int maxBrightness=sensitivity==2?240:247;
        boolean facingGround=Float.isNaN(upZ) || upZ > (sensitivity==2?3.5f:2.0f);
        boolean goodLight=brightness>=minBrightness && brightness<=maxBrightness;
        boolean clearTexture=texture>=minTexture;
        boolean valid=facingGround && goodLight && clearTexture;

        if(valid){goodStreak++;badStreak=0;}
        else {badStreak++;goodStreak=0;}
        if(!locked && goodStreak>=2)locked=true;
        if(locked && badStreak>=3)locked=false;

        String message;
        if(!facingGround) message="후면 카메라를 바닥 쪽으로 향하게 하세요";
        else if(!goodLight) message="화면이 너무 어둡거나 밝아요";
        else if(!clearTexture) message="바닥 무늬가 부족해요. 다른 매트를 사용하세요";
        else if(!locked) message="바닥 인식 안정화 중...";
        else message="바닥 인식됨 · 이동 가능";

        return new Reading(locked,message,texture,brightness);
    }
}
