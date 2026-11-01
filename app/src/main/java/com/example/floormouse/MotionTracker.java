package com.example.floormouse;

final class MotionTracker {
    static final int WIDTH = 80, HEIGHT = 60, SEARCH = 6;
    private byte[] previous;
    static class Motion {
        final int dx, dy;
        Motion(int x, int y) { dx = x; dy = y; }
    }

    void reset() { previous = null; }

    Motion track(byte[] frame) {
        if (frame.length != WIDTH * HEIGHT) return null;
        byte[] old = previous;
        previous = frame.clone();
        if (old == null) return null;

        int texture = 0, count = 0;
        for (int y = 10; y < HEIGHT - 10; y += 3) {
            for (int x = 10; x < WIDTH - 10; x += 3) {
                int p = y * WIDTH + x;
                texture += Math.abs((frame[p] & 255) - (frame[p+1] & 255));
                texture += Math.abs((frame[p] & 255) - (frame[p+WIDTH] & 255));
                count += 2;
            }
        }
        if (count == 0 || (double)texture / count < 2.0) return null;
        long best = Long.MAX_VALUE, zero = Long.MAX_VALUE;
        int bx = 0, by = 0, samples = 0;
        for (int dy = -SEARCH; dy <= SEARCH; dy++) {
            for (int dx = -SEARCH; dx <= SEARCH; dx++) {
                long err = 0; int n = 0;
                for (int y = 9; y < HEIGHT - 9; y += 2) {
                    for (int x = 9; x < WIDTH - 9; x += 2) {
                        int a = frame[y*WIDTH+x] & 255;
                        int b = old[(y+dy)*WIDTH+(x+dx)] & 255;
                        err += Math.abs(a-b); n++;
                    }
                }
                if (dx == 0 && dy == 0) zero = err;
                if (err < best) { best = err; bx = dx; by = dy; samples = n; }
            }
        }
        if (bx == 0 && by == 0) return null;
        if (Math.abs(bx) == SEARCH || Math.abs(by) == SEARCH) return null;
        if (samples == 0 || (double)best / samples > 28) return null;
        if (best >= zero * 0.94) return null;
        return new Motion(bx, by);
    }
}
