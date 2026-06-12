package Views;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.DecelerateInterpolator;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.HashMap;

import Models.Card;
import Models.GameRoom;
import Models.User;

// קלאס התצוגה הראשי של השולחן - אחראי לצייר את כל רכיבי המשחק באנימציות
public class PokerGameView extends View {

    private GameRoom currentRoom;
    private String uid;

    // --- מכחולים (Paints) ---
    private Paint tablePaint, tableInnerPaint, borderPaint, borderInnerPaint, feltLinePaint;
    private Paint cardPaint, shadowPaint;
    private Paint labelBgPaint, labelBorderPaint, highlightBorderPaint, namePaint, chipsPaint;
    private Paint avatarBgPaint, avatarBorderPaint, avatarTextPaint;
    private Paint potBgPaint, potTextPaint, potLabelPaint, potBorderPaint;
    private Paint overlayPaint, winnerPanelPaint, winnerTitlePaint, winnerNamePaint, winnerSubPaint;
    private Paint betBgPaint, betTextPaint;

    // --- מידות וגבולות ---
    private int screenW, screenH;
    private float centerX, centerY;
    private int cardW, cardH, smallCardW, smallCardH;
    private RectF tableRect, tableInnerRect;

    // --- זיכרון מטמון לתמונות ---
    private HashMap<String, Bitmap> cardCache;
    private Bitmap smallBackBitmap, normalBackBitmap;

    // --- משתני אנימציה ---
    private boolean isFlipping = false, isDealing = false, isNewRound = false;
    private float flipScale = 1f, dealProgress = 1f;
    private int previousCommunityCount = 0, animatingStartIndex = 0;

    // 🌟 המשתנים החדשים לצורך האנימציה של קלפי היריבים 🌟
    private boolean isOpponentFlipping = false;   // האם היריבים כרגע באמצע סיבוב קלפים
    private float opponentFlipScale = 1f;         // ערך הגלילה של קלפי היריבים (מ-1 ל-1-)
    private String previousGameState = "";         // שומר את הסטטוס הקודם של החדר כדי לזהות כניסה ל-Showdown

    // --- צבעים עיקריים לשולחן ---
    private static final String COLOR_FELT_DARK   = "#0D3B1F";
    private static final String COLOR_FELT_LIGHT  = "#1E7A3D";
    private static final String COLOR_GOLD        = "#D4A843";
    private static final String COLOR_CHIP_BG     = "#1A2E1A";

    public PokerGameView(Context context) {
        super(context);
        init();
    }

