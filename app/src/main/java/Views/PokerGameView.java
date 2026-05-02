package Views;

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
import android.graphics.Typeface;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.DecelerateInterpolator;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.HashMap;

import Models.Card;
import Models.GameRoom;
import Models.User;

public class PokerGameView extends View {

    private GameRoom currentRoom;
    private String uid;

    private Paint tablePaint;
    private Paint borderPaint;
    private Paint cardPaint;
    private Paint textBgPaint;
    private Paint textPaint;

    // --- מכחולים לתצוגת הניצחון ---
    private Paint overlayPaint;
    private Paint winnerTextPaint;

    private int screenW, screenH;
    private int cardW, cardH;
    private int smallCardW, smallCardH;
    private RectF tableRect;

    private HashMap<String, Bitmap> cardCache;
    private Bitmap smallBackBitmap;
    private Bitmap normalBackBitmap;

    private boolean isFlipping = false;
    private float flipScale = 1f;
    private int previousCommunityCount = 0;
    private int animatingStartIndex = 0;

    private boolean isDealing = false;
    private float dealProgress = 1f;
    private boolean isNewRound = false;

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

        // --- אתחול מכחולי הניצחון ---
        overlayPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        overlayPaint.setColor(Color.parseColor("#B3000000")); // שחור עם 70% שקיפות להחשכת המסך

        winnerTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        winnerTextPaint.setColor(Color.parseColor("#FFD700")); // צבע זהב יוקרתי
        winnerTextPaint.setTextSize(100f); // טקסט ענק!
        winnerTextPaint.setTextAlign(Paint.Align.CENTER);
        winnerTextPaint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        winnerTextPaint.setShadowLayer(15f, 0f, 5f, Color.BLACK); // צל כבד כדי שהטקסט יבלוט
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

        float deckX = screenW / 2f - cardW / 2f;
        float deckY = screenH / 2f - cardH / 2f;

        if (currentRoom.getCommunityCards() != null && !currentRoom.getCommunityCards().isEmpty()) {
            ArrayList<Card> communityCards = currentRoom.getCommunityCards();
            int space = 15;
            int totalWidth = communityCards.size() * cardW + (communityCards.size() - 1) * space;
            float startX = (screenW - totalWidth) / 2f;
            float startY = (screenH - cardH) / 2f - 30;

            for (int i = 0; i < communityCards.size(); i++) {
                Card card = communityCards.get(i);
                Bitmap frontBitmap = getCachedImage(card.getImageResourceName(), cardW, cardH);

                if (isFlipping && i >= animatingStartIndex) {
                    canvas.save();
                    float centerX = startX + cardW / 2f;
                    float centerY = startY + cardH / 2f;
                    float currentScaleX = Math.abs(flipScale);
                    canvas.scale(currentScaleX, 1f, centerX, centerY);

                    if (flipScale > 0) {
                        if (normalBackBitmap != null) canvas.drawBitmap(normalBackBitmap, startX, startY, cardPaint);
                    } else {
                        if (frontBitmap != null) canvas.drawBitmap(frontBitmap, startX, startY, cardPaint);
                    }
                    canvas.restore();
                } else {
                    if (frontBitmap != null) canvas.drawBitmap(frontBitmap, startX, startY, cardPaint);
                }
                startX += cardW + space;
            }
        }

        User me = null;
        ArrayList<User> opponents = new ArrayList<>();
        if (currentRoom.getPlayers() != null) {
            for (User player : currentRoom.getPlayers()) {
                if (player.getUid() != null && player.getUid().equals(uid)) me = player;
                else opponents.add(player);
            }
        }

