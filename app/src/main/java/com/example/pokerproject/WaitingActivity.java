package com.example.pokerproject;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

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

    private int currentChips = 500;
    private int maxAllowedChips = 10000;

    private boolean amIHost = false;
    private DatabaseReference roomRef;
    private PlayerAdapter adapter;
    private CheckBox cbIsPrivate;


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
        cbIsPrivate = findViewById(R.id.cbIsPrivate);


        btnMinusChips.setOnClickListener(this);
        btnPlusChips.setOnClickListener(this);
        btnStartGame.setOnClickListener(this);

        tvRoomCode.setText(roomId);
        tvChipsValue.setText(String.valueOf(currentChips));
        updateRoom();

        cbIsPrivate.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (amIHost) {
                // שומרים את ההחלטה בפיירבייס בזמן אמת
                roomRef.child("private").setValue(isChecked);
            }
        });

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
        if(!snapshot.exists()) {
            finish();
            return;
        }

        GameRoom room = snapshot.getValue(GameRoom.class);

        if (room != null) {
            if (room.getHostId() != null) {
                amIHost = room.getHostId().equals(myUid);
            }

            if (room.getPlayers() != null) {
                adapter.updateList(room.getPlayers(), amIHost);

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
        }
    }

    @Override
    public void onCancelled(@NonNull DatabaseError error) {
    }
}