    public PokerGameView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    // אתחול והגדרת כל המכחולים של המשחק (רץ פעם אחת בלבד בטעינה)
    public void init() {
        cardCache = new HashMap<>();

        // שולחן וצללים
        tablePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        tableInnerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        borderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        borderPaint.setStyle(Paint.Style.STROKE);
        borderPaint.setStrokeWidth(40f);

        borderInnerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        borderInnerPaint.setStyle(Paint.Style.STROKE);
        borderInnerPaint.setStrokeWidth(3f);
        borderInnerPaint.setColor(Color.parseColor(COLOR_GOLD));

        feltLinePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        feltLinePaint.setStyle(Paint.Style.STROKE);
        feltLinePaint.setColor(Color.parseColor(COLOR_FELT_LIGHT));
        feltLinePaint.setAlpha(40);

        cardPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        shadowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        shadowPaint.setColor(Color.parseColor("#55000000"));

        // תוויות ואווטרים של שחקנים
        labelBgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        labelBgPaint.setColor(Color.parseColor("#CC0A0A0A"));
        labelBorderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        labelBorderPaint.setStyle(Paint.Style.STROKE);
        labelBorderPaint.setColor(Color.parseColor("#55D4A843"));

        highlightBorderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        highlightBorderPaint.setStyle(Paint.Style.STROKE);
        highlightBorderPaint.setColor(Color.parseColor(COLOR_GOLD));
        highlightBorderPaint.setStrokeWidth(2f);

        namePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        namePaint.setColor(Color.WHITE);
        namePaint.setTextAlign(Paint.Align.CENTER);
        namePaint.setTextSize(24f);

        chipsPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        chipsPaint.setColor(Color.parseColor("#F0C96A"));
        chipsPaint.setTextAlign(Paint.Align.CENTER);
        chipsPaint.setTextSize(20f);

        avatarBgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        avatarBorderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        avatarBorderPaint.setStyle(Paint.Style.STROKE);
        avatarBorderPaint.setStrokeWidth(2.5f);

        avatarTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        avatarTextPaint.setColor(Color.WHITE);
        avatarTextPaint.setTextAlign(Paint.Align.CENTER);
        avatarTextPaint.setFakeBoldText(true);

        // קופה מרכזית (Pot)
        potBgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        potBgPaint.setColor(Color.parseColor(COLOR_CHIP_BG));
        potBorderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        potBorderPaint.setStyle(Paint.Style.STROKE);
        potBorderPaint.setColor(Color.parseColor(COLOR_GOLD));
        potTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        potTextPaint.setColor(Color.parseColor("#F0C96A"));
        potTextPaint.setTextAlign(Paint.Align.CENTER);
        potTextPaint.setTextSize(30f);
        potLabelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        potLabelPaint.setColor(Color.parseColor("#AAFFFFFF"));
        potLabelPaint.setTextAlign(Paint.Align.CENTER);
        potLabelPaint.setTextSize(20f);

        // בועות הימור על השולחן (Bet Chips)
        betBgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        betBgPaint.setColor(Color.parseColor(COLOR_CHIP_BG));
        betBgPaint.setStyle(Paint.Style.FILL);

        betTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        betTextPaint.setColor(Color.parseColor("#F0C96A"));
        betTextPaint.setTextAlign(Paint.Align.CENTER);
        betTextPaint.setTextSize(22f);
        betTextPaint.setFakeBoldText(true);

        // מסך ומודל ניצחון
        overlayPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        overlayPaint.setColor(Color.parseColor("#CC000000"));
        winnerPanelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        winnerTitlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        winnerTitlePaint.setColor(Color.parseColor(COLOR_GOLD));
        winnerTitlePaint.setTextSize(38f);
        winnerTitlePaint.setTextAlign(Paint.Align.CENTER);
        winnerNamePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        winnerNamePaint.setColor(Color.WHITE);
        winnerNamePaint.setTextSize(70f);
        winnerNamePaint.setTextAlign(Paint.Align.CENTER);
        winnerSubPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        winnerSubPaint.setColor(Color.parseColor("#AAFFFFFF"));
        winnerSubPaint.setTextSize(26f);
        winnerSubPaint.setTextAlign(Paint.Align.CENTER);
    }

    // חישוב מידות דינמיות בהתאם לגודל המסך של המכשיר
    @Override
    public void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        screenW = w; screenH = h;
        centerX = w / 2f; centerY = h / 2f;

        cardH = (int) (screenH * 0.28); cardW = (int) (cardH * 0.72);
        smallCardH = (int) (cardH * 0.65); smallCardW = (int) (smallCardH * 0.72);

        int marginH = (int) (screenH * 0.05); int marginW = (int) (screenW * 0.04);
        tableRect = new RectF(marginW, marginH, screenW - marginW, screenH - marginH);
        tableInnerRect = new RectF(tableRect.left + 18f, tableRect.top + 18f, tableRect.right - 18f, tableRect.bottom - 18f);

        // יצירת מעברי הצבע (Gradients) לשולחן ולעץ
        tablePaint.setShader(new RadialGradient(centerX, centerY, screenW * 0.65f,
                new int[]{Color.parseColor(COLOR_FELT_LIGHT), Color.parseColor(COLOR_FELT_DARK)},
                new float[]{0f, 1f}, Shader.TileMode.CLAMP));

        borderPaint.setShader(new LinearGradient(tableRect.left, tableRect.top, tableRect.right, tableRect.bottom,
                new int[]{Color.parseColor("#8B5A2B"), Color.parseColor("#3B2005")},
                new float[]{0f, 1f}, Shader.TileMode.CLAMP));

