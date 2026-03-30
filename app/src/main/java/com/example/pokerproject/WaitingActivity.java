package com.example.pokerproject;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.bumptech.glide.Glide;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;

public class WaitingActivity extends AppCompatActivity implements View.OnClickListener, ValueEventListener {

    private TextView tvRoomCode, tvChipsValue, tvWaitMessage;
    private Button btnStartGame, btnPlusChips, btnMinusChips;
    private LinearLayout adminPanel, guestMessage;
    private String roomId;
    private String myUid;

    private int currentChips = 500;
    private int maxAllowedChips = 10000;

    private boolean amIHost = false;
    private DatabaseReference roomRef;
    private CheckBox cbIsPrivate;

    // מערכים של הכיסאות
    private TextView[] tvPlayerNames = new TextView[4];
    private ImageView[] ivPlayers = new ImageView[4];
    private Button[] btnKicks = new Button[4];

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

        // 🌟 משימה 6: הגנת ניתוק פתאומי (אם המארח נופל, החדר נמחק)
        roomRef.get().addOnSuccessListener(snapshot -> {
            if (snapshot.exists()) {
                GameRoom room = snapshot.getValue(GameRoom.class);
                if (room != null && room.getHostId() != null && room.getHostId().equals(myUid)) {
                    roomRef.onDisconnect().removeValue();
                }
            }
        });

        tvRoomCode = findViewById(R.id.tvRoomCode);
        tvChipsValue = findViewById(R.id.tvChipsValue);
        tvWaitMessage = findViewById(R.id.tvWaitMessage);
        btnStartGame = findViewById(R.id.btnStartGame);
        btnPlusChips = findViewById(R.id.btnPlusChips);
        btnMinusChips = findViewById(R.id.btnMinusChips);
        adminPanel = findViewById(R.id.adminPanel);
        guestMessage = findViewById(R.id.guestMessage);
        cbIsPrivate = findViewById(R.id.cbIsPrivate);

        tvPlayerNames[0] = findViewById(R.id.tvPlayer1Name);
        tvPlayerNames[1] = findViewById(R.id.tvPlayer2Name);
        tvPlayerNames[2] = findViewById(R.id.tvPlayer3Name);
        tvPlayerNames[3] = findViewById(R.id.tvPlayer4Name);

        ivPlayers[0] = findViewById(R.id.ivPlayer1);
        ivPlayers[1] = findViewById(R.id.ivPlayer2);
        ivPlayers[2] = findViewById(R.id.ivPlayer3);
        ivPlayers[3] = findViewById(R.id.ivPlayer4);

        btnKicks[0] = findViewById(R.id.btnKick1);
        btnKicks[1] = findViewById(R.id.btnKick2);
        btnKicks[2] = findViewById(R.id.btnKick3);
        btnKicks[3] = findViewById(R.id.btnKick4);

        btnMinusChips.setOnClickListener(this);
        btnPlusChips.setOnClickListener(this);
        btnStartGame.setOnClickListener(this);

        tvRoomCode.setText(roomId);
        tvChipsValue.setText(String.valueOf(currentChips));
        updateRoom();

