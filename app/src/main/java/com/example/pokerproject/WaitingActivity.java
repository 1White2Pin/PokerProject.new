package com.example.pokerproject;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;

public class WaitingActivity extends AppCompatActivity implements View.OnClickListener, ValueEventListener {
    private RecyclerView rvPlayers;
    private TextView tvRoomCode, tvChipsValue, tvWaitMessage;
    private Button btnStartGame, btnPlusChips, btnMinusChips;
    private LinearLayout adminPanel, guestMessage;
    private String roomId;
    private String myUid;
    private int currentChips = 10000;
    private boolean amIHost = false;
    private DatabaseReference roomRef;
    private PlayerAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_waiting);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.waitingLayout), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        myUid = FirebaseAuth.getInstance().getCurrentUser().getUid();
        roomId = getIntent().getStringExtra("ROOM_ID");
        roomRef = FirebaseDatabase.getInstance().getReference("Rooms").child(roomId);

        tvRoomCode = findViewById(R.id.tvRoomCode);
        tvChipsValue = findViewById(R.id.tvChipsValue);
        tvWaitMessage = findViewById(R.id.tvWaitMessage);
        btnStartGame = findViewById(R.id.btnStartGame);
        btnPlusChips = findViewById(R.id.btnPlusChips);
        btnMinusChips = findViewById(R.id.btnMinusChips);
        adminPanel = findViewById(R.id.adminPanel);
        guestMessage = findViewById(R.id.guestMessage);

        btnMinusChips.setOnClickListener(this);
        btnPlusChips.setOnClickListener(this);
        btnStartGame.setOnClickListener(this);

        tvRoomCode.setText(roomId);
        tvChipsValue.setText(String.valueOf(currentChips));
        updateRoom();

        rvPlayers = findViewById(R.id.rvPlayers);
        rvPlayers.setHasFixedSize(true);
        rvPlayers.setLayoutManager(new LinearLayoutManager(this));
        adapter = new PlayerAdapter(myUid, new OnPlayerKickListener() {
            @Override
            public void onKick(User player) {
                roomRef.child("players").get().addOnCompleteListener(task -> {
                    ArrayList<User> remainingPlayers = new ArrayList<>();
                    DataSnapshot snapshot = task.getResult();
                    for(DataSnapshot child : snapshot.getChildren()) {
                        User user = child.getValue(User.class);
                        if(!user.getUid().equals(player.getUid())) {
                            remainingPlayers.add(user);
                        }
                    }
                    roomRef.child("players").setValue(remainingPlayers);
                });
            }
        });
        rvPlayers.setAdapter(adapter);
    }

    // פונקציית עזר ליצירת חפיסה מעורבבת
    private ArrayList<Card> createShuffledDeck() {
        ArrayList<Card> deck = new ArrayList<>();
        // מעבר על כל הצורות והמספרים
        for (Suit suit : Suit.values()) {
            for (Rank rank : Rank.values()) {
                deck.add(new Card(suit, rank));
            }
        }
        // ערבוב החפיסה
        java.util.Collections.shuffle(deck);
        return deck;
    }

    @Override
    public void onClick(View view) {
        if( view.getId() == R.id.btnMinusChips) {
            if(currentChips > 500) {
                currentChips -= 500;
                tvChipsValue.setText(String.valueOf(currentChips));
            } else {
                tvChipsValue.setText("500");
            }
        }
        if(view.getId() == R.id.btnPlusChips) {
            if(currentChips < 10000) {
                currentChips += 500;
                tvChipsValue.setText(String.valueOf(currentChips));
            } else {
                tvChipsValue.setText("10000");
            }
        }
        if(view.getId() == R.id.btnStartGame) {
            // 1. משיכת המצב העדכני של החדר
            roomRef.get().addOnSuccessListener(dataSnapshot -> {
                GameRoom room = dataSnapshot.getValue(GameRoom.class);

                if (room != null) {
                    // 2. יצירת חפיסה חדשה
                    ArrayList<Card> deck = createShuffledDeck();

                    // 3. חלוקת קלפים לכל שחקן
                    if (room.getPlayers() != null) {
                        for (User player : room.getPlayers()) {
                            ArrayList<Card> hand = new ArrayList<>();
                            hand.add(deck.remove(0));
                            hand.add(deck.remove(0));

                            player.setHand(hand);
                            player.setStatus("Active");
                        }
                    }

                    // 4. הגדרת המשתנים לתחילת המשחק
                    room.setGameActive(true);
                    room.setStartingChips(currentChips);
                    room.setDeck(deck);
                    room.setGameState("PreFlop");
                    room.setCommunityCards(new ArrayList<>());

                    // 5. שמירה של החדר המעודכן לפיירבייס
                    roomRef.setValue(room);
                }
            });
        }
    }

    // פונקציה שמפעילה את ההאזנה
    public void updateRoom(){
        roomRef.addValueEventListener(this);
    }

    @Override
    public void onDataChange(@NonNull DataSnapshot snapshot) {
        // 1. בדיקה אם החדר קיים
        if(!snapshot.exists()) {
            finish();
            return;
        }

        // 2. המרת הנתונים לאובייקט
        GameRoom room = snapshot.getValue(GameRoom.class);

        if (room != null) {
            // 3. עדכון משתנה המארח
            if (room.getHostId() != null) {
                amIHost = room.getHostId().equals(myUid);
            }

            // 4. עדכון רשימת השחקנים במסך
            if (room.getPlayers() != null) {
                adapter.updateList(room.getPlayers(), amIHost);
            }

            // 5. עדכון כפתורי הניהול
            if(amIHost) {
                adminPanel.setVisibility(View.VISIBLE);
                guestMessage.setVisibility(View.GONE);
                btnStartGame.setVisibility(View.VISIBLE);
            } else {
                adminPanel.setVisibility(View.GONE);
                guestMessage.setVisibility(View.VISIBLE);
                btnStartGame.setVisibility(View.GONE);
                tvChipsValue.setText(String.valueOf(room.getStartingChips()));
            }

            // 6. בדיקת תחילת משחק ומעבר מסך
            if(room.isGameActive()) {
                Intent intent = new Intent(WaitingActivity.this, MainActivity.class);
                intent.putExtra("roomId", roomId);

                // ניתוק ההאזנה כדי למנוע את פתיחת המסך מחדש בלחיצות בתוך המשחק
                roomRef.removeEventListener(this);

                startActivity(intent);
                finish();
            }
        }
    }

    @Override
    public void onCancelled(@NonNull DatabaseError error) {
    }
}