package com.example.pokerproject;

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

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.HashMap;

public class PokerGameView extends View {
    private GameRoom currentRoom;
    private String uid;

    private Paint paint;
    private Paint bgPaint;
    private Paint boardPaint;
    private Paint podPaint;

    private int screenW, screenH;
    private int cardW, cardH;
    private int smallCardW, smallCardH;

    private HashMap<String, Bitmap> cardCache;
    private Bitmap smallBackBitmap;

    public PokerGameView(Context context) {
        super(context);
        init();
    }

    public PokerGameView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public void init() {
        paint = new Paint();
        paint.setAntiAlias(true);

        cardCache = new HashMap<>();

        // רקע השולחן - יתמלא ב-Radial Gradient
        bgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

        // הלוח השקוף שעליו יונחו קלפי הקהילה
        boardPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        boardPaint.setColor(Color.parseColor("#66000000")); // שחור עם 40% שקיפות

        // "תגיות" השחקנים שעליהן יונחו הקלפים שלהם
        podPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        podPaint.setColor(Color.parseColor("#4D000000")); // שחור עם 30% שקיפות
    }

    @Override
    public void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        screenW = w;
        screenH = h;

        // מידות הקלפים
        cardH = (int)(screenH * 0.32);
        cardW = (int)(cardH * 0.72);

        smallCardH = (int)(cardH * 0.70);
        smallCardW = (int)(smallCardH * 0.72);

        // אפקט זרקור עדין ועשיר על כל המסך (קזינו מודרני)
        RadialGradient gradient = new RadialGradient(
                screenW / 2f, screenH / 2f,
                screenW * 0.8f,
                Color.parseColor("#1B4F3B"), // ירוק-כחלחל עשיר במרכז
                Color.parseColor("#0A1C15"), // שחור-ירוק עמוק בקצוות
                Shader.TileMode.CLAMP
        );
        bgPaint.setShader(gradient);

        int backResId = getResources().getIdentifier("card_back", "drawable", getContext().getPackageName());
        if (backResId != 0) {
            Bitmap originalBack = BitmapFactory.decodeResource(getResources(), backResId);
            smallBackBitmap = Bitmap.createScaledBitmap(originalBack, smallCardW, smallCardH, true);
        }
    }

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        super.onDraw(canvas);

        // 1. ציור הרקע (תופס את כל המסך)
        canvas.drawRect(0, 0, screenW, screenH, bgPaint);

        if (currentRoom == null) return;

        // ---------------------------------------------------------
        // ציור קלפי הקהילה על "לוח זכוכית"
        // ---------------------------------------------------------
        if (currentRoom.getCommunityCards() != null && !currentRoom.getCommunityCards().isEmpty()) {
            ArrayList<Card> communityCards = currentRoom.getCommunityCards();

            int space = 12; // רווח בין קלפי הקהילה
            int totalWidth = communityCards.size() * cardW + (communityCards.size() - 1) * space;
            float startX = (screenW - totalWidth) / 2f;
            float startY = (screenH - cardH) / 2f - 40;

            // ציור הרקע הכהה מאחורי הקלפים (The Board)
            RectF boardRect = new RectF(startX - 20, startY - 20, startX + totalWidth + 20, startY + cardH + 20);
            canvas.drawRoundRect(boardRect, 30, 30, boardPaint);

            // ציור הקלפים עצמם
            float currentX = startX;
            for (Card card : communityCards) {
                Bitmap bitmap = getCachedImage(card.getImageResourceName(), cardW, cardH);
                if (bitmap != null) {
                    canvas.drawBitmap(bitmap, currentX, startY, paint);
                }
                currentX += cardW + space;
            }
        }

        // ---------------------------------------------------------
        // מציאת השחקנים
        // ---------------------------------------------------------
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

        // ---------------------------------------------------------
        // ציור הקלפים שלך (בחפיפה - Overlapping)
        // ---------------------------------------------------------
        if (me != null && me.getHand() != null && !me.getHand().isEmpty()) {
            if ("Folded".equals(me.getStatus())) paint.setAlpha(100);
            else paint.setAlpha(255);

            int myHandSize = me.getHand().size();
            // שינוי הגישה: הקלפים חופפים! המרחק ביניהם הוא רק חצי מהרוחב של קלף
            float overlapOffset = cardW * 0.5f;
            float totalWidthMyHand = cardW + (myHandSize - 1) * overlapOffset;

            float myX = (screenW - totalWidthMyHand) / 2f;
            float myY = screenH - cardH - 10;

            for (Card card : me.getHand()) {
                Bitmap bitmap = getCachedImage(card.getImageResourceName(), cardW, cardH);
                if (bitmap != null) canvas.drawBitmap(bitmap, myX, myY, paint);
                myX += overlapOffset; // מתקדמים רק חצי קלף, ליצירת חפיפה
            }
            paint.setAlpha(255);
        }

        // ---------------------------------------------------------
        // ציור היריבים (קצת יותר קרוב לפינות, ועל תגית שחורה)
        // ---------------------------------------------------------
        if (!opponents.isEmpty() && smallBackBitmap != null) {
            for (int i = 0; i < opponents.size(); i++) {
                User opponent = opponents.get(i);

                if (opponent.getHand() == null || opponent.getHand().isEmpty()) continue;

                if ("Folded".equals(opponent.getStatus())) paint.setAlpha(100);
                else paint.setAlpha(255);

                int handSize = opponent.getHand().size();
                float overlapOffset = smallCardW * 0.4f; // חפיפה צפופה יותר ליריבים
                float totalW = smallCardW + (handSize - 1) * overlapOffset;

                float oppX = 0;
                float oppY = 0;

                // מיקומים נקיים באזורים מתים של המסך
                if (i == 0) {
                    oppX = 60;
                    oppY = screenH * 0.25f;
                } else if (i == 1) {
                    oppX = (screenW - totalW) / 2f;
                    oppY = 40;
                } else if (i == 2) {
                    oppX = screenW - totalW - 60;
                    oppY = screenH * 0.25f;
                }

                // ציור ה"צלחת/תגית" מתחת לקלפי היריב
                RectF podRect = new RectF(oppX - 15, oppY - 15, oppX + totalW + 15, oppY + smallCardH + 15);
                canvas.drawRoundRect(podRect, 25, 25, podPaint);

                // ציור הקלפים של היריב
                for (int k = 0; k < handSize; k++) {
                    canvas.drawBitmap(smallBackBitmap, oppX, oppY, paint);
                    oppX += overlapOffset;
                }
                paint.setAlpha(255);
            }
        }
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

    public void updateGame(GameRoom room, String uid) {
        this.currentRoom = room;
        this.uid = uid;
        invalidate();
    }
}