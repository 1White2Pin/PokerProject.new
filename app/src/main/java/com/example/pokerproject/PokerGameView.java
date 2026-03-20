package com.example.pokerproject;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.HashMap;

public class PokerGameView extends View {

    private GameRoom currentRoom;
    private String uid;

    private Paint tablePaint;
    private Paint borderPaint;
    private Paint cardPaint;
    private Paint textBgPaint;
    private Paint textPaint;

    private int screenW, screenH;
    private int cardW, cardH;
    private int smallCardW, smallCardH;
    private RectF tableRect;

    private HashMap<String, Bitmap> cardCache;
    private Bitmap smallBackBitmap;
    private Bitmap normalBackBitmap; // נוסף: גב קלף בגודל רגיל בשביל האנימציה

    // --- משתני אנימציית ההיפוך (Flip Animation) ---
    private boolean isFlipping = false;
    private float flipScale = 1f; // נע בין 1 (גב) למינוס 1 (פנים)
    private int previousCommunityCount = 0; // זוכר כמה קלפים היו כדי לדעת את מי לסובב
    private int animatingStartIndex = 0; // מאיזה אינדקס להתחיל את האנימציה

    public PokerGameView(Context context) {
        super(context);
        init();
    }

    public PokerGameView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public void init() {
        cardCache = new HashMap<>();

        tablePaint = new Paint(Paint.ANTI_ALIAS_FLAG);

        borderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        borderPaint.setColor(Color.parseColor("#5C3A21"));
        borderPaint.setStyle(Paint.Style.STROKE);
        borderPaint.setStrokeWidth(30f);

        cardPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

        textBgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        textBgPaint.setColor(Color.parseColor("#99000000"));

        textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        textPaint.setColor(Color.WHITE);
        textPaint.setTextAlign(Paint.Align.CENTER);
        textPaint.setTextSize(35f);
    }

    @Override
    public void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        screenW = w;
        screenH = h;

        cardH = (int)(screenH * 0.32);
        cardW = (int)(cardH * 0.72);
        smallCardH = (int)(cardH * 0.70);
        smallCardW = (int)(smallCardH * 0.72);

        tableRect = new RectF(50, 50, screenW - 50, screenH - 50);

        RadialGradient gradient = new RadialGradient(
                screenW / 2f, screenH / 2f,
                screenW / 1.5f,
                Color.parseColor("#2E7D32"),
                Color.parseColor("#124015"),
                Shader.TileMode.CLAMP
        );
        tablePaint.setShader(gradient);

