package com.anime.lofiatc;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.view.View;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

/** Night sky: twinkling stars, and now and then a plane crossing with a blinking light. ~20 frames a second. */
class SkyView extends View {
    private final Random random = new Random();
    private final Paint bg = new Paint(), glow = new Paint(), star = new Paint(Paint.ANTI_ALIAS_FLAG),
            red = new Paint(Paint.ANTI_ALIAS_FLAG);
    private float[] sx, sy, sr, sp;
    private final List<float[]> planes = new ArrayList<>(); // x, y, speed, frame

    SkyView(Context c) {
        super(c);
        star.setColor(0xFFFFFFFF);
        red.setColor(0xFFFF5C5C);
    }

    @Override
    protected void onSizeChanged(int w, int h, int ow, int oh) {
        bg.setShader(new LinearGradient(0, 0, 0, h, 0xFF0B1020, 0xFF1A1433, Shader.TileMode.CLAMP));
        glow.setShader(new RadialGradient(w / 2f, h * 1.2f, h * 0.9f, 0x993A2350, 0x003A2350, Shader.TileMode.CLAMP));
        int n = w * h / 9000;
        sx = new float[n]; sy = new float[n]; sr = new float[n]; sp = new float[n];
        for (int i = 0; i < n; i++) {
            sx[i] = random.nextFloat() * w;
            sy[i] = random.nextFloat() * h * 0.85f;
            sr[i] = 0.6f + random.nextFloat() * 1.8f;
            sp[i] = random.nextFloat() * 6.3f;
        }
    }

    @Override
    protected void onDraw(Canvas c) {
        int w = getWidth(), h = getHeight();
        c.drawRect(0, 0, w, h, bg);
        c.drawRect(0, 0, w, h, glow);
        if (sx == null) return;
        long t = System.currentTimeMillis();
        for (int i = 0; i < sx.length; i++) {
            star.setAlpha((int) (255 * (0.35 + 0.35 * Math.sin(t / 1400.0 + sp[i]))));
            c.drawCircle(sx[i], sy[i], sr[i], star);
        }
        if (planes.size() < 2 && random.nextFloat() < 0.004f)
            planes.add(new float[]{-20, h * (0.08f + random.nextFloat() * 0.3f), 1.2f + random.nextFloat() * 1.2f, 0});
        star.setAlpha(230);
        for (Iterator<float[]> it = planes.iterator(); it.hasNext(); ) {
            float[] p = it.next();
            p[0] += p[2];
            p[3]++;
            if (p[0] > w + 20) { it.remove(); continue; }
            c.drawRect(p[0], p[1], p[0] + 3, p[1] + 3, star);
            if (p[3] % 24 < 3) c.drawCircle(p[0], p[1], 4, red);
        }
        postInvalidateDelayed(50);
    }
}