        int backResId = getResources().getIdentifier("card_back", "drawable", getContext().getPackageName());
        if (backResId != 0) {
            Bitmap originalBack = BitmapFactory.decodeResource(getResources(), backResId);
            smallBackBitmap = Bitmap.createScaledBitmap(originalBack, smallCardW, smallCardH, true);
            normalBackBitmap = Bitmap.createScaledBitmap(originalBack, cardW, cardH, true);
        }
    }

    // הפעלת פונקציות הציור המרכזיות לפי הסדר
    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        super.onDraw(canvas);
        canvas.drawColor(Color.parseColor("#0A0A0A"));

        drawTable(canvas);

        if (currentRoom == null) return;

        drawCommunityCards(canvas);
        drawPot(canvas);
        drawAllPlayers(canvas);
        drawShowdown(canvas);
    }

    // מצייר את אליפסות השולחן וקווי המרקם של הלבד
    private void drawTable(Canvas canvas) {
        if (tableRect == null) return;
        canvas.drawOval(tableRect, borderPaint);
        canvas.drawOval(tableInnerRect, tablePaint);
        canvas.drawOval(tableInnerRect, borderInnerPaint);

        canvas.save();
        Path ovalPath = new Path();
        ovalPath.addOval(tableInnerRect, Path.Direction.CW);
        canvas.clipPath(ovalPath);
        for (int r = 80; r < screenW; r += 120) canvas.drawCircle(centerX, centerY, r, feltLinePaint);
        canvas.restore();
    }

    // מצייר את קלפי הקהילה במרכז עם האנימציה שלהם
    private void drawCommunityCards(Canvas canvas) {
        if (currentRoom.getCommunityCards() == null || currentRoom.getCommunityCards().isEmpty()) return;

        ArrayList<Card> cards = currentRoom.getCommunityCards();
        float totalWidth = cards.size() * cardW + (cards.size() - 1) * 12f;
        float startX = (screenW - totalWidth) / 2f;
        float startY = centerY - cardH / 2f - 50;

        for (int i = 0; i < cards.size(); i++) {
            Bitmap frontBmp = getCachedImage(cards.get(i).getImageResourceName(), cardW, cardH);
            drawCardShadow(canvas, startX, startY, cardW, cardH);

            if (isFlipping && i >= animatingStartIndex) {
                canvas.save();
                canvas.scale(Math.abs(flipScale), 1f, startX + cardW / 2f, startY + cardH / 2f);
                canvas.drawBitmap((flipScale > 0) ? normalBackBitmap : frontBmp, startX, startY, cardPaint);
                canvas.restore();
            } else if (frontBmp != null) {
                canvas.drawBitmap(frontBmp, startX, startY, cardPaint);
            }
            startX += cardW + 12f;
        }
    }

    // מצייר את פאנל הקופה הכללית
    private void drawPot(Canvas canvas) {
        if (currentRoom.getPot() <= 0) return;
        float cy = centerY + cardH / 2f - 20;
        RectF potRect = new RectF(centerX - 100f, cy, centerX + 100f, cy + 55f);

        canvas.drawRoundRect(potRect, 28f, 28f, potBgPaint);
        canvas.drawRoundRect(potRect, 28f, 28f, potBorderPaint);
        canvas.drawText("POT", centerX, cy + 18f, potLabelPaint);
        canvas.drawText("₪" + currentRoom.getPot(), centerX, cy + 44f, potTextPaint);
    }

    // מצייר בועת צ'יפים של הימור נוכחי של שחקן ספציפי
    private void drawBetBubble(Canvas canvas, int amount, float cx, float cy) {
        if (amount <= 0) return;

        String text = "₪" + amount;
        float textWidth = betTextPaint.measureText(text);
        float bgWidth = textWidth + 36f;
        float bgHeight = 40f;

        RectF bgRect = new RectF(cx - bgWidth/2, cy - bgHeight/2, cx + bgWidth/2, cy + bgHeight/2);

        canvas.drawRoundRect(bgRect, 20f, 20f, betBgPaint);
        canvas.drawRoundRect(bgRect, 20f, 20f, potBorderPaint);
        canvas.drawText(text, cx, cy + 8f, betTextPaint);
    }

    // הפונקציה המרכזית שמפזרת את השחקנים והקלפים שלהם סביב השולחן
    private void drawAllPlayers(Canvas canvas) {
        float deckX = centerX - cardW / 2f;
        float deckY = centerY - cardH / 2f;
        int opponentIndex = 0;

        boolean isShowdown = "Showdown".equalsIgnoreCase(currentRoom.getGameState());

        for (User player : currentRoom.getPlayers()) {
            boolean isMe = player.getUid().equals(uid);
            boolean isFolded = "Folded".equals(player.getStatus());
            cardPaint.setAlpha(isFolded ? 90 : 255);

            if (isMe) {
                // --- ציור קלפי המשתמש המקומי (בתחתית המסך) ---
                int handSize = (player.getHand() != null) ? player.getHand().size() : 0;
                float overlap = cardW * 0.45f;
                float startX = (screenW - (cardW + (handSize - 1) * overlap)) / 2f;
                float targetY = screenH - cardH - 30;

                for (int k = 0; k < handSize; k++) {
                    Bitmap bmp = getCachedImage(player.getHand().get(k).getImageResourceName(), cardW, cardH);
                    float curX = deckX + (startX - deckX) * dealProgress;
                    float curY = deckY + (targetY - deckY) * dealProgress;
                    drawCardShadow(canvas, curX, curY, cardW, cardH);
                    if (bmp != null) canvas.drawBitmap(bmp, curX, curY, cardPaint);
                    startX += overlap;
                }

                drawPlayerLabel(canvas, player, centerX, targetY - 10, true);

                // ציור בועת הימור מעל הקלפים שלי (רק אם ההימור פעיל וזה לא שלב חשיפת קלפים)
                if (player.getCurrentBet() > 0 && !isShowdown) {
                    drawBetBubble(canvas, player.getCurrentBet(), centerX, targetY - 40f);
                }

            } else {
                // --- ציור קלפי היריבים (שמאל, למעלה, ימין) ---
                int handSize = (player.getHand() != null) ? player.getHand().size() : 0;
                float overlap = smallCardW * 0.4f;
                float totalW = smallCardW + (handSize - 1) * overlap;
                float targetX = 0, targetY = 0;
                float betCX = 0, betCY = 0;

                // חישוב מיקום חכם של היריב ושל הצ'יפים שלו לכיוון מרכז השולחן
                switch (opponentIndex) {
                    case 0: // שחקן שמאלי
                        targetX = screenW * 0.07f;
                        targetY = centerY - smallCardH * 0.8f;
                        betCX = targetX + totalW + 50f;
                        betCY = targetY + smallCardH / 2f;
                        break;
                    case 1: // שחקן עליון
                        targetX = centerX - totalW / 2f;
                        targetY = screenH * 0.10f;
                        betCX = centerX;
                        betCY = targetY + smallCardH + 80f;
                        break;
                    case 2: // שחקן ימני
                        targetX = screenW * 0.93f - totalW;
                        targetY = centerY - smallCardH * 0.8f;
                        betCX = targetX - 50f;
                        betCY = targetY + smallCardH / 2f;
                        break;
                }

                float tempX = targetX;
                for (int k = 0; k < handSize; k++) {
                    float curX = deckX + (tempX - deckX) * dealProgress;
                    float curY = deckY + (targetY - deckY) * dealProgress;
                    drawCardShadow(canvas, curX, curY, smallCardW, smallCardH);

                    // 🌟 ניהול פתיחת קלפים ואנימציה ב-Showdown 🌟
                    if (isShowdown && !isFolded) {
                        Bitmap bmp = getCachedImage(player.getHand().get(k).getImageResourceName(), smallCardW, smallCardH);

                        if (isOpponentFlipping) {
                            // המצב החדש: אם האנימציה רצה, נכווץ את הקלף ונשנה אותו לפי ערך האנימטור
                            canvas.save();
                            canvas.scale(Math.abs(opponentFlipScale), 1f, curX + smallCardW / 2f, curY + smallCardH / 2f);
                            // אם הציון חיובי מראים את הגב, אם שלילי מראים את הפנים
                            canvas.drawBitmap((opponentFlipScale > 0) ? smallBackBitmap : bmp, curX, curY, cardPaint);
                            canvas.restore();
                        } else {
                            // האנימציה הסתיימה - מציגים את קלף הפנים קבוע
                            if (bmp != null) canvas.drawBitmap(bmp, curX, curY, cardPaint);
                        }
                    } else {
                        // מצב רגיל (PreFlop, Flop וכו') - קלפי היריבים תמיד סגורים
                        if (smallBackBitmap != null) canvas.drawBitmap(smallBackBitmap, curX, curY, cardPaint);
                    }
                    tempX += overlap;
                }

                drawPlayerLabel(canvas, player, targetX + totalW / 2f, targetY + smallCardH + 8, false);

                if (player.getCurrentBet() > 0 && !isShowdown) {
                    drawBetBubble(canvas, player.getCurrentBet(), betCX, betCY);
                }

                opponentIndex++;
            }
        }
        cardPaint.setAlpha(255);
    }

    // מצייר את פאנל השם, היתרה והאווטר של השחקן
    private void drawPlayerLabel(Canvas canvas, User user, float cx, float topY, boolean isMe) {
        String name = (user.getNickname() != null) ? user.getNickname() : "Player";
        if (name.length() > 10) name = name.substring(0, 9) + "…";
        boolean folded = "Folded".equals(user.getStatus());

        RectF panelRect = new RectF(cx - 100f, topY, cx + 100f, topY + 56f);
        canvas.drawRoundRect(panelRect, 30f, 30f, labelBgPaint);
        canvas.drawRoundRect(panelRect, 30f, 30f, isMe ? highlightBorderPaint : labelBorderPaint);

        float avatarCX = panelRect.left + 30f;
        avatarBgPaint.setColor(isMe ? Color.parseColor("#2E5C2E") : Color.parseColor("#1A3A4A"));
        avatarBorderPaint.setColor(isMe ? Color.parseColor(COLOR_GOLD) : Color.parseColor(folded ? "#555555" : "#4488CC"));

        canvas.drawCircle(avatarCX + 2f, panelRect.centerY() + 3f, 22f, shadowPaint);
        canvas.drawCircle(avatarCX, panelRect.centerY(), 22f, avatarBgPaint);
        canvas.drawCircle(avatarCX, panelRect.centerY(), 22f, avatarBorderPaint);
        canvas.drawText(name.substring(0,1).toUpperCase(), avatarCX, panelRect.centerY() + 8f, avatarTextPaint);

        namePaint.setAlpha(folded ? 140 : 255);
        canvas.drawText(name, cx + 10f, topY + 26f, namePaint);
        canvas.drawText("₪" + user.getChips(), cx + 10f, topY + 46f, chipsPaint);

        if (folded) {
            namePaint.setAlpha(150);
            canvas.drawText("FOLDED", cx, topY + 75f, namePaint);
        }
    }

    // מצייר את פאנל מסך הסיום החצי-שקוף עם רווח והפסד אישיים
    private void drawShowdown(Canvas canvas) {
        if (!"Showdown".equalsIgnoreCase(currentRoom.getGameState()) || currentRoom.getWinnerName() == null) return;

        canvas.drawRect(0, 0, screenW, screenH, overlayPaint);
        RectF panel = new RectF(centerX - 250f, centerY - 130f, centerX + 250f, centerY + 130f);

        winnerPanelPaint.setColor(Color.parseColor("#EE1A1A1A"));
        canvas.drawRoundRect(panel, 24f, 24f, winnerPanelPaint);
        canvas.drawRoundRect(panel, 24f, 24f, highlightBorderPaint);

        canvas.drawText("🏆 WINNER 🏆", centerX, centerY - 65f, winnerTitlePaint);
        canvas.drawText(currentRoom.getWinnerName(), centerX, centerY + 25f, winnerNamePaint);

        User me = null;
        if (currentRoom.getPlayers() != null) {
            for (User u : currentRoom.getPlayers()) {
                if (u.getUid().equals(this.uid)) {
                    me = u;
                    break;
                }
            }
        }

        String profitMessage = "Round Over";
        winnerSubPaint.setColor(Color.parseColor("#AAFFFFFF"));

        if (me != null) {
            int profit = me.getChips() - me.getChipsBeforeRound();
            if (profit > 0) {
                profitMessage = "You won ₪" + profit + "!";
                winnerSubPaint.setColor(Color.parseColor("#4CAF50"));
            } else if (profit < 0) {
                profitMessage = "You lost ₪" + Math.abs(profit) + ".";
                winnerSubPaint.setColor(Color.parseColor("#F44336"));
            } else {
                profitMessage = "You broke even.";
            }
        }

        canvas.drawText(profitMessage, centerX, centerY + 85f, winnerSubPaint);
    }

    private void drawCardShadow(Canvas canvas, float x, float y, int w, int h) {
        canvas.drawRoundRect(new RectF(x + 5, y + 7, x + w + 5, y + h + 7), 6f, 6f, shadowPaint);
    }

    private Bitmap getCachedImage(String name, int w, int h) {
        String key = name + "_" + w;
        if (cardCache.containsKey(key)) return cardCache.get(key);
        int id = getResources().getIdentifier(name, "drawable", getContext().getPackageName());
        if (id != 0) {
            Bitmap bmp = Bitmap.createScaledBitmap(BitmapFactory.decodeResource(getResources(), id), w, h, true);
            cardCache.put(key, bmp);
            return bmp;
        }
        return null;
    }

    // ====================================================================
    // מערכת ניהול האנימציות והרענון של השרת
    // ====================================================================
    public void updateGame(GameRoom room, String uid) {
        this.currentRoom = room; this.uid = uid;
        int commCount = (room.getCommunityCards() != null) ? room.getCommunityCards().size() : 0;

        // אנימציית חלוקת קלפים התחלתית
        if (room.getGameState().equalsIgnoreCase("PreFlop") && !isNewRound) {
            isNewRound = true; startDealAnimation();
        } else if (!room.getGameState().equalsIgnoreCase("PreFlop")) {
            isNewRound = false;
        }

        // 🌟 התוספת החדשה: זיהוי כניסה ל-Showdown והפעלת אנימציית הסיבוב 🌟
        if (room.getGameState().equalsIgnoreCase("Showdown") && !previousGameState.equalsIgnoreCase("Showdown")) {
            startOpponentFlipAnimation();
        }

        // אנימציית היפוך קלפי קהילה (Flop, Turn, River)
        if (commCount > previousCommunityCount) {
            animatingStartIndex = previousCommunityCount; startFlipAnimation();
        } else {
            invalidate();
        }

        previousCommunityCount = commCount;
        previousGameState = room.getGameState(); // שומרים את המצב הנוכחי שיהפוך למצב הקודם בריצה הבאה
    }

    private void startDealAnimation() {
        if (isDealing) return;
        isDealing = true; dealProgress = 0f;
        ValueAnimator anim = ValueAnimator.ofFloat(0f, 1f);
        anim.setDuration(500); anim.setInterpolator(new DecelerateInterpolator());
        anim.addUpdateListener(a -> { dealProgress = (float) a.getAnimatedValue(); invalidate(); });
        anim.addListener(new AnimatorListenerAdapter() {
            @Override public void onAnimationEnd(Animator a) { isDealing = false; dealProgress = 1f; invalidate(); }
        });
        anim.start();
    }

    private void startFlipAnimation() {
        if (isFlipping) return;
        isFlipping = true;
        ValueAnimator anim = ValueAnimator.ofFloat(1f, -1f);
        anim.setDuration(500);
        anim.addUpdateListener(a -> { flipScale = (float) a.getAnimatedValue(); invalidate(); });
        anim.addListener(new AnimatorListenerAdapter() {
            @Override public void onAnimationEnd(Animator a) { isFlipping = false; flipScale = -1f; invalidate(); }
        });
        anim.start();
    }

    // 🌟 הפונקציה החדשה שמסובבת את קלפי היריבים ב-Showdown 🌟
    private void startOpponentFlipAnimation() {
        if (isOpponentFlipping) return;
        isOpponentFlipping = true;
        opponentFlipScale = 1f; // מתחיל מקלף סגור מלא

        ValueAnimator anim = ValueAnimator.ofFloat(1f, -1f); // רץ מ-1 ל-1- כדי לייצר אפקט סיבוב
        anim.setDuration(600); // 600 מילישניות של סיבוב חלק

        anim.addUpdateListener(animation -> {
            opponentFlipScale = (float) animation.getAnimatedValue();
            invalidate(); // מצייר מחדש את המסך בכל פריים של האנימציה
        });

        anim.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                isOpponentFlipping = false;
                opponentFlipScale = -1f; // מסתיים בקלף פתוח מלא
                invalidate();
            }
        });

        anim.start();
    }
}