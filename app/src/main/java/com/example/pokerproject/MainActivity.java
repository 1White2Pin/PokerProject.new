package com.example.pokerproject;

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

public class MainActivity extends AppCompatActivity implements View.OnClickListener, SeekBar.OnSeekBarChangeListener {

    PokerGameView pokerGameView;
    Button btnFold, btnCheck, btnRaise;
    TextView tvPotSize;

    DatabaseReference mDatabase;
    DatabaseReference roomRef;
    String roomId;
    String myUid;
    LinearLayout layoutActionButtons, layoutBetting;
    Button btnCancelBet, btnConfirmBet,btnAllIn;
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

                        // ניהול תורות (הדלקה/כיבוי כפתורים)
                        if (room.getPlayers() != null && !room.getPlayers().isEmpty()) {
                            int currentTurnIndex = room.getTurnIndex();

                            if (currentTurnIndex >= 0 && currentTurnIndex < room.getPlayers().size()) {
                                User playerTurn = room.getPlayers().get(currentTurnIndex);
                                boolean isMyTurn = playerTurn.getUid().equals(myUid);

                                boolean amIFolded = false;
                                for(User p : room.getPlayers()) {
                                    if(p.getUid().equals(myUid) && p.getStatus().equals("Folded")) {
                                        amIFolded = true;
                                        break;
                                    }
                                }

                                btnCheck.setEnabled(isMyTurn && !amIFolded);
                                btnFold.setEnabled(isMyTurn && !amIFolded);
                                btnRaise.setEnabled(isMyTurn && !amIFolded);
                                if (room.getCurrentBet() > 0) {
                                    btnCheck.setText("Call");
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
                if(task.isSuccessful() && task.getResult().exists()) {
                    GameRoom room = task.getResult().getValue(GameRoom.class);

                    if(room != null && room.getPlayers() != null) {
                        for(User player : room.getPlayers()) {
                            if(player.getUid().equals(myUid)) {
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

                        if(!isRoundComplete) {
                            // שימוש בפונקציית העזר!
                            room.setTurnIndex(getNextActivePlayerIndex(room));
                        } else {
                            // הסיבוב הסתיים: פותחים קלפים
                            ArrayList<Card> deck = room.getDeck();
                            ArrayList<Card> communityCards = room.getCommunityCards();
                            if (communityCards == null) communityCards = new ArrayList<>();

                            if (room.getGameState().equalsIgnoreCase("PreFlop")) {
                                for(int i = 0; i < 3; i++) {
                                    if(deck != null && !deck.isEmpty()) communityCards.add(deck.remove(0));
                                }
                                room.setGameState("Flop");
                            } else if (room.getGameState().equalsIgnoreCase("Flop")) {
                                if(deck != null && !deck.isEmpty()) communityCards.add(deck.remove(0));
                                room.setGameState("Turn");
                            } else if (room.getGameState().equalsIgnoreCase("Turn")) {
                                if(deck != null && !deck.isEmpty()) communityCards.add(deck.remove(0));
                                room.setGameState("River");
                            } else if (room.getGameState().equalsIgnoreCase("River")) {
                                Toast.makeText(this, "Showdown!", Toast.LENGTH_SHORT).show();
                            }
                            room.setCommunityCards(communityCards);

                            // איפוס שחקנים
                            for (User player : room.getPlayers()) {
                                if (!player.getStatus().equals("Folded")) {
                                    player.setStatus("Waiting");
                                }
                            }
                            // איפוס התור לשחקן הפעיל הראשון (הפונקציה השנייה!)
                            room.setTurnIndex(getFirstActivePlayerIndex(room));
                            room.setCurrentBet(0);
                        }
                        roomRef.setValue(room);
                    }
                }
            });

        } else if (view.getId() == R.id.btnCheck) {
            roomRef.get().addOnCompleteListener(task -> {
                if (task.isSuccessful() && task.getResult().exists()) {
                    GameRoom room = task.getResult().getValue(GameRoom.class);

                    if (room != null && room.getPlayers() != null) {
                        // הלולאה המעודכנת בתוך btnCheck
                        for (User player : room.getPlayers()) {
                            if (player.getUid().equals(myUid)) {

                                // --- אם מישהו העלה הימור, מורידים צ'יפים מהשחקן ומוסיפים לקופה ---
                                if (room.getCurrentBet() > 0) {
                                    player.setChips(player.getChips() - room.getCurrentBet());
                                    room.setPot(room.getPot() + room.getCurrentBet());
                                    Toast.makeText(this, "Called " + room.getCurrentBet(), Toast.LENGTH_SHORT).show();
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
                            // שימוש בפונקציית העזר!
                            room.setTurnIndex(getNextActivePlayerIndex(room));
                        } else {
                            // הסיבוב הסתיים: פותחים קלפים
                            ArrayList<Card> deck = room.getDeck();
                            ArrayList<Card> communityCards = room.getCommunityCards();
                            if (communityCards == null) communityCards = new ArrayList<>();

                            if (room.getGameState().equalsIgnoreCase("PreFlop")) {
                                for(int i = 0; i < 3; i++) {
                                    if(deck != null && !deck.isEmpty()) communityCards.add(deck.remove(0));
                                }
                                room.setGameState("Flop");
                            } else if (room.getGameState().equalsIgnoreCase("Flop")) {
                                if(deck != null && !deck.isEmpty()) communityCards.add(deck.remove(0));
                                room.setGameState("Turn");
                            } else if (room.getGameState().equalsIgnoreCase("Turn")) {
                                if(deck != null && !deck.isEmpty()) communityCards.add(deck.remove(0));
                                room.setGameState("River");
                            } else if (room.getGameState().equalsIgnoreCase("River")) {
                                Toast.makeText(this, "Showdown!", Toast.LENGTH_SHORT).show();
                            }
                            room.setCommunityCards(communityCards);

                            for (User player : room.getPlayers()) {
                                if (!player.getStatus().equals("Folded")) {
                                    player.setStatus("Waiting");
                                }
                            }
                            room.setCurrentBet(0);
                            // איפוס התור לשחקן הפעיל הראשון
                            room.setTurnIndex(getFirstActivePlayerIndex(room));
                        }
                        roomRef.setValue(room);
                    }
                }
            });

        }
        else if (view.getId() == R.id.btnRaise) {
            // במקום סתם לפתוח את הפאנל, נבדוק קודם כמה כסף יש לשחקן
            roomRef.get().addOnCompleteListener(task -> {
                if(task.isSuccessful() && task.getResult().exists()) {
                    GameRoom room = task.getResult().getValue(GameRoom.class);

                    if (room != null && room.getPlayers() != null) {
                        for(User player : room.getPlayers()) {
                            if (player.getUid().equals(myUid)) {
                                // 1. מגדירים את המקסימום של הסליידר לפי הצ'יפים של השחקן!
                                sbBetAmount.setMax(player.getChips());

                                // 2. מאפסים את הסליידר ל-0 כדי שיתחיל מהתחלה
                                sbBetAmount.setProgress(0);
                                tvBetAmount.setText("0");
                                break;
                            }
                        }

                        // 3. עכשיו, כשהסליידר מוכן, נציג את פאנל ההימורים
                        layoutActionButtons.setVisibility(View.GONE);
                        layoutBetting.setVisibility(View.VISIBLE);
                    }
                }
            });
        }
        else if(view.getId()==R.id.btnCancelBet) {
            layoutActionButtons.setVisibility(View.VISIBLE);
            layoutBetting.setVisibility(View.GONE);
        }
        else if(view.getId()==R.id.btnConfirmBet)
        {
            int finalBetAmount = sbBetAmount.getProgress();

            if (finalBetAmount <= 0) {
                Toast.makeText(this, "Bet must be greater than 0", Toast.LENGTH_SHORT).show();
                return;
            }

            roomRef.get().addOnCompleteListener(task -> {
                if(task.isSuccessful() && task.getResult().exists()) {
                    GameRoom room = task.getResult().getValue(GameRoom.class);

                    if (room != null && room.getPlayers() != null) {
                        for(User player : room.getPlayers()) {
                            if (player.getUid().equals(myUid)) {
                                room.setCurrentBet(finalBetAmount);
                                room.setPot(room.getPot() + finalBetAmount);
                                player.setChips(player.getChips() - finalBetAmount);
                                player.setStatus("Checked");
                            }
                            else if (!player.getStatus().equals("Folded")) {
                                player.setStatus("Waiting");
                            }
                        }

                        // העברת תור לשחקן הבא באמצעות פונקציית העזר!
                        room.setTurnIndex(getNextActivePlayerIndex(room));
                        roomRef.setValue(room);

                        layoutBetting.setVisibility(View.GONE);
                        layoutActionButtons.setVisibility(View.VISIBLE);
                    }
                }
            });
        }else if (view.getId() == R.id.btnAllIn) {

            roomRef.get().addOnCompleteListener(task -> {
                if(task.isSuccessful() && task.getResult().exists()) {
                    GameRoom room = task.getResult().getValue(GameRoom.class);

                    if (room != null && room.getPlayers() != null) {
                        for(User player : room.getPlayers()) {
                            if (player.getUid().equals(myUid)) {

                                int allInAmount = player.getChips();

                                room.setCurrentBet(allInAmount);
                                room.setPot(room.getPot() + allInAmount);
                                player.setChips(0);
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
    // פונקציית עזר: מוצאת את השחקן הבא בתור שלא פרש
    private int getNextActivePlayerIndex(GameRoom room) {
        int nextIndex = room.getTurnIndex() + 1;
        if (nextIndex >= room.getPlayers().size()) {
            nextIndex = 0;
        }
        while (room.getPlayers().get(nextIndex).getStatus().equals("Folded")) {
            nextIndex++;
            if (nextIndex >= room.getPlayers().size()) {
                nextIndex = 0;
            }
        }
        return nextIndex;
    }

    // פונקציית עזר: מוצאת את השחקן הראשון שלא פרש (בשביל תחילת סיבוב חדש)
    private int getFirstActivePlayerIndex(GameRoom room) {
        int firstPlayer = 0;
        while (firstPlayer < room.getPlayers().size() && room.getPlayers().get(firstPlayer).getStatus().equals("Folded")) {
            firstPlayer++;
        }
        return firstPlayer;
    }

    @Override
    public void onProgressChanged(SeekBar seekBar, int i, boolean b) {
        sbBetAmount.setProgress(i);
        tvBetAmount.setText(String.valueOf(i));
    }

    @Override
    public void onStartTrackingTouch(SeekBar seekBar) {

    }

    @Override
    public void onStopTrackingTouch(SeekBar seekBar) {

    }
}