package Activities;

import android.content.Intent;
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
import androidx.appcompat.app.AlertDialog;
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

    // --- רכיבי התצוגה (UI) ---
    PokerGameView pokerGameView; // הקנבס הירוק שמצייר את הקלפים והשולחן
    Button btnFold, btnCheck, btnRaise;
    TextView tvPotSize;

    // --- משתני חיבור לפיירבייס ---
    DatabaseReference mDatabase;
    DatabaseReference roomRef;
    String roomId; // קוד החדר הנוכחי
    String myUid;  // המזהה הייחודי של השחקן שמחזיק את הטלפון

    // --- רכיבי ממשק להעלאת הימור (Raise) ---
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

        // 1. חיבור המשתנים לרכיבים בקובץ העיצוב (XML)
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

        // הגדרת מאזינים ללחיצות על הכפתורים
        btnFold.setOnClickListener(this);
        btnCheck.setOnClickListener(this);
        btnRaise.setOnClickListener(this);
        btnCancelBet.setOnClickListener(this);
        btnConfirmBet.setOnClickListener(this);
        btnAllIn.setOnClickListener(this);
        sbBetAmount.setOnSeekBarChangeListener(this);

        // 2. קבלת המשתמש הנוכחי מ-Firebase Auth כדי לדעת מי אני
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user != null) {
            myUid = user.getUid();
        }

        // 3. קבלת ה-ID של החדר (שנשלח ממסך ההמתנה)
        if (getIntent().hasExtra("roomId")) {
            roomId = getIntent().getStringExtra("roomId");
        }

        // הגנה: אם אין קוד חדר משום מה, חוזרים אחורה כדי למנוע קריסה
        if (roomId == null || roomId.isEmpty()) {
            Toast.makeText(this, "Error: No Room ID found!", Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        // 4. חיבור לפיירבייס - יצירת "צינור תקשורת" ישירות לחדר הספציפי שלנו
        mDatabase = FirebaseDatabase.getInstance().getReference();
        roomRef = mDatabase.child("Rooms").child(roomId);

        // ====================================================================
        // הלב של משחק הרשת: המאזין שקורא שינויים מ-Firebase בזמן אמת
        // כל פעם שמישהו בחדר עושה מהלך, הפונקציה הזו מופעלת אצל כולם!
        // ====================================================================
        roomRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (snapshot.exists()) {
                    GameRoom room = snapshot.getValue(GameRoom.class);

                    if (room == null) return; // הגנה מקריסות במקרה של שגיאת רשת

                    // 🌟 בדיקת סיום משחק (Yoda Condition למניעת קריסות) 🌟
                    // אם הסטטוס הוא GameOver, המשחק נגמר ואנחנו מחשבים רווחים
                    if("GameOver".equals(room.getGameState())) {
                        int currentChips = 0;
                        if(room.getPlayers() != null) {
                            for(User u : room.getPlayers()) {
                                if(u.getUid().equals(myUid)) {
                                    currentChips = u.getChips(); // מוצאים כמה כסף נשאר לי
                                    break; // עוצרים את הלולאה כי מצאנו אותי
                                }
                            }
                        }

                        // חישוב הרווח (כמה יש לי עכשיו פחות עם כמה נכנסתי לחדר)
                        int totalProfit = currentChips - room.getStartingChips();
                        String message = "";

                        // בניית הודעה מותאמת אישית
                        if(totalProfit > 0) {
                            message = "You won ₪" + totalProfit + " in this room! Great job!";
                        } else if (totalProfit < 0) {
                            message = "You lost ₪" + Math.abs(totalProfit) + ". Better luck next time!";
                        } else {
                            message = "You broke even! No profit, no loss.";
                        }

                        // הקפצת חלון סיכום שלא ניתן לסגור אלא בלחיצה על הכפתור
                        new AlertDialog.Builder(OnlineActivity.this)
                                .setTitle("Game Over")
                                .setMessage(message)
                                .setCancelable(false) // נועל את החלון
                                .setPositiveButton("Back to lobby", (dialog, which) -> {
                                    // ברגע שלוחצים על הכפתור, חוזרים ללובי ומוחקים היסטוריה
                                    Intent intent = new Intent(OnlineActivity.this, LobbyActivity.class);
                                    intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
                                    startActivity(intent);
                                    finish();
                                })
                                .show();

                        return; // עוצרים את המשך הפונקציה כדי לא לצייר את החדר
                    }

                    // אם המשחק ממשיך - מוודאים שהשחקנים תקינים לפני שמציירים
                    if (room.getPlayers() != null) {
                        tvPotSize.setText("Pot: " + room.getPot());

                        // בדיקה האם הסיבוב רק התחיל ועדיין אין קלפים (Deck ריק)
                        if (room.getDeck() == null || room.getDeck().isEmpty()) {
                            // הגנה קריטית: רק המארח (שחקן 0) מערבב ומחלק! שאר השחקנים מחכים להחלטה שלו.
                            if (room.getPlayers().size() >= 2 && myUid.equals(room.getPlayers().get(0).getUid())) {
                                resetRoomForNextRound(room); // יוצר חבילה חדשה ומחלק קלפים
                                roomRef.setValue(room); // דוחף את החבילה המעורבבת לענן
                            }
                            return; // כולם מחכים עד שהחבילה תרד מהענן
                        }

                        // שולחים את המידע העדכני לקנבס כדי שיצייר את השולחן מחדש
                        pokerGameView.updateGame(room, myUid);

                        // --- ניהול תורות וכפתורי משחק ---
                        if (!room.getPlayers().isEmpty()) {
                            int currentTurnIndex = room.getTurnIndex();

                            // בודקים של מי התור עכשיו
                            if (currentTurnIndex >= 0 && currentTurnIndex < room.getPlayers().size()) {
                                User playerTurn = room.getPlayers().get(currentTurnIndex);
                                boolean isMyTurn = playerTurn.getUid().equals(myUid); // האם זה אני?

                                // בדיקה האם הסטטוס שלי הוא "מת" (פרשתי או שאין לי כסף)
                                boolean amIFoldedOrOut = false;
                                for (User p : room.getPlayers()) {
                                    if (p.getUid().equals(myUid) && (p.getStatus().equals("Folded") || p.getStatus().equals("Out"))) {
                                        amIFoldedOrOut = true;
                                        break;
                                    }
                                }

                                // מדליק את כפתורי המשחק רק אם התור שלי ואני בחיים
                                btnCheck.setEnabled(isMyTurn && !amIFoldedOrOut);
                                btnFold.setEnabled(isMyTurn && !amIFoldedOrOut);
                                btnRaise.setEnabled(isMyTurn && !amIFoldedOrOut);

                                // שינוי חכם של טקסט הכפתור (Call לעומת Check לעומת All In)
                                if (room.getCurrentBet() > 0) {
                                    if (room.getCurrentBet() >= playerTurn.getChips() + playerTurn.getCurrentBet()) {
                                        btnCheck.setText("All In"); // חסר לי כסף להשוות, אז אני שם את הכל
                                    } else {
                                        btnCheck.setText("Call"); // אני צריך להשוות הימור של מישהו אחר
                                    }
                                } else {
                                    btnCheck.setText("Check"); // חינם - אין הימור פעיל
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
    // טיפול בלחיצות כפתורים של השחקן (הכפתורים מעדכנים את Firebase)
    // ====================================================================
    @Override
    public void onClick(View view) {

        // --- כפתור פרישה (Fold) ---
        if (view.getId() == R.id.btnFold) {
            roomRef.get().addOnCompleteListener(task -> {
                if (task.isSuccessful() && task.getResult().exists()) {
                    GameRoom room = task.getResult().getValue(GameRoom.class);
                    if (room != null && room.getPlayers() != null) {
                        for (User player : room.getPlayers()) {
                            if (player.getUid().equals(myUid)) {
                                player.setStatus("Folded"); // הופך ללא-פעיל בסיבוב
                                break;
                            }
                        }
                        advanceGameRound(room); // מעביר את התור לשחקן הבא
                    }
                }
            });

            // --- כפתור בדיקה/השוואה (Check / Call) ---
        } else if (view.getId() == R.id.btnCheck) {
            roomRef.get().addOnCompleteListener(task -> {
                if (task.isSuccessful() && task.getResult().exists()) {
                    GameRoom room = task.getResult().getValue(GameRoom.class);
                    if (room != null && room.getPlayers() != null) {
                        for (User player : room.getPlayers()) {
                            if (player.getUid().equals(myUid)) {
                                // אם מישהו העלה, אנחנו מחשבים כמה חסר לי כדי להשוות
                                if (room.getCurrentBet() > 0) {
                                    int amountToCall = room.getCurrentBet() - player.getCurrentBet();
                                    if (amountToCall > 0) {
                                        // מונע כניסה למינוס (All-In אוטומטי אם חסר כסף)
                                        if (amountToCall >= player.getChips()) {
                                            amountToCall = player.getChips();
                                        }
                                        player.setChips(player.getChips() - amountToCall);
                                        room.setPot(room.getPot() + amountToCall);
                                        player.setCurrentBet(player.getCurrentBet() + amountToCall);
                                    }
                                }
                                player.setStatus("Checked"); // מסמן שאישרתי וסיימתי את התור שלי
                                break;
                            }
                        }
                        advanceGameRound(room); // מעביר תור
                    }
                }
            });

            // --- כפתור פתיחת חלון העלאת הימור (Raise) ---
        } else if (view.getId() == R.id.btnRaise) {
            roomRef.get().addOnCompleteListener(task -> {
                if (task.isSuccessful() && task.getResult().exists()) {
                    GameRoom room = task.getResult().getValue(GameRoom.class);
                    if (room != null && room.getPlayers() != null) {
                        for (User player : room.getPlayers()) {
                            if (player.getUid().equals(myUid)) {
                                // מסדר את ה"סליידר" שיימתח עד למקסימום הכסף שלי
                                sbBetAmount.setMax(player.getChips() + player.getCurrentBet());
                                sbBetAmount.setProgress(room.getCurrentBet());
                                tvBetAmount.setText(String.valueOf(room.getCurrentBet()));
                                break;
                            }
                        }
                        // מחליף בין הכפתורים הרגילים לתפריט ההימורים
                        layoutActionButtons.setVisibility(View.GONE);
                        layoutBetting.setVisibility(View.VISIBLE);
                    }
                }
            });

            // --- ביטול תפריט העלאה וחזרה לכפתורים הרגילים ---
        } else if (view.getId() == R.id.btnCancelBet) {
            layoutActionButtons.setVisibility(View.VISIBLE);
            layoutBetting.setVisibility(View.GONE);

            // --- אישור ההעלאה (Confirm Bet) ---
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
                                // הגנה: לא ניתן להעלות פחות ממה שכבר קבעו בחדר
                                if(finalBetAmount < room.getCurrentBet()) {
                                    Toast.makeText(OnlineActivity.this, "Not enough chips!", Toast.LENGTH_SHORT).show();
                                    return;
                                }
                                if (amountToAdd > player.getChips()) {
                                    amountToAdd = player.getChips();
                                }

                                // עדכון המחיר הקבוצתי והכסף בקופה
                                room.setCurrentBet(finalBetAmount);
                                room.setPot(room.getPot() + amountToAdd);
                                player.setChips(player.getChips() - amountToAdd);
                                player.setCurrentBet(finalBetAmount);
                                player.setStatus("Checked");
                            } else if (!player.getStatus().equals("Folded") && !player.getStatus().equals("Out")) {
                                // קריטי: אם העליתי את ההימור, התור חייב לחזור לכולם כדי שישוו!
                                player.setStatus("Waiting");
                            }
                        }
                        layoutBetting.setVisibility(View.GONE);
                        layoutActionButtons.setVisibility(View.VISIBLE);
                        advanceGameRound(room);
                    }
                }
            });

            // --- כפתור All-In מהיר ---
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
                                    room.setCurrentBet(totalBet); // מקפיץ את רף החדר למקסימום שלי
                                }
                                room.setPot(room.getPot() + allInAmount);
                                player.setChips(0);
                                player.setCurrentBet(totalBet);
                                player.setStatus("Checked");
                            } else if (!player.getStatus().equals("Folded") && !player.getStatus().equals("Out")) {
                                player.setStatus("Waiting"); // מחזיר תור לכולם
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

    // ====================================================================
    // אלגוריתם קידום המשחק - מחליט אם להעביר תור או לחשוף קלפי קהילה
    // ====================================================================
    private void advanceGameRound(GameRoom room) {
        boolean isRoundComplete = true;
        // בודק אם כל השחקנים החיים סיימו להשוות (כלומר הסטטוס שלהם הוא Checked)
        for (User player : room.getPlayers()) {
            if (!player.getStatus().equals("Checked") && !player.getStatus().equals("Folded") && !player.getStatus().equals("Out")) {
                isRoundComplete = false;
                break;
            }
        }

        if (!isRoundComplete) {
            // עדיין חסרה תגובה ממישהו - מעבירים תור
            room.setTurnIndex(getNextActivePlayerIndex(room));
            roomRef.setValue(room);
        } else {
            // כולם סיימו את סבב ההימורים! מתקדמים לשלב הבא.
            if (room.getGameState().equalsIgnoreCase("River")) {
                handleShowdown(room); // אם סיימנו ריבר, חושפים קלפים
            } else {
                // שולפים את החבילה ואת קלפי הקהילה
                ArrayList<Card> deck = room.getDeck();
                ArrayList<Card> communityCards = room.getCommunityCards();
                if (communityCards == null) communityCards = new ArrayList<>();

                // חלוקת קלפים לשולחן בהתאם לשלב הנוכחי
                if (room.getGameState().equalsIgnoreCase("PreFlop")) {
                    for (int i = 0; i < 3; i++) { // בפלופ שולפים 3 קלפים
                        if (deck != null && !deck.isEmpty()) communityCards.add(deck.remove(0));
                    }
                    room.setGameState("Flop");
                } else if (room.getGameState().equalsIgnoreCase("Flop")) {
                    if (deck != null && !deck.isEmpty()) communityCards.add(deck.remove(0)); // בטרן שולפים 1
                    room.setGameState("Turn");
                } else if (room.getGameState().equalsIgnoreCase("Turn")) {
                    if (deck != null && !deck.isEmpty()) communityCards.add(deck.remove(0)); // בריבר שולפים 1
                    room.setGameState("River");
                }
                room.setCommunityCards(communityCards);

                // בודקים אם יש מצב של All-In שמצריך הריצה אוטומטית של שאר הקלפים
                int activePlayers = 0;
                int playersWithChipsCount = 0;
                for (User player : room.getPlayers()) {
                    if (!player.getStatus().equals("Folded") && !player.getStatus().equals("Out")) {
                        activePlayers++;
                        if (player.getChips() > 0) playersWithChipsCount++;
                    }
                }

                if (activePlayers >= 2 && playersWithChipsCount <= 1) {
                    // מישהו באול-אין מול שחקן בלי כסף: מריצים את המשחק אוטומטית
                    roomRef.setValue(room);
                    new Handler().postDelayed(() -> {
                        roomRef.get().addOnCompleteListener(task -> {
                            if (task.isSuccessful() && task.getResult().exists()) {
                                GameRoom currentRoom = task.getResult().getValue(GameRoom.class);
                                if (currentRoom != null) {
                                    advanceGameRound(currentRoom); // קריאה רקורסיבית
                                }
                            }
                        });
                    }, 1500); // משהים שניה וחצי כדי שיוכלו לראות את הקלפים
                } else {
                    // סיבוב רגיל - מאפסים את הסטטוסים לקראת סבב הימורים חדש
                    for (User player : room.getPlayers()) {
                        if (!player.getStatus().equals("Folded") && !player.getStatus().equals("Out")) {
                            player.setStatus("Waiting");
                        }
                        player.setCurrentBet(0);
                    }
                    room.setCurrentBet(0);
                    // התור חוזר לשחקן הראשון הפעיל אחרי הדילר
                    room.setTurnIndex(getFirstActivePlayerIndex(room));
                    roomRef.setValue(room); // מעדכנים את החדר בשרת
                }
            }
        }
    }

    // ====================================================================
    // Showdown - חשיפת קלפים, הכרזת מנצחים וחלוקת הקופה
    // ====================================================================
    private void handleShowdown(GameRoom room) {
        if (room.getPlayers() == null) return;

        // 1. שולחים את הידיים של כל השחקנים למעריך (HandEvaluator) כדי לקבל ציון
        HashMap<String, Integer> playerScores = new HashMap<>();
        for (User u : room.getPlayers()) {
            if (!u.getStatus().equals("Folded")) {
                ArrayList<Card> sevenCards = new ArrayList<>();
                if (u.getHand() != null) sevenCards.addAll(u.getHand());
                if (room.getCommunityCards() != null) sevenCards.addAll(room.getCommunityCards());
                playerScores.put(u.getUid(), HandEvaluator.evaluateHand(sevenCards));
            } else {
                playerScores.put(u.getUid(), 0); // מי שפרש מקבל אפס מאופס
            }
        }

        // 2. מסדרים את השחקנים לפי סכום ההימור שלהם (קריטי כדי לחשב Side Pots כשאנשים עושים All in בסכומים שונים)
        ArrayList<User> sortedPlayers = new ArrayList<>(room.getPlayers());
        Collections.sort(sortedPlayers, (u1, u2) -> u1.getCurrentBet() - u2.getCurrentBet());

        // 3. מציאת הציון הגבוה ביותר והשחקנים שהשיגו אותו
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

        // 4. אלגוריתם חלוקת הקופה בקומות (כדי ששחקן שהימר קצת לא ייקח את כל הקופה הענקית)
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
                    int splitAmount = layerPot / layerWinners.size(); // מחלקים את השכבה שווה בשווה בין הזוכים
                    for (User w : layerWinners) w.setChips(w.getChips() + splitAmount);
                }
                previousInvested = currentInvested;
            }
        }

        // 🌟 סנכרון הבנק הראשי מול Firebase (קריטי לרווחים ארוכי טווח) 🌟
        // רק המארח (שחקן 0) מחשב את זה כדי שלא כל המכשירים יוסיפו כסף בו זמנית
        if (room.getPlayers().size() > 0 && myUid.equals(room.getPlayers().get(0).getUid())) {
            DatabaseReference usersRef = FirebaseDatabase.getInstance().getReference("Users");

            for (User u : room.getPlayers()) {
                int roundProfitOrLoss = u.getChips() - u.getChipsBeforeRound();
                usersRef.child(u.getUid()).child("chips").get().addOnSuccessListener(snapshot -> {
                    if (snapshot.exists()) {
                        int realBank = snapshot.getValue(Integer.class);
                        usersRef.child(u.getUid()).child("chips").setValue(realBank + roundProfitOrLoss);
                    }
                });
            }
        }

        // 5. בניית הודעת הניצחון שתוצג על המסך לכולם
        StringBuilder msg = new StringBuilder();
        if (winners.size() == 1) {
            msg.append("🏆 ").append(winners.get(0).getNickname()).append(" Wins! 🏆");
        } else {
            msg.append("🤝 Tie! ");
            for (User w : winners) msg.append(w.getNickname()).append(" ");
        }

        room.setWinnerName(msg.toString());
        room.setGameState("Showdown");
        roomRef.setValue(room); // השרת מתעדכן שהגענו לשואודאון - והמסכים יציירו את חלון הניצחון

        // משהים ל-4 שניות לתצוגה, ואז בודקים אם יש צורך בסיבוב נוסף או שהמשחק נגמר
        new Handler().postDelayed(() -> {
            roomRef.get().addOnCompleteListener(task -> {
                if (task.isSuccessful() && task.getResult().exists()) {
                    GameRoom currentRoomState = task.getResult().getValue(GameRoom.class);
                    // מוודאים שעוד לא התחילו משחק בלעדינו
                    if (currentRoomState != null && "Showdown".equals(currentRoomState.getGameState())) {
                        int alivePlayers = 0;
                        if(currentRoomState.getPlayers() != null) {
                            for(User p : currentRoomState.getPlayers()) {
                                if(p.getChips() > 0) alivePlayers++; // סופרים מי לא פשט רגל
                            }
                        }

                        if(alivePlayers > 1) {
                            // יש לפחות 2 שחקנים חיים - מאפסים את החדר לסיבוב הבא
                            resetRoomForNextRound(currentRoomState);
                            currentRoomState.setWinnerName("");
                            roomRef.setValue(currentRoomState);
                        } else {
                            // נשאר שחקן אחד בלבד עם כסף (או שכולם מתו). המשחק נגמר!
                            currentRoomState.setGameState("GameOver");
                            currentRoomState.setGameActive(false);
                            roomRef.setValue(currentRoomState); // שולחים סטטוס סיום לשרת
                        }
                    }
                }
            });
        }, 4000);
    }

    // ====================================================================
    // איפוס החדר לסיבוב הבא - הפעולה הזו מבוצעת אך ורק על ידי המארח!
    // ====================================================================
    private void resetRoomForNextRound(GameRoom room) {
        room.setGameState("PreFlop");
        if (room.getCommunityCards() != null) room.getCommunityCards().clear(); // מנקים שולחן

        for (User player : room.getPlayers()) {
            player.setChipsBeforeRound(player.getChips()); // שומרים נקודת ייחוס לחישוב הרווחים בסוף הסיבוב
            player.setCurrentBet(0);
            player.setHand(new ArrayList<>()); // אוספים קלפים מהיד

            if (player.getChips() <= 0) {
                player.setStatus("Out"); // מי שנגמר לו הכסף מודח מהסיבוב הזה
            } else {
                player.setStatus("Waiting"); // מי שחי חוזר למצב המתנה
            }
        }

        // --- חישוב עמדות כפתור (Dealer, SB, BB) ---
        room.setDealerIndex((room.getDealerIndex() + 1) % room.getPlayers().size());
        int smallBlindIndex, bigBlindIndex, dealerTurnIndex;

        // טיפול במקרה של 2 שחקנים (Heads Up)
        if(room.getPlayers().size() == 2) {
            smallBlindIndex = room.getDealerIndex();
            bigBlindIndex = (room.getDealerIndex() + 1) % room.getPlayers().size();
            dealerTurnIndex = smallBlindIndex; // הדילר הוא גם הסמול בליינד והוא מתחיל ראשון לפני הפלופ
        } else { // 3-4 שחקנים
            smallBlindIndex = (room.getDealerIndex() + 1) % room.getPlayers().size();
            bigBlindIndex = (room.getDealerIndex() + 2) % room.getPlayers().size();
            dealerTurnIndex = (bigBlindIndex + 1) % room.getPlayers().size(); // שחקן "Under the gun" מתחיל
        }

        int sbAmount = 100;
        int bbAmount = 200;

        // גביית הסמול בליינד
        User sbPlayer = room.getPlayers().get(smallBlindIndex);
        int actualSb = Math.min(sbAmount, sbPlayer.getChips()); // מגן ממינוס במקרה של שחקן עני
        sbPlayer.setChips(sbPlayer.getChips() - actualSb);
        sbPlayer.setCurrentBet(actualSb);

        // גביית הביג בליינד
        User bbPlayer = room.getPlayers().get(bigBlindIndex);
        int actualBb = Math.min(bbAmount, bbPlayer.getChips());
        bbPlayer.setChips(bbPlayer.getChips() - actualBb);
        bbPlayer.setCurrentBet(actualBb);

        // עדכון הקופה והגדרת מי שתורו לשחק עכשיו
        room.setPot(actualSb + actualBb);
        room.setCurrentBet(actualBb);
        room.setTurnIndex(dealerTurnIndex);

        // יצירת חבילת קלפים חדשה לגמרי, ערבוב וחלוקה
        Deck newDeck = new Deck();
        newDeck.shuffle();
        ArrayList<Card> deckList = new ArrayList<>();
        Card c;
        while ((c = newDeck.drawCard()) != null) deckList.add(c);
        room.setDeck(deckList);

        // חלוקת 2 קלפי כיס לכל שחקן חי (מורידים מהחבילה הכללית ומעבירים אליהם)
        for (User player : room.getPlayers()) {
            if(!player.getStatus().equals("Out")) {
                ArrayList<Card> newHand = new ArrayList<>();
                newHand.add(deckList.remove(0));
                newHand.add(deckList.remove(0));
                player.setHand(newHand);
            }
        }
    }

    // ====================================================================
    // פונקציות עזר - מציאת שחקנים וניהול הבר-החלקתי (SeekBar)
    // ====================================================================

    // מוצא את המיקום ברשימה של השחקן הבא שעדיין חי ולא קיפל קלפים
    private int getNextActivePlayerIndex(GameRoom room) {
        int nextIndex = room.getTurnIndex() + 1;
        if (nextIndex >= room.getPlayers().size()) nextIndex = 0;
        while (room.getPlayers().get(nextIndex).getStatus().equals("Folded") || room.getPlayers().get(nextIndex).getStatus().equals("Out")) {
            nextIndex++;
            if (nextIndex >= room.getPlayers().size()) nextIndex = 0;
        }
        return nextIndex;
    }

    // מוצא את השחקן הפעיל הראשון בתחילת כל סיבוב (אחרי שהקלפים נפתחו)
    private int getFirstActivePlayerIndex(GameRoom room) {
        int firstPlayer = 0;
        while (firstPlayer < room.getPlayers().size() && (room.getPlayers().get(firstPlayer).getStatus().equals("Folded") || room.getPlayers().get(firstPlayer).getStatus().equals("Out"))) {
            firstPlayer++;
        }
        return firstPlayer;
    }

    // מעדכן את תצוגת הטקסט מעל בר-הגרירה כשמעלים הימור
    @Override
    public void onProgressChanged(SeekBar seekBar, int i, boolean b) {
        sbBetAmount.setProgress(i);
        tvBetAmount.setText(String.valueOf(i));
    }

    @Override public void onStartTrackingTouch(SeekBar seekBar) {}
    @Override public void onStopTrackingTouch(SeekBar seekBar) {}
}