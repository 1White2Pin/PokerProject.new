package Activities;

import android.os.Bundle;
import android.os.Handler;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import Models.Card;
import Models.Deck;
import Models.GameRoom;
import Helpers.HandEvaluator;
import Views.PokerGameView;
import com.example.pokerproject.R;
import Models.User;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;

public class OnlineActivity extends AppCompatActivity implements View.OnClickListener, SeekBar.OnSeekBarChangeListener {

    // רכיבי התצוגה של המשחק
    PokerGameView pokerGameView; // הקנבס הירוק שמצייר את הקלפים והשולחן
    Button btnFold, btnCheck, btnRaise;
    TextView tvPotSize;

    // משתני חיבור לפיירבייס
    DatabaseReference mDatabase;
    DatabaseReference roomRef;
    String roomId;
    String myUid; // המזהה הייחודי שלי כמשחק

    // רכיבי ממשק להעלאת הימור (Raise)
    LinearLayout layoutActionButtons, layoutBetting;
    Button btnCancelBet, btnConfirmBet, btnAllIn;
    SeekBar sbBetAmount;
    TextView tvBetAmount;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        // 1. חיבור לרכיבים בקובץ ה-XML
        btnFold = findViewById(R.id.btnFold);
        btnCheck = findViewById(R.id.btnCheck);
        btnRaise = findViewById(R.id.btnRaise);
        tvPotSize = findViewById(R.id.tvPotSize);
        pokerGameView = findViewById(R.id.pokerGameView);
        layoutActionButtons = findViewById(R.id.layoutActionButtons);
        layoutBetting = findViewById(R.id.layoutBetting);
        btnCancelBet = findViewById(R.id.btnCancelBet);
        btnConfirmBet = findViewById(R.id.btnConfirmBet);
        sbBetAmount = findViewById(R.id.sbBetAmount);
        tvBetAmount = findViewById(R.id.tvBetAmount);
        btnAllIn = findViewById(R.id.btnAllIn);

        // הגדרת מאזינים ללחיצות
        btnFold.setOnClickListener(this);
        btnCheck.setOnClickListener(this);
        btnRaise.setOnClickListener(this);
        btnCancelBet.setOnClickListener(this);
        btnConfirmBet.setOnClickListener(this);
        btnAllIn.setOnClickListener(this);
        sbBetAmount.setOnSeekBarChangeListener(this);