        cbIsPrivate.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (amIHost) {
                roomRef.child("private").setValue(isChecked);
            }
        });
    }

    @Override
    public void onClick(View view) {
        if( view.getId() == R.id.btnMinusChips) {
            if(currentChips > 100) {
                currentChips -= 100;
                tvChipsValue.setText(String.valueOf(currentChips));
            } else {
                Toast.makeText(this, "Minimum buy-in is 100", Toast.LENGTH_SHORT).show();
            }
        }

        if(view.getId() == R.id.btnPlusChips) {
            if(currentChips + 100 <= maxAllowedChips) {
                currentChips += 100;
                tvChipsValue.setText(String.valueOf(currentChips));
            } else {
                Toast.makeText(this, "Cannot increase further! The lowest player balance is " + maxAllowedChips, Toast.LENGTH_SHORT).show();
            }
        }

        if(view.getId() == R.id.btnStartGame) {
            roomRef.get().addOnSuccessListener(dataSnapshot -> {
                GameRoom room = dataSnapshot.getValue(GameRoom.class);

                if (room != null) {
                    if (room.getPlayers() != null) {
                        for (User player : room.getPlayers()) {
                            player.setChips(currentChips);
                        }
                    }

                    room.setStartingChips(currentChips);
                    room.setGameActive(true);
                    roomRef.setValue(room);
                }
            });
        }
    }

    public void updateRoom(){
        roomRef.addValueEventListener(this);
    }

    @Override
    public void onDataChange(@NonNull DataSnapshot snapshot) {
        // 🌟 משימה 6: אם החדר נמחק (המארח יצא), האורחים עפים ללובי
        if(!snapshot.exists()) {
            Toast.makeText(this, "The host has closed the room.", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        GameRoom room = snapshot.getValue(GameRoom.class);

        if (room != null) {
            if (room.getHostId() != null) {
                amIHost = room.getHostId().equals(myUid);
            }

            if (room.getPlayers() != null) {
                int minBankroll = Integer.MAX_VALUE;
                for (User p : room.getPlayers()) {
                    if (p.getChips() < minBankroll) {
                        minBankroll = p.getChips();
                    }
                }

                maxAllowedChips = minBankroll;

                if (currentChips > maxAllowedChips) {
                    currentChips = maxAllowedChips;
                    if(currentChips < 500) currentChips = 500;

                    if (amIHost) {
                        tvChipsValue.setText(String.valueOf(currentChips));
                        Toast.makeText(this, "Buy-in automatically adjusted to match the lowest player balance", Toast.LENGTH_SHORT).show();
                    }
                }
            }

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

            if(room.isGameActive()) {
                Intent intent = new Intent(WaitingActivity.this, MainActivity.class);
                intent.putExtra("roomId", roomId);
                roomRef.removeEventListener(this);
                startActivity(intent);
                finish();
            }

            if (!amIHost) {
                boolean amIStillIn = false;

                if (room.getPlayers() != null) {
                    for(User u : room.getPlayers()) {
                        if(u.getUid().equals(myUid)) {
                            amIStillIn = true;
                            break;
                        }
                    }
                }

                if(!amIStillIn) {
                    Toast.makeText(WaitingActivity.this, "You were kicked from the room", Toast.LENGTH_SHORT).show();
                    finish();
                    return;
                }
            }

            // 1. קודם כל מנקים את כל 4 הכיסאות (The Cleaner)
            for(int i = 0; i < 4; i++) {
                tvPlayerNames[i].setText("Empty");
                // אפשר לשים פה תמונת ברירת מחדל אם רוצים ivPlayers[i].setImageResource(...)
                btnKicks[i].setVisibility(View.GONE);
                btnKicks[i].setOnClickListener(null);
            }

            // 2. עכשיו מושיבים את השחקנים הקיימים (The Seater)
            if(room.getPlayers() != null) {
                for (int i = 0; i < room.getPlayers().size(); i++) {
                    if (i >= 4) break; // הגנה שלא נחרוג מ-4 כיסאות

                    User player = room.getPlayers().get(i);
                    tvPlayerNames[i].setText(player.getNickname());
                    if(player.getImageURL() != null && !player.getImageURL().isEmpty()) {
                        Glide.with(this).load(player.getImageURL()).into(ivPlayers[i]);
                    }

                    // 🌟 התיקון הקריטי ל-KICK!
                    // רק אם אני המארח, ורק אם השחקן הזה הוא **לא** אני - תראה את הכפתור
                    if(amIHost && !player.getUid().equals(myUid)) {
                        btnKicks[i].setVisibility(View.VISIBLE);
                        btnKicks[i].setOnClickListener(v -> kickPlayer(player));
                    } else {
                        btnKicks[i].setVisibility(View.GONE);
                        btnKicks[i].setOnClickListener(null);
                    }
                }
            }
        }
    }

    private void kickPlayer(User player) {
        roomRef.child("players").get().addOnCompleteListener(task -> {
            if (task.isSuccessful() && task.getResult() != null) {
                ArrayList<User> remainingPlayers = new ArrayList<>();
                DataSnapshot snapshot = task.getResult();
                for(DataSnapshot child : snapshot.getChildren()) {
                    User user = child.getValue(User.class);
                    if(user != null && !user.getUid().equals(player.getUid())) {
                        remainingPlayers.add(user);
                    }
                }
                roomRef.child("players").setValue(remainingPlayers);
            }
        });
    }

    @Override
    public void onCancelled(@NonNull DatabaseError error) {
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        if (roomRef != null) {
            if (amIHost) {
                roomRef.removeValue();
            } else if (myUid != null) {
                roomRef.child("players").get().addOnCompleteListener(task -> {
                    if (task.isSuccessful() && task.getResult().exists()) {
                        ArrayList<User> remainingPlayers = new ArrayList<>();
                        for (DataSnapshot child : task.getResult().getChildren()) {
                            User user = child.getValue(User.class);
                            if (user != null && !user.getUid().equals(myUid)) {
                                remainingPlayers.add(user);
                            }
                        }
                        roomRef.child("players").setValue(remainingPlayers);
                    }
                });
            }
        }
    }
}