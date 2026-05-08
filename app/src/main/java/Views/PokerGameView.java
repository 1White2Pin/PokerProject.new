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

    // משתנים לשמירת נתוני המשחק והשחקן המקומי
    private GameRoom currentRoom;
    private String uid;

    // מכחולים (Paint) - מגדירים צבעים, עובי קווים וסגנונות ציור
    private Paint tablePaint;
    private Paint borderPaint;
    private Paint cardPaint;
    private Paint textBgPaint;
    private Paint textPaint;

    // מכחולים לתצוגת הניצחון (Showdown)
    private Paint overlayPaint;
    private Paint winnerTextPaint;

    // מידות המסך והקלפים
    private int screenW, screenH;
    private int cardW, cardH;
    private int smallCardW, smallCardH;
    private RectF tableRect; // המלבן שתוחם את שולחן הפוקר האליפטי

    // מטמון (Cache) לשמירת תמונות הקלפים בזיכרון כדי למנוע טעינה חוזרת ואיטיות
    private HashMap<String, Bitmap> cardCache;
    private Bitmap smallBackBitmap;
    private Bitmap normalBackBitmap;

    // משתנים לניהול אנימציית היפוך הקלפים (Flop, Turn, River)
    private boolean isFlipping = false;
    private float flipScale = 1f;
    private int previousCommunityCount = 0;
    private int animatingStartIndex = 0;

    // משתנים לניהול אנימציית חלוקת הקלפים (Dealing) בתחילת סיבוב
    private boolean isDealing = false;
    private float dealProgress = 1f;
    private boolean isNewRound = false;

    // בנאי ראשון - נוצר כשיוצרים את התצוגה דרך הקוד
    public PokerGameView(Context context) {
        super(context);
        init();
    }

    // בנאי שני - נוצר כשמערכת האנדרואיד קוראת את התצוגה מתוך קובץ ה-XML
    public PokerGameView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    // פעולת אתחול: מכינה את כל ה"מכחולים" וההגדרות הגרפיות הראשוניות
    public void init() {
        cardCache = new HashMap<>();

        // מכחול לשולחן הירוק
        tablePaint = new Paint(Paint.ANTI_ALIAS_FLAG);

        // מכחול למסגרת העץ של השולחן
        borderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        borderPaint.setColor(Color.parseColor("#5C3A21"));
        borderPaint.setStyle(Paint.Style.STROKE);
        borderPaint.setStrokeWidth(30f);

        // מכחול לציור הקלפים (משמש גם לקביעת שקיפות לקלפים של מי שפרש)
        cardPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

        // מכחול לרקע השחור-שקוף מאחורי שמות השחקנים
        textBgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        textBgPaint.setColor(Color.parseColor("#99000000"));

        // מכחול לטקסט של שמות השחקנים והצ'יפים
        textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        textPaint.setColor(Color.WHITE);
        textPaint.setTextAlign(Paint.Align.CENTER);
        textPaint.setTextSize(35f);

        // אתחול מכחולי הניצחון (מסך כהה וטקסט זהב)
        overlayPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        overlayPaint.setColor(Color.parseColor("#B3000000"));

        winnerTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        winnerTextPaint.setColor(Color.parseColor("#FFD700"));
        winnerTextPaint.setTextSize(100f);
        winnerTextPaint.setTextAlign(Paint.Align.CENTER);
        winnerTextPaint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        winnerTextPaint.setShadowLayer(15f, 0f, 5f, Color.BLACK);
    }

    // מופעלת כשהמסך נטען או משנה גודל - כאן מחושבות המידות המדויקות
    @Override
    public void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        screenW = w; // רוחב המסך
        screenH = h; // גובה המסך

        // חישוב גודל הקלפים באופן יחסי לגובה המסך
        cardH = (int)(screenH * 0.32);
        cardW = (int)(cardH * 0.72);
        smallCardH = (int)(cardH * 0.70);
        smallCardW = (int)(smallCardH * 0.72);

        // הגדרת גבולות שולחן הפוקר (משאיר שוליים של 50 פיקסלים)
        tableRect = new RectF(50, 50, screenW - 50, screenH - 50);

        // יצירת אפקט מעבר צבעים (Gradient) שנותן לשולחן מראה של תלת-ממד
        RadialGradient gradient = new RadialGradient(
                screenW / 2f, screenH / 2f,
                screenW / 1.5f,
                Color.parseColor("#2E7D32"),
                Color.parseColor("#124015"),
                Shader.TileMode.CLAMP
        );
        tablePaint.setShader(gradient);

        // טעינה ושינוי גודל של התמונה של גב הקלף, כדי לא לעשות את זה בכל פריים מחדש
        int backResId = getResources().getIdentifier("card_back", "drawable", getContext().getPackageName());
        if (backResId != 0) {
            Bitmap originalBack = BitmapFactory.decodeResource(getResources(), backResId);
            smallBackBitmap = Bitmap.createScaledBitmap(originalBack, smallCardW, smallCardH, true);
            normalBackBitmap = Bitmap.createScaledBitmap(originalBack, cardW, cardH, true);
        }
    }

    // לב המערכת: הפעולה שאחראית לצייר בפועל את כל האלמנטים על המסך (מופעלת בכל פעם שיש invalidate)
    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        super.onDraw(canvas);

        // 1. צביעת הרקע שמחוץ לשולחן בשחור
        canvas.drawColor(Color.BLACK);

        // 2. ציור שולחן הפוקר והמסגרת שלו
        if (tableRect != null) {
            canvas.drawOval(tableRect, tablePaint);
            canvas.drawOval(tableRect, borderPaint);
        }

        if (currentRoom == null) return; // הגנה: אם אין נתוני חדר, לא ממשיכים

        // חישוב נקודת האמצע של המסך (ה"חבילה" שממנה מחלקים את הקלפים)
        float deckX = screenW / 2f - cardW / 2f;
        float deckY = screenH / 2f - cardH / 2f;

        // 3. ציור קלפי הקהילה (הקלפים שבאמצע השולחן)
        if (currentRoom.getCommunityCards() != null && !currentRoom.getCommunityCards().isEmpty()) {
            ArrayList<Card> communityCards = currentRoom.getCommunityCards();
            int space = 15;
            int totalWidth = communityCards.size() * cardW + (communityCards.size() - 1) * space;
            float startX = (screenW - totalWidth) / 2f;
            float startY = (screenH - cardH) / 2f - 30;

            for (int i = 0; i < communityCards.size(); i++) {
                Card card = communityCards.get(i);
                Bitmap frontBitmap = getCachedImage(card.getImageResourceName(), cardW, cardH);

                // טיפול באנימציית ההיפוך (אם הקלף כרגע מונפש)
                if (isFlipping && i >= animatingStartIndex) {
                    canvas.save(); // שומר את מצב הקנבס לפני השינוי
                    float centerX = startX + cardW / 2f;
                    float centerY = startY + cardH / 2f;
                    float currentScaleX = Math.abs(flipScale);
                    canvas.scale(currentScaleX, 1f, centerX, centerY); // כיווץ הקלף כדי ליצור אשליית היפוך

                    if (flipScale > 0) {
                        // חצי ראשון של האנימציה: רואים את גב הקלף
                        if (normalBackBitmap != null) canvas.drawBitmap(normalBackBitmap, startX, startY, cardPaint);
                    } else {
                        // חצי שני של האנימציה: רואים את פני הקלף
                        if (frontBitmap != null) canvas.drawBitmap(frontBitmap, startX, startY, cardPaint);
                    }
                    canvas.restore(); // מחזיר את הקנבס למצב הרגיל כדי לא להשפיע על שאר האלמנטים
                } else {
                    // ציור רגיל של הקלף אם אין אנימציה
                    if (frontBitmap != null) canvas.drawBitmap(frontBitmap, startX, startY, cardPaint);
                }
                startX += cardW + space; // מעבר למיקום של הקלף הבא
            }
        }

        // חלוקת השחקנים ל"אני" ול"שאר השחקנים"
        User me = null;
        ArrayList<User> opponents = new ArrayList<>();
        if (currentRoom.getPlayers() != null) {
            for (User player : currentRoom.getPlayers()) {
                if (player.getUid() != null && player.getUid().equals(uid)) me = player;
                else opponents.add(player);
            }
        }

        // 4. ציור הקלפים של השחקן המקומי (היד שלך)
        if (me != null && me.getHand() != null && !me.getHand().isEmpty()) {
            // אם השחקן פרש, הקלפים יהפכו לחצי שקופים
            if ("Folded".equals(me.getStatus())) cardPaint.setAlpha(100);
            else cardPaint.setAlpha(255);

            int myHandSize = me.getHand().size();
            float overlap = cardW * 0.5f; // חפיפה בין הקלפים
            float totalWidth = cardW + (myHandSize - 1) * overlap;
            float targetX = (screenW - totalWidth) / 2f; // מיקום סופי בציר ה-X
            float targetY = screenH - cardH - 20;        // מיקום סופי בציר ה-Y (בתחתית המסך)

            for (Card card : me.getHand()) {
                Bitmap bitmap = getCachedImage(card.getImageResourceName(), cardW, cardH);

                // חישוב המיקום הנוכחי של הקלף לפי התקדמות אנימציית החלוקה (dealProgress)
                float currentX = deckX + (targetX - deckX) * dealProgress;
                float currentY = deckY + (targetY - deckY) * dealProgress;

                if (bitmap != null) canvas.drawBitmap(bitmap, currentX, currentY, cardPaint);
                targetX += overlap;
            }
            cardPaint.setAlpha(255); // החזרת השקיפות למצב רגיל

            // ציור התווית עם השם והכסף של השחקן
            drawPlayerLabel(canvas, me.getNickname(), me.getChips(), screenW / 2f, targetY - 10);
        }

        // 5. ציור הקלפים של היריבים (קצוות השולחן)
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

                // קביעת מיקומים שונים לכל יריב (שמאל, למעלה, ימין)
                if (i == 0) {
                    targetX = 60; targetY = (screenH - smallCardH) / 2f - 30;
                } else if (i == 1) {
                    targetX = (screenW - totalW) / 2f; targetY = 40;
                } else if (i == 2) {
                    targetX = screenW - totalW - 60; targetY = (screenH - smallCardH) / 2f - 30;
                }

                float tempTargetX = targetX;
                for (int k = 0; k < handSize; k++) {
                    // חישוב מיקום נוכחי לאנימציית החלוקה מהאמצע למיקום היריב
                    float currentX = deckX + (tempTargetX - deckX) * dealProgress;
                    float currentY = deckY + (targetY - deckY) * dealProgress;

                    canvas.drawBitmap(smallBackBitmap, currentX, currentY, cardPaint);
                    tempTargetX += overlap;
                }
                cardPaint.setAlpha(255);
                drawPlayerLabel(canvas, opponent.getNickname(), opponent.getChips(), targetX + totalW / 2f, targetY + smallCardH + 40);
            }
        }

        // 6. ציור מסך הניצחון (Showdown) - מצויר אחרון כדי שיופיע מעל הכל
        if ("Showdown".equalsIgnoreCase(currentRoom.getGameState()) && currentRoom.getWinnerName() != null && !currentRoom.getWinnerName().isEmpty()) {
            canvas.drawRect(0, 0, screenW, screenH, overlayPaint); // החשכת המסך
            canvas.drawText(currentRoom.getWinnerName(), screenW / 2f, screenH / 2f + 30, winnerTextPaint); // שם המנצח באמצע
        }
    }

    // פונקציית עזר: מציירת את הרקע והטקסט של תוויות השחקנים (שם וכמות צ'יפים)
    private void drawPlayerLabel(Canvas canvas, String name, int chips, float centerX, float bottomY) {
        String text = (name != null ? name : "Player") + " | ₪" + chips;
        RectF bgRect = new RectF(centerX - 120, bottomY - 45, centerX + 120, bottomY + 15);
        canvas.drawRoundRect(bgRect, 15, 15, textBgPaint);
        canvas.drawText(text, centerX, bottomY, textPaint);
    }

    // פונקציית עזר: מנהלת את טעינת תמונות הקלפים ושומרת אותן בזיכרון (Cache) כדי לשפר ביצועים
    private Bitmap getCachedImage(String cardName, int reqWidth, int reqHeight) {
        String key = cardName + "_" + reqWidth;
        if (cardCache.containsKey(key)) return cardCache.get(key); // מחזיר מהזיכרון אם קיים

        int resID = getResources().getIdentifier(cardName, "drawable", getContext().getPackageName());
        if (resID != 0) {
            Bitmap originalBitmap = BitmapFactory.decodeResource(getResources(), resID);
            Bitmap scaledBitmap = Bitmap.createScaledBitmap(originalBitmap, reqWidth, reqHeight, true);
            cardCache.put(key, scaledBitmap); // שומר בזיכרון לשימוש עתידי
            return scaledBitmap;
        }
        return null;
    }

    // הפעולה שקוראים לה מה-Activity כדי לעדכן את התצוגה בנתונים החדשים מהשרת/מהמשחק
    public void updateGame(GameRoom room, String uid) {
        this.currentRoom = room;
        this.uid = uid;

        int currentCommunityCount = (room.getCommunityCards() != null) ? room.getCommunityCards().size() : 0;

        // מזהה אם התחיל סיבוב חדש (PreFlop) ומפעיל אנימציית חלוקת קלפים
        if (room.getGameState().equalsIgnoreCase("PreFlop") && !isNewRound) {
            isNewRound = true;
            startDealAnimation();
        }
        else if (!room.getGameState().equalsIgnoreCase("PreFlop")) {
            isNewRound = false;
        }

        // מזהה אם נוספו קלפי קהילה חדשים ומפעיל אנימציית היפוך
        if (currentCommunityCount > previousCommunityCount) {
            animatingStartIndex = previousCommunityCount;
            startFlipAnimation();
        } else if (currentCommunityCount == 0) {
            previousCommunityCount = 0;
            invalidate(); // קורא מחדש לפעולת onDraw לעדכון מיידי
        } else {
            invalidate(); // עדכון רגיל ללא אנימציות
        }

        previousCommunityCount = currentCommunityCount;
    }

    // מפעילה את האנימציה שבה הקלפים עפים ממרכז המסך לשחקנים (משנה את dealProgress מ-0 ל-1)
    private void startDealAnimation() {
        if (isDealing) return; // מונע הפעלה כפולה
        isDealing = true;
        dealProgress = 0f;

        ValueAnimator animator = ValueAnimator.ofFloat(0f, 1f);
        animator.setDuration(500); // האנימציה תימשך חצי שנייה
        animator.setInterpolator(new DecelerateInterpolator()); // גורם לאנימציה להאט לקראת הסוף

        // מעדכן את המסך בכל פריים של האנימציה (כאן תוקנה השגיאה addUpdaפteListener)
        animator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator animation) {
                dealProgress = (float) animation.getAnimatedValue();
                invalidate(); // אומר לאנדרואיד לצייר מחדש
            }
        });

        // פעולות סיום האנימציה
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

    // מפעילה את האנימציה של היפוך קלפי הקהילה (משנה את ה-scale מ-1 למינוס 1)
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
                flipScale = -1f; // סיום ההיפוך - הקלף מוצג פתוח
                invalidate();
            }
        });

        animator.start();
    }
}