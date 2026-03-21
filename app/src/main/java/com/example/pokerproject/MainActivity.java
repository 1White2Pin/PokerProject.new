package com.example.pokerproject;

import android.app.AlertDialog;
import android.os.Bundle;
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

public class MainActivity extends AppCompatActivity implements View.OnClickListener, SeekBar.OnSeekBarChangeListener {

    PokerGameView pokerGameView;
    Button btnFold, btnCheck, btnRaise;
    TextView tvPotSize;

    DatabaseReference mDatabase;
    DatabaseReference roomRef;
    String roomId;
    String myUid;
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

        // 1. חיבור לרכיבים
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

        btnFold.setOnClickListener(this);
        btnCheck.setOnClickListener(this);
        btnRaise.setOnClickListener(this);
        btnCancelBet.setOnClickListener(this);
        btnConfirmBet.setOnClickListener(this);
        btnAllIn.setOnClickListener(this);

        sbBetAmount.setOnSeekBarChangeListener(this);

        // 2. קבלת משתמש נוכחי
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user != null) {
            myUid = user.getUid();
        }

        // 3. קבלת ה-ID ובדיקת הגנה
        if (getIntent().hasExtra("roomId")) {
            roomId = getIntent().getStringExtra("roomId");
        }

        if (roomId == null || roomId.isEmpty()) {
            Toast.makeText(this, "Error: No Room ID found!", Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        // 4. חיבור לפיירבייס
        mDatabase = FirebaseDatabase.getInstance().getReference();
        roomRef = mDatabase.child("Rooms").child(roomId);

        roomRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (snapshot.exists()) {
                    GameRoom room = snapshot.getValue(GameRoom.class);

                    if (room != null) {
                        tvPotSize.setText("Pot: " + room.getPot());
                        pokerGameView.updateGame(room, myUid);
                        // בודק שיש לפחות 2 שחקנים, ושחבילת הקלפים ריקה או לא קיימת, ושאני המארח
                        if (room.getPlayers().size() >= 2 && (room.getDeck() == null || room.getDeck().isEmpty()) && myUid.equals(room.getPlayers().get(0).getUid())) {
                            resetRoomForNextRound(room);
                            roomRef.setValue(room);
                            return;
                        }

                        // ניהול תורות (הדלקה/כיבוי כפתורים)
                        if (room.getPlayers() != null && !room.getPlayers().isEmpty()) {
                            int currentTurnIndex = room.getTurnIndex();

                            if (currentTurnIndex >= 0 && currentTurnIndex < room.getPlayers().size()) {
                                User playerTurn = room.getPlayers().get(currentTurnIndex);
                                boolean isMyTurn = playerTurn.getUid().equals(myUid);

                                boolean amIFolded = false;
                                for (User p : room.getPlayers()) {
                                    if (p.getUid().equals(myUid) && p.getStatus().equals("Folded")) {
                                        amIFolded = true;
                                        break;
                                    }
                                }

                                btnCheck.setEnabled(isMyTurn && !amIFolded);
                                btnFold.setEnabled(isMyTurn && !amIFolded);
                                btnRaise.setEnabled(isMyTurn && !amIFolded);

                                if (room.getCurrentBet() > 0) {
                                    if (room.getCurrentBet() >= playerTurn.getChips()) {
                                        btnCheck.setText("All In");
                                    } else {
                                        btnCheck.setText("Call");
                                    }
                                } else {
                                    btnCheck.setText("Check");
                                }
                            }
                        }
                    }
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Toast.makeText(MainActivity.this, "Database Error", Toast.LENGTH_SHORT).show();
            }
        });
    }

    @Override
    public void onClick(View view) {
        if (view.getId() == R.id.btnFold) {
            roomRef.get().addOnCompleteListener(task -> {
                if (task.isSuccessful() && task.getResult().exists()) {
                    GameRoom room = task.getResult().getValue(GameRoom.class);

                    if (room != null && room.getPlayers() != null) {
                        for (User player : room.getPlayers()) {
                            if (player.getUid().equals(myUid)) {
                                player.setStatus("Folded");
                                break;
                            }
                        }

                        boolean isRoundComplete = true;
                        for (User player : room.getPlayers()) {
                            if (!player.getStatus().equals("Checked") && !player.getStatus().equals("Folded")) {
                                isRoundComplete = false;
                                break;
                            }
                        }

                        if (!isRoundComplete) {
                            room.setTurnIndex(getNextActivePlayerIndex(room));
                        } else {
                            if (room.getGameState().equalsIgnoreCase("River")) {
                                handleShowdown(room);
                            } else {
                                ArrayList<Card> deck = room.getDeck();
                                ArrayList<Card> communityCards = room.getCommunityCards();
                                if (communityCards == null) communityCards = new ArrayList<>();

                                if (room.getGameState().equalsIgnoreCase("PreFlop")) {
                                    for (int i = 0; i < 3; i++) {
                                        if (deck != null && !deck.isEmpty()) communityCards.add(deck.remove(0));
                                    }
                                    room.setGameState("Flop");
                                } else if (room.getGameState().equalsIgnoreCase("Flop")) {
                                    if (deck != null && !deck.isEmpty()) communityCards.add(deck.remove(0));
                                    room.setGameState("Turn");
                                } else if (room.getGameState().equalsIgnoreCase("Turn")) {
                                    if (deck != null && !deck.isEmpty()) communityCards.add(deck.remove(0));
                                    room.setGameState("River");
                                }
                                room.setCommunityCards(communityCards);

                                for (User player : room.getPlayers()) {
                                    if (!player.getStatus().equals("Folded")) {
                                        player.setStatus("Waiting");
                                    }
                                }
                                room.setTurnIndex(getFirstActivePlayerIndex(room));
                                room.setCurrentBet(0);
                                roomRef.setValue(room);
                            }
                        }
                        if (!room.getGameState().equalsIgnoreCase("River") || !isRoundComplete) {
                            roomRef.setValue(room);
                        }
                    }
                }
            });

        } else if (view.getId() == R.id.btnCheck) {
            roomRef.get().addOnCompleteListener(task -> {
                if (task.isSuccessful() && task.getResult().exists()) {
                    GameRoom room = task.getResult().getValue(GameRoom.class);

                    if (room != null && room.getPlayers() != null) {
                        for (User player : room.getPlayers()) {
                            if (player.getUid().equals(myUid)) {
                                    if (room.getCurrentBet() > 0) {
                                        int amountToCall = room.getCurrentBet() - player.getCurrentBet();


                                        if (amountToCall >0) {
                                            if(amountToCall >= player.getChips())
                                                amountToCall = player.getChips();

                                        }

                                        player.setChips(player.getChips() - amountToCall);
                                        room.setPot(room.getPot() + amountToCall);
                                        player.setCurrentBet(player.getCurrentBet() + amountToCall);

                                        Toast.makeText(MainActivity.this, "Called " + amountToCall, Toast.LENGTH_SHORT).show();
                                    }
                                player.setStatus("Checked");
                                break;
                            }
                        }

                        boolean isRoundComplete = true;
                        for (User player : room.getPlayers()) {
                            if (!player.getStatus().equals("Checked") && !player.getStatus().equals("Folded")) {
                                isRoundComplete = false;
                                break;
                            }
                        }

                        if (!isRoundComplete) {
                            room.setTurnIndex(getNextActivePlayerIndex(room));
                        } else {
                            if (room.getGameState().equalsIgnoreCase("River")) {
                                handleShowdown(room);
                            } else {
                                ArrayList<Card> deck = room.getDeck();
                                ArrayList<Card> communityCards = room.getCommunityCards();
                                if (communityCards == null) communityCards = new ArrayList<>();

                                if (room.getGameState().equalsIgnoreCase("PreFlop")) {
                                    for (int i = 0; i < 3; i++) {
                                        if (deck != null && !deck.isEmpty()) communityCards.add(deck.remove(0));
                                    }
                                    room.setGameState("Flop");
                                } else if (room.getGameState().equalsIgnoreCase("Flop")) {
                                    if (deck != null && !deck.isEmpty()) communityCards.add(deck.remove(0));
                                    room.setGameState("Turn");
                                } else if (room.getGameState().equalsIgnoreCase("Turn")) {
                                    if (deck != null && !deck.isEmpty()) communityCards.add(deck.remove(0));
                                    room.setGameState("River");
                                }
                                room.setCommunityCards(communityCards);

                                for (User player : room.getPlayers()) {
                                    if (!player.getStatus().equals("Folded")) {
                                        player.setStatus("Waiting");
                                    }
                                }
                                room.setCurrentBet(0);
                                room.setTurnIndex(getFirstActivePlayerIndex(room));
                                roomRef.setValue(room);
                            }
                        }
                        if (!room.getGameState().equalsIgnoreCase("River") || !isRoundComplete) {
                            roomRef.setValue(room);
                        }
                    }
                }
            });

        } else if (view.getId() == R.id.btnRaise) {
            roomRef.get().addOnCompleteListener(task -> {
                if (task.isSuccessful() && task.getResult().exists()) {
                    GameRoom room = task.getResult().getValue(GameRoom.class);
                    if (room != null && room.getPlayers() != null) {
                        for (User player : room.getPlayers()) {
                            if (player.getUid().equals(myUid)) {
                                sbBetAmount.setMax(player.getChips());
                                sbBetAmount.setProgress(0);
                                tvBetAmount.setText("0");
                                break;
                            }
                        }
                        layoutActionButtons.setVisibility(View.GONE);
                        layoutBetting.setVisibility(View.VISIBLE);
                    }
                }
            });
        } else if (view.getId() == R.id.btnCancelBet) {
            layoutActionButtons.setVisibility(View.VISIBLE);
            layoutBetting.setVisibility(View.GONE);
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

                                room.setCurrentBet(finalBetAmount);
                                room.setPot(room.getPot() + amountToAdd);
                                player.setChips(player.getChips() - amountToAdd);
                                player.setCurrentBet(finalBetAmount); // ההימור שלו עכשיו עומד על הסכום שהוא בחר
                                player.setStatus("Checked");
                            } else if (!player.getStatus().equals("Folded")) {
                                player.setStatus("Waiting");
                            }
                        }

                        room.setTurnIndex(getNextActivePlayerIndex(room));
                        roomRef.setValue(room);

                        layoutBetting.setVisibility(View.GONE);
                        layoutActionButtons.setVisibility(View.VISIBLE);
                    }
                }
            });
        } else if (view.getId() == R.id.btnAllIn) {
            roomRef.get().addOnCompleteListener(task -> {
                if (task.isSuccessful() && task.getResult().exists()) {
                    GameRoom room = task.getResult().getValue(GameRoom.class);

                    if (room != null && room.getPlayers() != null) {
                        for (User player : room.getPlayers()) {
                            if (player.getUid().equals(myUid)) {
                                int allInAmount = player.getChips();

                                if (allInAmount > room.getCurrentBet()) {
                                    room.setCurrentBet(allInAmount);
                                }

                                room.setPot(room.getPot() + allInAmount);
                                player.setChips(0);
                                player.setCurrentBet(player.getCurrentBet() + allInAmount);
                                player.setStatus("Checked");

                            } else if (!player.getStatus().equals("Folded")) {
                                player.setStatus("Waiting");
                            }
                        }

                        room.setTurnIndex(getNextActivePlayerIndex(room));
                        roomRef.setValue(room);

                        layoutBetting.setVisibility(View.GONE);
                        layoutActionButtons.setVisibility(View.VISIBLE);
                    }
                }
            });
        }
    }

    // ══════════════════════════════════════════════════════
    //  SHOWDOWN — חישוב מנצחים והצגה על הקנבס
    // ══════════════════════════════════════════════════════

    private void handleShowdown(GameRoom room) {
        if (room.getPlayers() == null) return;

        // 1. חישוב ציונים
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

        // 2. מיון לפי השקעה לחישוב side pots
        ArrayList<User> sortedPlayers = new ArrayList<>(room.getPlayers());
        Collections.sort(sortedPlayers, (u1, u2) -> u1.getCurrentBet() - u2.getCurrentBet());

        // 3. מציאת המנצח הראשי
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

        // 4. בניית שכבות הקופה (Side Pots) וחלוקת הכסף
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
                    int splitAmount = layerPot / layerWinners.size();
                    for (User w : layerWinners) w.setChips(w.getChips() + splitAmount);
                }
                previousInvested = currentInvested;
            }
        }

        // 5. בניית הודעת המנצח (שתופיע על הקנבס!)
        StringBuilder msg = new StringBuilder();
        if (winners.size() == 1) {
            msg.append("🏆 ").append(winners.get(0).getNickname()).append(" Wins! 🏆");
        } else {
            msg.append("🤝 Tie! ");
            for (User w : winners) msg.append(w.getNickname()).append(" ");
        }

        // מעדכנים את החדר לסטטוס ניצחון ושומרים בפיירבייס כדי שכולם יראו את האנימציה
        room.setWinnerName(msg.toString());
        room.setGameState("Showdown");
        roomRef.setValue(room);

        // 6. טיימר של 4 שניות: מחכים שהשחקנים יראו את הניצחון, ואז מאפסים לסיבוב הבא!
        new android.os.Handler().postDelayed(() -> {
            roomRef.get().addOnCompleteListener(task -> {
                if (task.isSuccessful() && task.getResult().exists()) {
                    GameRoom currentRoomState = task.getResult().getValue(GameRoom.class);
                    // מוודאים שאנחנו עדיין בשלב הניצחון לפני האיפוס
                    if (currentRoomState != null && "Showdown".equals(currentRoomState.getGameState())) {
                        resetRoomForNextRound(currentRoomState);
                        currentRoomState.setWinnerName(""); // מוחקים את הודעת הניצחון
                        roomRef.setValue(currentRoomState);
                    }
                }
            });
        }, 4000); // 4000 מילישניות = 4 שניות תצוגה
    }

    // ══════════════════════════════════════════════════════
    //  איפוס החדר לסיבוב הבא
    // ══════════════════════════════════════════════════════

    private void resetRoomForNextRound(GameRoom room) {
        room.setGameState("PreFlop");
        if (room.getCommunityCards() != null) room.getCommunityCards().clear();

        for (User player : room.getPlayers()) {
            // 1. איפוס מה שמשותף לכולם:
            player.setCurrentBet(0);
            player.setHand(new ArrayList<>());

            // 2. קביעת הסטטוס:
            if (player.getChips() <= 0) {
                player.setStatus("Out");
            } else {
                player.setStatus("Waiting");
            }
        }
        room.setDealerIndex((room.getDealerIndex() + 1) % room.getPlayers().size());
        int smallBlindIndex, bigBlindIndex, DealerIndex;
        if(room.getPlayers().size() == 2) {
            smallBlindIndex = room.getDealerIndex();
            bigBlindIndex = (room.getDealerIndex() + 1) % room.getPlayers().size();
            DealerIndex = smallBlindIndex;
        }
        else
        {
            smallBlindIndex = (room.getDealerIndex() + 1) % room.getPlayers().size();
            bigBlindIndex = (room.getDealerIndex() + 2) % room.getPlayers().size();
            DealerIndex = (bigBlindIndex + 1) % room.getPlayers().size();
        }
        int sbAmount = 100;
        int bbAmount = 200;

        User sbPlayer = room.getPlayers().get(smallBlindIndex);
        int actualSb = Math.min(sbAmount, sbPlayer.getChips());
        sbPlayer.setChips(sbPlayer.getChips() - actualSb);
        sbPlayer.setCurrentBet(actualSb);

        User bbPlayer = room.getPlayers().get(bigBlindIndex);
        int actualBb = Math.min(bbAmount, bbPlayer.getChips());
        bbPlayer.setChips(bbPlayer.getChips() - actualBb);
        bbPlayer.setCurrentBet(actualBb);

        room.setPot(actualSb + actualBb);
        room.setCurrentBet(actualBb);

        room.setTurnIndex(DealerIndex);






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
    //  פונקציות עזר
    // ══════════════════════════════════════════════════════

    private int getNextActivePlayerIndex(GameRoom room) {
        int nextIndex = room.getTurnIndex() + 1;
        if (nextIndex >= room.getPlayers().size()) nextIndex = 0;
        while (room.getPlayers().get(nextIndex).getStatus().equals("Folded") || room.getPlayers().get(nextIndex).getStatus().equals("Out")) {
            nextIndex++;
            if (nextIndex >= room.getPlayers().size()) nextIndex = 0;
        }
        return nextIndex;
    }

    private int getFirstActivePlayerIndex(GameRoom room) {
        int firstPlayer = 0;
        while (firstPlayer < room.getPlayers().size() && room.getPlayers().get(firstPlayer).getStatus().equals("Folded") ||  firstPlayer < room.getPlayers().size() && room.getPlayers().get(firstPlayer).getStatus().equals("Out")) {
            firstPlayer++;
        }
        return firstPlayer;
    }

    @Override
    public void onProgressChanged(SeekBar seekBar, int i, boolean b) {
        sbBetAmount.setProgress(i);
        tvBetAmount.setText(String.valueOf(i));
    }

    @Override public void onStartTrackingTouch(SeekBar seekBar) {}
    @Override public void onStopTrackingTouch(SeekBar seekBar) {}
}