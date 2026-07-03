package com.example.slagalicatim04.fragments;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.LinearInterpolator;

import java.util.Random;

public class RewardConfettiView extends View {
    private static final int PARTICLE_COUNT = 150;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Random random = new Random();
    private final Particle[] particles = new Particle[PARTICLE_COUNT];
    private float progress = 1f;
    private ValueAnimator animator;

    public RewardConfettiView(Context context) {
        super(context);
        init();
    }

    public RewardConfettiView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        setWillNotDraw(false);
        int[] colors = {0xFFFFC400, 0xFF6F4BB2, 0xFFEF5350, 0xFF26A69A, 0xFF42A5F5};
        for (int i = 0; i < particles.length; i++) {
            Particle particle = new Particle();
            particle.color = colors[i % colors.length];
            particles[i] = particle;
        }
    }

    public void burst() {
        setVisibility(VISIBLE);
        post(() -> {
            resetParticles();
            if (animator != null) {
                animator.cancel();
            }
            bringToFront();
            animator = ValueAnimator.ofFloat(0.18f, 1f);
            animator.setDuration(3200);
            animator.setInterpolator(new LinearInterpolator());
            animator.addUpdateListener(animation -> {
                progress = (float) animation.getAnimatedValue();
                invalidate();
            });
            animator.addListener(new android.animation.AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(android.animation.Animator animation) {
                    setVisibility(INVISIBLE);
                }
            });
            setVisibility(VISIBLE);
            animator.start();
        });
    }

    private void resetParticles() {
        int width = getWidth();
        int height = getHeight();
        if (width <= 1 || height <= 1) {
            width = getResources().getDisplayMetrics().widthPixels;
            height = getResources().getDisplayMetrics().heightPixels;
        }
        for (Particle particle : particles) {
            particle.startX = random.nextInt(width);
            particle.startY = -height * 0.35f + random.nextFloat() * height * 1.1f;
            particle.velocityX = -35f + random.nextFloat() * 70f;
            particle.velocityY = height * (0.52f + random.nextFloat() * 0.42f);
            particle.sway = 18f + random.nextFloat() * 42f;
            particle.phase = random.nextFloat() * (float) Math.PI * 2f;
            particle.size = 7 + random.nextInt(11);
            particle.rotation = random.nextFloat() * 180f;
        }
        progress = 0.18f;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (progress >= 1f) {
            return;
        }
        float alpha = progress < 0.82f ? 1f : Math.max(0f, (1f - progress) / 0.18f);
        for (Particle particle : particles) {
            float x = particle.startX + particle.velocityX * progress
                    + (float) Math.sin(particle.phase + progress * 10f) * particle.sway;
            float y = particle.startY + particle.velocityY * progress;
            paint.setColor(particle.color);
            paint.setAlpha((int) (255 * alpha));
            canvas.save();
            canvas.rotate(particle.rotation + 360f * progress, x, y);
            canvas.drawRect(x - particle.size / 2f, y - particle.size / 3f,
                    x + particle.size / 2f, y + particle.size / 3f, paint);
            canvas.restore();
        }
    }

    private static final class Particle {
        float startX;
        float startY;
        float velocityX;
        float velocityY;
        float sway;
        float phase;
        float size;
        float rotation;
        int color;
    }
}