        if (me != null && me.getHand() != null && !me.getHand().isEmpty()) {
            if ("Folded".equals(me.getStatus())) cardPaint.setAlpha(100);
            else cardPaint.setAlpha(255);

            int myHandSize = me.getHand().size();
            float overlap = cardW * 0.5f;
            float totalWidth = cardW + (myHandSize - 1) * overlap;
            float targetX = (screenW - totalWidth) / 2f;
            float targetY = screenH - cardH - 20;

            for (Card card : me.getHand()) {
                Bitmap bitmap = getCachedImage(card.getImageResourceName(), cardW, cardH);

                float currentX = deckX + (targetX - deckX) * dealProgress;
                float currentY = deckY + (targetY - deckY) * dealProgress;

                if (bitmap != null) canvas.drawBitmap(bitmap, currentX, currentY, cardPaint);
                targetX += overlap;
            }
            cardPaint.setAlpha(255);
            drawPlayerLabel(canvas, me.getNickname(), me.getChips(), screenW / 2f, targetY - 10);
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
                float targetX = 0, targetY = 0;

                if (i == 0) {
                    targetX = 60; targetY = (screenH - smallCardH) / 2f - 30;
                } else if (i == 1) {
                    targetX = (screenW - totalW) / 2f; targetY = 40;
                } else if (i == 2) {
                    targetX = screenW - totalW - 60; targetY = (screenH - smallCardH) / 2f - 30;
                }

                float tempTargetX = targetX;
                for (int k = 0; k < handSize; k++) {
                    float currentX = deckX + (tempTargetX - deckX) * dealProgress;
                    float currentY = deckY + (targetY - deckY) * dealProgress;

                    canvas.drawBitmap(smallBackBitmap, currentX, currentY, cardPaint);
                    tempTargetX += overlap;
                }
                cardPaint.setAlpha(255);
                drawPlayerLabel(canvas, opponent.getNickname(), opponent.getChips(), targetX + totalW / 2f, targetY + smallCardH + 40);
            }
        }

        // ---------------------------------------------------------
        // ציור תצוגת הניצחון מעל הכל!
        // ---------------------------------------------------------
        if ("Showdown".equalsIgnoreCase(currentRoom.getGameState()) && currentRoom.getWinnerName() != null && !currentRoom.getWinnerName().isEmpty()) {
            // ציור רקע שקוף שיחשיך את כל השולחן והקלפים
            canvas.drawRect(0, 0, screenW, screenH, overlayPaint);

            // כתיבת הטקסט הענק של המנצח בדיוק באמצע המסך
            canvas.drawText(currentRoom.getWinnerName(), screenW / 2f, screenH / 2f + 30, winnerTextPaint);
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
        if (cardCache.containsKey(key)) return cardCache.get(key);

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

        int currentCommunityCount = (room.getCommunityCards() != null) ? room.getCommunityCards().size() : 0;

        if (room.getGameState().equalsIgnoreCase("PreFlop") && !isNewRound) {
            isNewRound = true;
            startDealAnimation();
        }
        else if (!room.getGameState().equalsIgnoreCase("PreFlop")) {
            isNewRound = false;
        }

        if (currentCommunityCount > previousCommunityCount) {
            animatingStartIndex = previousCommunityCount;
            startFlipAnimation();
        } else if (currentCommunityCount == 0) {
            previousCommunityCount = 0;
            invalidate();
        } else {
            invalidate();
        }

        previousCommunityCount = currentCommunityCount;
    }

    private void startDealAnimation() {
        if (isDealing) return;
        isDealing = true;
        dealProgress = 0f;

        ValueAnimator animator = ValueAnimator.ofFloat(0f, 1f);
        animator.setDuration(500);
        animator.setInterpolator(new DecelerateInterpolator());

        animator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator animation) {
                dealProgress = (float) animation.getAnimatedValue();
                invalidate();
            }
        });

        animator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                isDealing = false;
                dealProgress = 1f;
                invalidate();
            }
        });

        animator.start();
    }

    private void startFlipAnimation() {
        if (isFlipping) return;
        isFlipping = true;

        ValueAnimator animator = ValueAnimator.ofFloat(1f, -1f);
        animator.setDuration(500);

        animator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator animation) {
                flipScale = (float) animation.getAnimatedValue();
                invalidate();
            }
        });

        animator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                isFlipping = false;
                flipScale = -1f;
                invalidate();
            }
        });

        animator.start();
    }
}