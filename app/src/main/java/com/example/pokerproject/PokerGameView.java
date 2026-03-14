package com.example.pokerproject;

import static com.bumptech.glide.Glide.init;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.List;

public class PokerGameView extends View {
    private GameRoom currentRoom;
    private String uid;
    private Paint paint;
    private int screenW, screenH;
    private int cardW, cardH;

    public PokerGameView(Context context) {
        super(context);
        init();
    }

    public PokerGameView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }








    public void init()
    {
        paint = new Paint();
        paint.setAntiAlias(true);


    }

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        super.onDraw(canvas);
        canvas.drawColor(Color.parseColor("#35654D"));

        // בדיקה ראשונית
        if (currentRoom == null) return;

        int space = 10; // רווח בין קלפים

        // ---------------------------------------------------------
        // חלק 1: ציור קלפי הקהילה (באמצע המסך)
        // ---------------------------------------------------------
        if (currentRoom.getCommunityCards() != null && !currentRoom.getCommunityCards().isEmpty()) {
            ArrayList<Card> communityCards = currentRoom.getCommunityCards();
            int totalWidth = communityCards.size() * cardW + (communityCards.size() - 1) * space;
            float currentX = (screenW - totalWidth) / 2f;
            float currentY = (screenH - cardH) / 2f;

            for (Card card : communityCards) {
                Bitmap bitmap = getImage(card, cardW, cardH);
                if (bitmap != null) {
                    canvas.drawBitmap(bitmap, currentX, currentY, paint);
                }
                currentX += cardW + space;
            }
        }

        // ---------------------------------------------------------
        // חלק 2: מציאת השחקנים (אני vs יריבים)
        // ---------------------------------------------------------
        User me = null;
        ArrayList<User> opponents = new ArrayList<>();

        if (currentRoom.getPlayers() != null) {
            for (User player : currentRoom.getPlayers()) {
                if (player.getUid() != null && player.getUid().equals(uid)) {
                    me = player; // זה אני
                } else {
                    opponents.add(player); // זה יריב
                }
            }
        }

        // ---------------------------------------------------------
        // חלק 3: ציור הקלפים שלי (למטה באמצע)
        // ---------------------------------------------------------
        if (me != null && me.getHand() != null && !me.getHand().isEmpty()) {
            if ("Folded".equals(me.getStatus())) paint.setAlpha(100);
            else paint.setAlpha(255);

            int myHandSize = me.getHand().size();
            int totalWidthMyHand = myHandSize * cardW + (myHandSize - 1) * space;
            float myX = (screenW - totalWidthMyHand) / 2f;
            float myY = screenH - cardH - 30;

            for (Card card : me.getHand()) {
                Bitmap bitmap = getImage(card, cardW, cardH);
                if (bitmap != null) canvas.drawBitmap(bitmap, myX, myY, paint);
                myX += cardW + space;
            }
            paint.setAlpha(255); // איפוס שקיפות
        }

        // ---------------------------------------------------------
        // חלק 4: ציור היריבים (עד 3 יריבים)
        // ---------------------------------------------------------
        if (!opponents.isEmpty()) {

            // א. חישוב הגודל הקטן ליריבים
            int smallCardH = (int)(cardH * 0.6);
            int smallCardW = (int)(smallCardH * 0.7);

            // ב. הכנת תמונת "גב קלף" מוקטנת (פעם אחת לכולם!)
            Bitmap smallBackBitmap = null;
            int backResId = getResources().getIdentifier("card_back", "drawable", getContext().getPackageName());
            if (backResId != 0) {
                Bitmap originalBack = BitmapFactory.decodeResource(getResources(), backResId);
                smallBackBitmap = Bitmap.createScaledBitmap(originalBack, smallCardW, smallCardH, true);
            }

            // ג. לולאה על היריבים וחישוב המיקום לפי האינדקס
            if (smallBackBitmap != null) {
                for (int i = 0; i < opponents.size(); i++) {
                    User opponent = opponents.get(i);

                    // אם ליריב אין קלפים, מדלגים עליו
                    if (opponent.getHand() == null || opponent.getHand().isEmpty()) continue;

                    // בדיקת Fold
                    if ("Folded".equals(opponent.getStatus())) paint.setAlpha(100);
                    else paint.setAlpha(255);

                    int handSize = opponent.getHand().size();
                    int totalW = handSize * smallCardW + (handSize - 1) * space;

                    float oppX = 0;
                    float oppY = 0;

                    // --- לוגיקת המיקומים ---
                    if (i == 0) {
                        // יריב ראשון: צד שמאל (Left Center)
                        oppX = 20;
                        oppY = (screenH - smallCardH) / 2f;
                    }
                    else if (i == 1) {
                        // יריב שני: למעלה באמצע (Top Center)
                        oppX = (screenW - totalW) / 2f;
                        oppY = 20;
                    }
                    else if (i == 2) {
                        // יריב שלישי: צד ימין (Right Center)
                        oppX = screenW - totalW - 20;
                        oppY = (screenH - smallCardH) / 2f;
                    }

                    // ד. ציור הקלפים של היריב הנוכחי
                    for (int k = 0; k < handSize; k++) {
                        canvas.drawBitmap(smallBackBitmap, oppX, oppY, paint);
                        oppX += smallCardW + space;
                    }

                    paint.setAlpha(255); // איפוס
                }
            }
        }
    }

    private Bitmap getImage(Card card, int reqWidth, int reqHeight) {
        String cardName = card.getImageResourceName();
        int resID = getResources().getIdentifier(cardName, "drawable", getContext().getPackageName());

        if (resID != 0) {
            Bitmap bitmap = BitmapFactory.decodeResource(getResources(), resID);
            return Bitmap.createScaledBitmap(bitmap, reqWidth, reqHeight, true);
        }
        return null;
    }

    public void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        screenW = w;
        screenH = h;
        cardH = (int)(h*0.2);
        cardW = (int)(cardH*0.7);


    }
    public void updateGame(GameRoom room, String uid)
    {
        this.currentRoom = room;
        this.uid = uid;
        invalidate();

    }






}