        // טעינת גב הקלף (מוקטן ליריבים ורגיל לאנימציה באמצע)
        int backResId = getResources().getIdentifier("card_back", "drawable", getContext().getPackageName());
        if (backResId != 0) {
            Bitmap originalBack = BitmapFactory.decodeResource(getResources(), backResId);
            smallBackBitmap = Bitmap.createScaledBitmap(originalBack, smallCardW, smallCardH, true);
            normalBackBitmap = Bitmap.createScaledBitmap(originalBack, cardW, cardH, true);
        }
    }

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        super.onDraw(canvas);

        canvas.drawColor(Color.BLACK);

        if (tableRect != null) {
            canvas.drawOval(tableRect, tablePaint);
            canvas.drawOval(tableRect, borderPaint);
        }

        if (currentRoom == null) return;

        // ---------------------------------------------------------
        // 3. קלפי הקהילה + אנימציית ההיפוך!
        // ---------------------------------------------------------
        if (currentRoom.getCommunityCards() != null && !currentRoom.getCommunityCards().isEmpty()) {
            ArrayList<Card> communityCards = currentRoom.getCommunityCards();
            int space = 15;
            int totalWidth = communityCards.size() * cardW + (communityCards.size() - 1) * space;
            float startX = (screenW - totalWidth) / 2f;
            float startY = (screenH - cardH) / 2f - 30;

            for (int i = 0; i < communityCards.size(); i++) {
                Card card = communityCards.get(i);
                Bitmap frontBitmap = getCachedImage(card.getImageResourceName(), cardW, cardH);

                // האם הקלף הזה נמצא עכשיו באנימציה?
                if (isFlipping && i >= animatingStartIndex) {
                    canvas.save(); // שומרים את מצב המסך כדי לא לסובב את כל השולחן בטעות

                    // מחשבים את מרכז הקלף שעליו הוא יסתובב
                    float centerX = startX + cardW / 2f;
                    float centerY = startY + cardH / 2f;

                    // מכווצים את הקלף ב-X לפי מצב האנימציה (ערך מוחלט כדי שיהיה חיובי תמיד)
                    float currentScaleX = Math.abs(flipScale);
                    canvas.scale(currentScaleX, 1f, centerX, centerY);

                    if (flipScale > 0) {
                        // חלק ראשון של האנימציה (מ-1 עד 0): מציירים את גב הקלף!
                        if (normalBackBitmap != null) {
                            canvas.drawBitmap(normalBackBitmap, startX, startY, cardPaint);
                        }
                    } else {
                        // חלק שני של האנימציה (מ-0 עד מינוס 1): מציירים את הקלף האמיתי!
                        if (frontBitmap != null) {
                            canvas.drawBitmap(frontBitmap, startX, startY, cardPaint);
                        }
                    }
                    canvas.restore(); // מחזירים את המסך למצב רגיל לקלף הבא
                } else {
                    // קלף שכבר פתוח (לא באנימציה) מצויר רגיל
                    if (frontBitmap != null) {
                        canvas.drawBitmap(frontBitmap, startX, startY, cardPaint);
                    }
                }

                startX += cardW + space;
            }
        }

        // --- שאר הציור כרגיל ---
        User me = null;
        ArrayList<User> opponents = new ArrayList<>();
        if (currentRoom.getPlayers() != null) {
            for (User player : currentRoom.getPlayers()) {
                if (player.getUid() != null && player.getUid().equals(uid)) {
                    me = player;
                } else {
                    opponents.add(player);
                }
            }
        }

        if (me != null && me.getHand() != null && !me.getHand().isEmpty()) {
            if ("Folded".equals(me.getStatus())) cardPaint.setAlpha(100);
            else cardPaint.setAlpha(255);

            int myHandSize = me.getHand().size();
            float overlap = cardW * 0.5f;
            float totalWidth = cardW + (myHandSize - 1) * overlap;
            float myX = (screenW - totalWidth) / 2f;
            float myY = screenH - cardH - 20;

            for (Card card : me.getHand()) {
                Bitmap bitmap = getCachedImage(card.getImageResourceName(), cardW, cardH);
                if (bitmap != null) canvas.drawBitmap(bitmap, myX, myY, cardPaint);
                myX += overlap;
            }
            cardPaint.setAlpha(255);
            drawPlayerLabel(canvas, me.getNickname(), me.getChips(), screenW / 2f, myY - 10);
        }

        if (!opponents.isEmpty() && smallBackBitmap != null) {
            for (int i = 0; i < opponents.size(); i++) {
                User opponent = opponents.get(i);
                if (opponent.getHand() == null || opponent.getHand().isEmpty()) continue;

                if ("Folded".equals(opponent.getStatus())) cardPaint.setAlpha(100);
                else cardPaint.setAlpha(255);

                int handSize = opponent.getHand().size();
                float overlap = smallCardW * 0.4f;
                float totalW = smallCardW + (handSize - 1) * overlap;
                float oppX = 0, oppY = 0;

                if (i == 0) {
                    oppX = 60;
                    oppY = (screenH - smallCardH) / 2f - 30;
                } else if (i == 1) {
                    oppX = (screenW - totalW) / 2f;
                    oppY = 40;
                } else if (i == 2) {
                    oppX = screenW - totalW - 60;
                    oppY = (screenH - smallCardH) / 2f - 30;
                }

                float currentX = oppX;
                for (int k = 0; k < handSize; k++) {
                    canvas.drawBitmap(smallBackBitmap, currentX, oppY, cardPaint);
                    currentX += overlap;
                }
                cardPaint.setAlpha(255);
                drawPlayerLabel(canvas, opponent.getNickname(), opponent.getChips(), oppX + totalW / 2f, oppY + smallCardH + 40);
            }
        }
    }

    private void drawPlayerLabel(Canvas canvas, String name, int chips, float centerX, float bottomY) {
        String text = (name != null ? name : "Player") + " | ₪" + chips;
        RectF bgRect = new RectF(centerX - 120, bottomY - 45, centerX + 120, bottomY + 15);
        canvas.drawRoundRect(bgRect, 15, 15, textBgPaint);
        canvas.drawText(text, centerX, bottomY, textPaint);
    }

    private Bitmap getCachedImage(String cardName, int reqWidth, int reqHeight) {
        String key = cardName + "_" + reqWidth;
        if (cardCache.containsKey(key)) {
            return cardCache.get(key);
        }
        int resID = getResources().getIdentifier(cardName, "drawable", getContext().getPackageName());
        if (resID != 0) {
            Bitmap originalBitmap = BitmapFactory.decodeResource(getResources(), resID);
            Bitmap scaledBitmap = Bitmap.createScaledBitmap(originalBitmap, reqWidth, reqHeight, true);
            cardCache.put(key, scaledBitmap);
            return scaledBitmap;
        }
        return null;
    }

    // --- מערכת עדכון המשחק החדשה שתופסת מתי צריך לעשות אנימציה ---
    public void updateGame(GameRoom room, String uid) {
        this.currentRoom = room;
        this.uid = uid;

        int currentCommunityCount = (room.getCommunityCards() != null) ? room.getCommunityCards().size() : 0;

        // אם יש קלפים חדשים על השולחן (למשל, עברנו ל-Flop)
        if (currentCommunityCount > previousCommunityCount) {
            animatingStartIndex = previousCommunityCount; // נסובב רק את הקלפים החדשים
            startFlipAnimation();
        }
        // אם המשחק התאפס (הקופה התחלקו והתחיל סיבוב חדש)
        else if (currentCommunityCount == 0) {
            previousCommunityCount = 0;
            invalidate();
        } else {
            invalidate(); // סתם עדכון רגיל בלי אנימציה
        }

        previousCommunityCount = currentCommunityCount;
    }

    // --- הפונקציה שמפעילה את הקסם של האנימציה ---
    private void startFlipAnimation() {
        if (isFlipping) return;
        isFlipping = true;

        // אנימציה שיורדת מ-1 (גב הקלף מלא) למינוס 1 (פני הקלף מלאים)
        ValueAnimator animator = ValueAnimator.ofFloat(1f, -1f);
        animator.setDuration(600); // 600 מילישניות (חצי שניה בערך)
        animator.setInterpolator(new AccelerateDecelerateInterpolator()); // מתחיל לאט, מאיץ, ומאט בסוף

        animator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator animation) {
                flipScale = (float) animation.getAnimatedValue();
                invalidate(); // אומר ל-Canvas לצייר מחדש כל פריים!
            }
        });

        animator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                isFlipping = false;
                flipScale = -1f; // מוודא שבסוף רואים את הקלף פתוח לחלוטין
                invalidate();
            }
        });

        animator.start();
    }
}