        // 2. קבלת המשתמש הנוכחי מ-Firebase Auth
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user != null) {
            myUid = user.getUid();
        }

        // 3. קבלת ה-ID של החדר (שנשלח ממסך ההמתנה)
        if (getIntent().hasExtra("roomId")) {
            roomId = getIntent().getStringExtra("roomId");
        }

        // הגנה: אם אין קוד חדר, חוזרים אחורה כדי למנוע קריסה
        if (roomId == null || roomId.isEmpty()) {
            Toast.makeText(this, "Error: No Room ID found!", Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        // 4. חיבור לפיירבייס - יצירת ה"צינור" לחדר הספציפי הזה
        mDatabase = FirebaseDatabase.getInstance().getReference();
        roomRef = mDatabase.child("Rooms").child(roomId);

        // ====================================================================
        // הלב של משחק הרשת: האזנה רציפה לכל שינוי שקורה בחדר ב-Firebase
        // ====================================================================
        roomRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (snapshot.exists()) {
                    GameRoom room = snapshot.getValue(GameRoom.class);

                    // מוודאים שהחדר והשחקנים תקינים כדי למנוע קריסות (NullPointer)
                    if (room != null && room.getPlayers() != null) {
                        tvPotSize.setText("Pot: " + room.getPot());

                        // אם החבילה עדיין ריקה, זה אומר שהסיבוב עוד לא באמת התחיל (הקלפים עוד לא חולקו)
                        if (room.getDeck() == null || room.getDeck().isEmpty()) {
                            // מנגנון הגנה של שרת-לקוח: רק השחקן במקום ה-0 (המארח) רשאי לערבב ולחלק קלפים!
                            // זה מונע מ-4 שחקנים לערבב את החבילה באותו זמן ולדרוס אחד את השני.
                            if (room.getPlayers().size() >= 2 && myUid.equals(room.getPlayers().get(0).getUid())) {
                                resetRoomForNextRound(room);
                                roomRef.setValue(room); // מעלים את החבילה המעורבבת לענן
                            }
                            return; // מחכים שהמארח יסיים לערבב
                        }

                        // מעדכנים את הקנבס (ציור הקלפים והשולחן) לפי הנתונים שהגיעו מהענן
                        pokerGameView.updateGame(room, myUid);

                        // --- ניהול תורות: כיבוי והדלקה של הכפתורים שלי ---
                        if (!room.getPlayers().isEmpty()) {
                            int currentTurnIndex = room.getTurnIndex();

                            if (currentTurnIndex >= 0 && currentTurnIndex < room.getPlayers().size()) {
                                User playerTurn = room.getPlayers().get(currentTurnIndex);
                                boolean isMyTurn = playerTurn.getUid().equals(myUid); // האם התור עכשיו הוא ה-UID שלי?

                                // בדיקה האם כבר פרשתי או נגמר לי הכסף
                                boolean amIFoldedOrOut = false;
                                for (User p : room.getPlayers()) {
                                    if (p.getUid().equals(myUid) && (p.getStatus().equals("Folded") || p.getStatus().equals("Out"))) {
                                        amIFoldedOrOut = true;
                                        break;
                                    }
                                }

                                // מדליק את הכפתורים רק אם זה התור שלי ואני עדיין במשחק
                                btnCheck.setEnabled(isMyTurn && !amIFoldedOrOut);
                                btnFold.setEnabled(isMyTurn && !amIFoldedOrOut);
                                btnRaise.setEnabled(isMyTurn && !amIFoldedOrOut);

                                // שינוי הטקסט של כפתור Check/Call/All-in בהתאם למצב הקופה
                                if (room.getCurrentBet() > 0) {
                                    if (room.getCurrentBet() >= playerTurn.getChips() + playerTurn.getCurrentBet()) {
                                        btnCheck.setText("All In"); // אם אין לי מספיק כסף להשוות
                                    } else {
                                        btnCheck.setText("Call"); // אם מישהו העלה, אני משווה
                                    }
                                } else {
                                    btnCheck.setText("Check"); // חינם
                                }
                            }
                        }
                    }
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Toast.makeText(OnlineActivity.this, "Database Error", Toast.LENGTH_SHORT).show();
            }
        });
    }

    // ====================================================================
    // טיפול בלחיצות של השחקן. שימו לב: אנחנו מעדכנים את הפיירבייס ולא רק את המסך!
    // ====================================================================
    @Override
    public void onClick(View view) {

        // --- פרישה (Fold) ---
        if (view.getId() == R.id.btnFold) {
            roomRef.get().addOnCompleteListener(task -> {
                if (task.isSuccessful() && task.getResult().exists()) {
                    GameRoom room = task.getResult().getValue(GameRoom.class);
                    if (room != null && room.getPlayers() != null) {
                        for (User player : room.getPlayers()) {
                            if (player.getUid().equals(myUid)) {
                                player.setStatus("Folded"); // משנים סטטוס ומעבירים תור
                                break;
                            }
                        }
                        advanceGameRound(room);
                    }
                }
            });

            // --- השוואה או דפיקה על השולחן (Check / Call) ---
        } else if (view.getId() == R.id.btnCheck) {
            roomRef.get().addOnCompleteListener(task -> {
                if (task.isSuccessful() && task.getResult().exists()) {
                    GameRoom room = task.getResult().getValue(GameRoom.class);
                    if (room != null && room.getPlayers() != null) {
                        for (User player : room.getPlayers()) {
                            if (player.getUid().equals(myUid)) {
                                // חישוב כמה כסף חסר לי כדי להשוות את ההימור הגבוה ביותר בחדר
                                if (room.getCurrentBet() > 0) {
                                    int amountToCall = room.getCurrentBet() - player.getCurrentBet();
                                    if (amountToCall > 0) {
                                        if (amountToCall >= player.getChips()) {
                                            amountToCall = player.getChips(); // All-in אוטומטי אם חסר
                                        }
                                        player.setChips(player.getChips() - amountToCall);
                                        room.setPot(room.getPot() + amountToCall);
                                        player.setCurrentBet(player.getCurrentBet() + amountToCall);
                                        Toast.makeText(OnlineActivity.this, "Called " + amountToCall, Toast.LENGTH_SHORT).show();
                                    }
                                }
                                player.setStatus("Checked"); // מסמן שסיימתי להגיב
                                break;
                            }
                        }
                        advanceGameRound(room); // מקדם את המשחק לשחקן הבא או לשלב הבא
                    }
                }
            });

            // --- פתיחת חלון העלאה (Raise) ---
        } else if (view.getId() == R.id.btnRaise) {
            roomRef.get().addOnCompleteListener(task -> {
                if (task.isSuccessful() && task.getResult().exists()) {
                    GameRoom room = task.getResult().getValue(GameRoom.class);
                    if (room != null && room.getPlayers() != null) {
                        for (User player : room.getPlayers()) {
                            if (player.getUid().equals(myUid)) {
                                // מתאימים את הבר-גרירה (SeekBar) לכמות הצ'יפים שיש לשחקן
                                sbBetAmount.setMax(player.getChips() + player.getCurrentBet());
                                sbBetAmount.setProgress(room.getCurrentBet());
                                tvBetAmount.setText(String.valueOf(room.getCurrentBet()));
                                break;
                            }
                        }
                        layoutActionButtons.setVisibility(View.GONE);
                        layoutBetting.setVisibility(View.VISIBLE);
                    }
                }
            });

            // --- ביטול העלאה (Cancel) ---
        } else if (view.getId() == R.id.btnCancelBet) {
            layoutActionButtons.setVisibility(View.VISIBLE);
            layoutBetting.setVisibility(View.GONE);

            // --- אישור העלאת הימור (Confirm Bet) ---
        } else if (view.getId() == R.id.btnConfirmBet) {
            int finalBetAmount = sbBetAmount.getProgress();
            if (finalBetAmount <= 0) {
                Toast.makeText(this, "Bet must be greater than 0", Toast.LENGTH_SHORT).show();
                return;
            }

            roomRef.get().addOnCompleteListener(task -> {
                if (task.isSuccessful() && task.getResult().exists()) {
                    GameRoom room = task.getResult().getValue(GameRoom.class);
                    if (room != null && room.getPlayers() != null) {
                        for (User player : room.getPlayers()) {
                            if (player.getUid().equals(myUid)) {
                                int amountToAdd = finalBetAmount - player.getCurrentBet();
                                if(finalBetAmount < room.getCurrentBet()) {
                                    Toast.makeText(OnlineActivity.this, "Not enough chips!", Toast.LENGTH_SHORT).show();
                                    return;
                                }
                                if (amountToAdd > player.getChips()) {
                                    amountToAdd = player.getChips(); // מגן ממינוס
                                }

                                // מעדכן את המחיר הקבוצתי בחדר, ואת הצ'יפים האישיים
                                room.setCurrentBet(finalBetAmount);
                                room.setPot(room.getPot() + amountToAdd);
                                player.setChips(player.getChips() - amountToAdd);
                                player.setCurrentBet(finalBetAmount);
                                player.setStatus("Checked");
                            } else if (!player.getStatus().equals("Folded") && !player.getStatus().equals("Out")) {
                                // ברגע שהעליתי הימור, התור חוזר לכולם! הם חייבים להגיב (Waiting)
                                player.setStatus("Waiting");
                            }
                        }
                        layoutBetting.setVisibility(View.GONE);
                        layoutActionButtons.setVisibility(View.VISIBLE);
                        advanceGameRound(room);
                    }
                }
            });

            // --- כפתור הכל פנימה (All-In) מתפריט ההעלאה ---
        } else if (view.getId() == R.id.btnAllIn) {
            roomRef.get().addOnCompleteListener(task -> {
                if (task.isSuccessful() && task.getResult().exists()) {
                    GameRoom room = task.getResult().getValue(GameRoom.class);
                    if (room != null && room.getPlayers() != null) {
                        for (User player : room.getPlayers()) {
                            if (player.getUid().equals(myUid)) {
                                int allInAmount = player.getChips();
                                int totalBet = player.getCurrentBet() + allInAmount;

                                if (totalBet > room.getCurrentBet()) {
                                    room.setCurrentBet(totalBet); // מעלה את רף החדר ל-All in שלי
                                }
                                room.setPot(room.getPot() + allInAmount);
                                player.setChips(0);
                                player.setCurrentBet(totalBet);
                                player.setStatus("Checked");
                            } else if (!player.getStatus().equals("Folded") && !player.getStatus().equals("Out")) {
                                player.setStatus("Waiting"); // פותח את התור מחדש לכולם
                            }
                        }
                        layoutBetting.setVisibility(View.GONE);
                        layoutActionButtons.setVisibility(View.VISIBLE);
                        advanceGameRound(room);
                    }
                }
            });
        }
    }

    // ══════════════════════════════════════════════════════
    //  לוגיקת ניהול הסיבובים - קובעת מה קורה אחרי מהלך של שחקן
    // ══════════════════════════════════════════════════════
    private void advanceGameRound(GameRoom room) {
        boolean isRoundComplete = true;
        // בודק אם כל השחקנים החיים בחדר נמצאים בסטטוס "Checked" (כלומר השוו להימור)
        for (User player : room.getPlayers()) {
            if (!player.getStatus().equals("Checked") && !player.getStatus().equals("Folded") && !player.getStatus().equals("Out")) {
                isRoundComplete = false;
                break;
            }
        }

        if (!isRoundComplete) {
            // לא כולם סיימו - מעבירים את התור לשחקן הבא שעדיין חי
            room.setTurnIndex(getNextActivePlayerIndex(room));
            roomRef.setValue(room); // מעדכנים בענן
        } else {
            // כולם סיימו להשוות! הזמן לפתוח קלפים ולעבור שלב
            if (room.getGameState().equalsIgnoreCase("River")) {
                handleShowdown(room); // אם סיימנו ריבר, הולכים לחשוף קלפים
            } else {
                ArrayList<Card> deck = room.getDeck();
                ArrayList<Card> communityCards = room.getCommunityCards();
                if (communityCards == null) communityCards = new ArrayList<>();

                // חלוקת קלפים לשולחן לפי השלב
                if (room.getGameState().equalsIgnoreCase("PreFlop")) {
                    for (int i = 0; i < 3; i++) { // פלופ - 3 קלפים
                        if (deck != null && !deck.isEmpty()) communityCards.add(deck.remove(0));
                    }
                    room.setGameState("Flop");
                } else if (room.getGameState().equalsIgnoreCase("Flop")) {
                    if (deck != null && !deck.isEmpty()) communityCards.add(deck.remove(0)); // טרן
                    room.setGameState("Turn");
                } else if (room.getGameState().equalsIgnoreCase("Turn")) {
                    if (deck != null && !deck.isEmpty()) communityCards.add(deck.remove(0)); // ריבר
                    room.setGameState("River");
                }
                room.setCommunityCards(communityCards);

                // בודקים אם נשאר רק שחקן אחד עם צ'יפים מול מישהו ב-All In, כדי להריץ קלפים מהר
                int activePlayers = 0;
                int playersWithChipsCount = 0;
                for (User player : room.getPlayers()) {
                    if (!player.getStatus().equals("Folded") && !player.getStatus().equals("Out")) {
                        activePlayers++;
                        if (player.getChips() > 0) playersWithChipsCount++;
                    }
                }

                if (activePlayers >= 2 && playersWithChipsCount <= 1) {
                    roomRef.setValue(room);
                    // הריצה המהירה של ה-All in
                    new Handler().postDelayed(() -> {
                        roomRef.get().addOnCompleteListener(task -> {
                            if (task.isSuccessful() && task.getResult().exists()) {
                                GameRoom currentRoom = task.getResult().getValue(GameRoom.class);
                                if (currentRoom != null) {
                                    advanceGameRound(currentRoom);
                                }
                            }
                        });
                    }, 1500);
                } else {
                    // איפוס ההימורים לקראת הסיבוב הבא
                    for (User player : room.getPlayers()) {
                        if (!player.getStatus().equals("Folded") && !player.getStatus().equals("Out")) {
                            player.setStatus("Waiting");
                        }
                        player.setCurrentBet(0);
                    }
                    room.setCurrentBet(0);
                    room.setTurnIndex(getFirstActivePlayerIndex(room));
                    roomRef.setValue(room); // מעדכנים בענן את הפתיחה החדשה
                }
            }
        }
    }

    // ══════════════════════════════════════════════════════
    //  SHOWDOWN — חישוב מנצחים והצגה על הקנבס
    // ══════════════════════════════════════════════════════
    private void handleShowdown(GameRoom room) {
        if (room.getPlayers() == null) return;

        // 1. שולחים את הידיים של כולם להערכה (HandEvaluator) ומקבלים ניקוד
        HashMap<String, Integer> playerScores = new HashMap<>();
        for (User u : room.getPlayers()) {
            if (!u.getStatus().equals("Folded")) {
                ArrayList<Card> sevenCards = new ArrayList<>();
                if (u.getHand() != null) sevenCards.addAll(u.getHand());
                if (room.getCommunityCards() != null) sevenCards.addAll(room.getCommunityCards());
                playerScores.put(u.getUid(), HandEvaluator.evaluateHand(sevenCards));
            } else {
                playerScores.put(u.getUid(), 0);
            }
        }

        // 2. מסדרים את השחקנים לפי כמה שהם הימרו (לצורך טיפול נכון במי שעשה All-in חלקי)
        ArrayList<User> sortedPlayers = new ArrayList<>(room.getPlayers());
        Collections.sort(sortedPlayers, (u1, u2) -> u1.getCurrentBet() - u2.getCurrentBet());

        // מוצאים את המנצח הכללי (או המנצחים במקרה של שוויון/Split)
        ArrayList<User> winners = new ArrayList<>();
        int bestScore = 0;
        for (User u : room.getPlayers()) {
            if (!u.getStatus().equals("Folded")) {
                int score = playerScores.get(u.getUid());
                if (score > bestScore) bestScore = score;
            }
        }
        for (User u : room.getPlayers()) {
            if (!u.getStatus().equals("Folded") && playerScores.get(u.getUid()) == bestScore) {
                winners.add(u);
            }
        }

        // 3. חלוקת הקופה בקומות (Side Pots) - מטפל יפה במצבי All-In קטנים
        int previousInvested = 0;
        for (int i = 0; i < sortedPlayers.size(); i++) {
            User current = sortedPlayers.get(i);
            int currentInvested = current.getCurrentBet();
            int layerAmount = currentInvested - previousInvested;

            if (layerAmount > 0) {
                int layerPot = 0;
                ArrayList<User> eligiblePlayers = new ArrayList<>();

                for (int j = i; j < sortedPlayers.size(); j++) {
                    layerPot += layerAmount;
                    if (!sortedPlayers.get(j).getStatus().equals("Folded")) {
                        eligiblePlayers.add(sortedPlayers.get(j));
                    }
                }

                if (!eligiblePlayers.isEmpty() && layerPot > 0) {
                    int layerBest = 0;
                    for (User p : eligiblePlayers) {
                        int s = playerScores.get(p.getUid());
                        if (s > layerBest) layerBest = s;
                    }
                    ArrayList<User> layerWinners = new ArrayList<>();
                    for (User p : eligiblePlayers) {
                        if (playerScores.get(p.getUid()) == layerBest) layerWinners.add(p);
                    }
                    // מפצלים את הכסף של ה"קומה" הזו לכל הזוכים
                    int splitAmount = layerPot / layerWinners.size();
                    for (User w : layerWinners) w.setChips(w.getChips() + splitAmount);
                }
                previousInvested = currentInvested;
            }
        }

        // 🌟 סנכרון הבנק הראשי מול Firebase (קריטי!) 🌟
        // רק המארח מבצע את החישוב הזה, כדי ש-4 מכשירים לא יוסיפו את הניצחון 4 פעמים בטעות.
        if (room.getPlayers().size() > 0 && myUid.equals(room.getPlayers().get(0).getUid())) {
            DatabaseReference usersRef = FirebaseDatabase.getInstance().getReference("Users");

            for (User u : room.getPlayers()) {
                // מבררים כמה כסף הוא הרוויח או הפסיד לעומת תחילת הסיבוב
                int roundProfitOrLoss = u.getChips() - u.getChipsBeforeRound();

                // מעדכנים את היתרה האמיתית שלו בבנק הראשי של האפליקציה (ששייכת לו גם בלובי)
                usersRef.child(u.getUid()).child("chips").get().addOnSuccessListener(snapshot -> {
                    if (snapshot.exists()) {
                        int realBank = snapshot.getValue(Integer.class);
                        usersRef.child(u.getUid()).child("chips").setValue(realBank + roundProfitOrLoss);
                    }
                });
            }
        }

        // בניית הודעת הניצחון למי שרואה את המסך
        StringBuilder msg = new StringBuilder();
        if (winners.size() == 1) {
            msg.append("🏆 ").append(winners.get(0).getNickname()).append(" Wins! 🏆");
        } else {
            msg.append("🤝 Tie! ");
            for (User w : winners) msg.append(w.getNickname()).append(" ");
        }

        room.setWinnerName(msg.toString());
        room.setGameState("Showdown");
        roomRef.setValue(room); // עדכון לענן כדי להציג לכולם את מסך הניצחון

        // משהים ל-4 שניות לתצוגה, ואז המארח מנקה את החדר לסיבוב הבא
        new Handler().postDelayed(() -> {
            roomRef.get().addOnCompleteListener(task -> {
                if (task.isSuccessful() && task.getResult().exists()) {
                    GameRoom currentRoomState = task.getResult().getValue(GameRoom.class);
                    // מוודאים שעוד לא התחילו משחק בלעדינו
                    if (currentRoomState != null && "Showdown".equals(currentRoomState.getGameState())) {
                        resetRoomForNextRound(currentRoomState);
                        currentRoomState.setWinnerName("");
                        roomRef.setValue(currentRoomState); // שולחים לענן חדר נקי ומוכן לפלופ הבא
                    }
                }
            });
        }, 4000);
    }

    // ══════════════════════════════════════════════════════
    //  איפוס החדר לסיבוב הבא - רק המארח מריץ את זה!
    // ══════════════════════════════════════════════════════
    private void resetRoomForNextRound(GameRoom room) {
        room.setGameState("PreFlop");
        if (room.getCommunityCards() != null) room.getCommunityCards().clear();

        for (User player : room.getPlayers()) {
            player.setChipsBeforeRound(player.getChips()); // שמירת נקודת התחלה לחישוב רווח עתידי
            player.setCurrentBet(0);
            player.setHand(new ArrayList<>()); // ריקון הקלפים הישנים

            if (player.getChips() <= 0) {
                player.setStatus("Out"); // משתמש שפשט רגל מודח
            } else {
                player.setStatus("Waiting"); // מוכן לסיבוב
            }
        }

        // חישוב עמדות: דילר, סמול בליינד וביג בליינד
        room.setDealerIndex((room.getDealerIndex() + 1) % room.getPlayers().size());
        int smallBlindIndex, bigBlindIndex, dealerTurnIndex;

        if(room.getPlayers().size() == 2) { // חוקי Heads up (2 שחקנים)
            smallBlindIndex = room.getDealerIndex();
            bigBlindIndex = (room.getDealerIndex() + 1) % room.getPlayers().size();
            dealerTurnIndex = smallBlindIndex;
        } else { // 3-4 שחקנים
            smallBlindIndex = (room.getDealerIndex() + 1) % room.getPlayers().size();
            bigBlindIndex = (room.getDealerIndex() + 2) % room.getPlayers().size();
            dealerTurnIndex = (bigBlindIndex + 1) % room.getPlayers().size();
        }

        int sbAmount = 100;
        int bbAmount = 200;

        // לקיחת כסף בכוח לשחקן הסמול בליינד
        User sbPlayer = room.getPlayers().get(smallBlindIndex);
        int actualSb = Math.min(sbAmount, sbPlayer.getChips());
        sbPlayer.setChips(sbPlayer.getChips() - actualSb);
        sbPlayer.setCurrentBet(actualSb);

        // לקיחת כסף בכוח לשחקן הביג בליינד
        User bbPlayer = room.getPlayers().get(bigBlindIndex);
        int actualBb = Math.min(bbAmount, bbPlayer.getChips());
        bbPlayer.setChips(bbPlayer.getChips() - actualBb);
        bbPlayer.setCurrentBet(actualBb);

        // עדכון הקופה והעברת התור לשחקן שאחרי הביג בליינד
        room.setPot(actualSb + actualBb);
        room.setCurrentBet(actualBb);
        room.setTurnIndex(dealerTurnIndex);

        // יצירת חבילה חדשה, ערבוב וחלוקת 2 קלפים לכל שחקן חי
        Deck newDeck = new Deck();
        newDeck.shuffle();
        ArrayList<Card> deckList = new ArrayList<>();
        Card c;
        while ((c = newDeck.drawCard()) != null) deckList.add(c);
        room.setDeck(deckList);

        for (User player : room.getPlayers()) {
            if(!player.getStatus().equals("Out")) {
                ArrayList<Card> newHand = new ArrayList<>();
                newHand.add(deckList.remove(0));
                newHand.add(deckList.remove(0));
                player.setHand(newHand);
            }
        }
    }

    // ══════════════════════════════════════════════════════
    //  פונקציות עזר - לוגיקת מעבר תורות
    // ══════════════════════════════════════════════════════

    // מוצא את המיקום ברשימה של השחקן הבא שעדיין משחק (מדלג על מי שפרש או מת)
    private int getNextActivePlayerIndex(GameRoom room) {
        int nextIndex = room.getTurnIndex() + 1;
        if (nextIndex >= room.getPlayers().size()) nextIndex = 0;
        while (room.getPlayers().get(nextIndex).getStatus().equals("Folded") || room.getPlayers().get(nextIndex).getStatus().equals("Out")) {
            nextIndex++;
            if (nextIndex >= room.getPlayers().size()) nextIndex = 0;
        }
        return nextIndex;
    }

    // מוצא את השחקן הפעיל הראשון בתחילת סיבוב פתיחת קלפים חדש
    private int getFirstActivePlayerIndex(GameRoom room) {
        int firstPlayer = 0;
        while (firstPlayer < room.getPlayers().size() && (room.getPlayers().get(firstPlayer).getStatus().equals("Folded") || room.getPlayers().get(firstPlayer).getStatus().equals("Out"))) {
            firstPlayer++;
        }
        return firstPlayer;
    }

    // עדכון תצוגת הכסף מעל הסליידר של הרייז (Raise)
    @Override
    public void onProgressChanged(SeekBar seekBar, int i, boolean b) {
        sbBetAmount.setProgress(i);
        tvBetAmount.setText(String.valueOf(i));
    }

    @Override public void onStartTrackingTouch(SeekBar seekBar) {}
    @Override public void onStopTrackingTouch(SeekBar seekBar) {}
}