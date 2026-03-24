package com.example.pokerproject;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;

public class LobbyActivity extends AppCompatActivity implements View.OnClickListener {

    Button btnCreate, btnJoin;
    EditText etRoomCode;
    FirebaseAuth mAuth;
    DatabaseReference mDatabase;
    TextView tvMyChips;
    RoomAdapter roomAdapter;
    RecyclerView rvPublicRooms;
    ArrayList<GameRoom> publicRooms;






    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_lobby);

        btnCreate = findViewById(R.id.btnCreateRoom);
        btnJoin = findViewById(R.id.btnJoinRoom);
        etRoomCode = findViewById(R.id.etRoomCode);
        tvMyChips = findViewById(R.id.tvMyChips);
        rvPublicRooms = findViewById(R.id.rvPublicRooms);
        rvPublicRooms.setLayoutManager(new LinearLayoutManager(this));

        publicRooms = new ArrayList<>();
        roomAdapter = new RoomAdapter(publicRooms, room -> {
            joinRoom(room.getRoomID());
        });
        rvPublicRooms.setAdapter(roomAdapter);



        mAuth = FirebaseAuth.getInstance();
        mDatabase = FirebaseDatabase.getInstance().getReference("Users");
        FirebaseUser currentUser = mAuth.getCurrentUser();
        if(mAuth.getCurrentUser() != null) {
            mDatabase.child(currentUser.getUid()).get().addOnCompleteListener(task -> {
                if (task.isSuccessful() && task.getResult().exists()) {
                    int chips = task.getResult().getValue(User.class).getChips();
                    tvMyChips.setText("My Chips: " + chips);
                } else {
                    Toast.makeText(this, "Error loading user profile", Toast.LENGTH_SHORT).show();
                }


            });
        }

        loadPublicRooms();

        btnCreate.setOnClickListener(this);
        btnJoin.setOnClickListener(this);
    }

    @Override
    public void onClick(View view) {
        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser == null) {
            Toast.makeText(this, "User not logged in", Toast.LENGTH_SHORT).show();
            return;
        }

        String myUid = currentUser.getUid();

        if (view.getId() == R.id.btnCreateRoom) {

            // 1. קוראים את כל הפרופיל של המשתמש מהפיירבייס (שם + צ'יפים)
            mDatabase.child(myUid).get().addOnCompleteListener(task -> {
                if (task.isSuccessful() && task.getResult().exists()) {

                    User dbUser = task.getResult().getValue(User.class);

                    // שולפים את השם האמיתי מהמסד נתונים!
                    String realNickname = dbUser.getNickname();
                    if (realNickname == null || realNickname.isEmpty()) {
                        realNickname = "Guest"; // למקרה שאין לו שם מסיבה כלשהי
                    }

                    // שולפים את הצ'יפים (אם אין לו, זה יהיה 0 בברירת מחדל של Java)
                    int startingChips = dbUser.getChips();
                        mDatabase.child(myUid).child("chips").setValue(startingChips); // שומרים בבנק


                    final int finalChips = startingChips;

                    // 2. יוצרים את השחקן שייכנס לחדר עם השם האמיתי והיתרה העדכנית
                    User myProfile = new User(myUid, realNickname, finalChips);

                    // 3. יוצרים חדר חדש ושומרים אותו
                    int randomCode = (int) (1000 + Math.random() * 9000);
                    String roomID = String.valueOf(randomCode);

                    GameRoom newRoom = new GameRoom(roomID, myUid, false);
                    newRoom.getPlayers().add(myProfile);

                    FirebaseDatabase.getInstance().getReference("Rooms")
                            .child(roomID)
                            .setValue(newRoom)
                            .addOnSuccessListener(unused -> {
                                Intent intent = new Intent(LobbyActivity.this, WaitingActivity.class);
                                intent.putExtra("ROOM_ID", roomID);
                                intent.putExtra("GAME_MODE", "ONLINE");
                                startActivity(intent);
                            })
                            .addOnFailureListener(e -> {
                                Toast.makeText(this, "Error creating room", Toast.LENGTH_SHORT).show();
                            });
                } else {
                    Toast.makeText(this, "Error loading user profile", Toast.LENGTH_SHORT).show();
                }
            });

        } else if (view.getId() == R.id.btnJoinRoom) {
            String code = etRoomCode.getText().toString().trim();
            if (code.isEmpty()) {
                Toast.makeText(this, "Please enter a room code", Toast.LENGTH_SHORT).show();
                return;
            }
            joinRoom(code);
        }
    }

    private void joinRoom(String roomID)
    {
        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser == null) {
            Toast.makeText(this, "User not logged in", Toast.LENGTH_SHORT).show();
            return;
        }

        String myUid = currentUser.getUid();

        // 1. קוראים את כל הפרופיל של המשתמש מהפיירבייס לפני ההצטרפות
        mDatabase.child(myUid).get().addOnCompleteListener(task -> {
            if (task.isSuccessful() && task.getResult().exists()) {

                User dbUser = task.getResult().getValue(User.class);

                // שולפים את השם האמיתי מהמסד נתונים
                String realNickname = dbUser.getNickname();
                if (realNickname == null || realNickname.isEmpty()) {
                    realNickname = "Guest";
                }

                int startingChips = dbUser.getChips();
                if (startingChips <= 0) {
                    startingChips = 1000;
                    mDatabase.child(myUid).child("chips").setValue(startingChips);
                }

                final int finalChips = startingChips;

                // 2. יוצרים את השחקן עם השם האמיתי
                User myProfile = new User(myUid, realNickname, finalChips);

                // 3. מצטרפים לחדר לפי ה-roomID שקיבלנו!
                DatabaseReference roomRef = FirebaseDatabase.getInstance().getReference("Rooms").child(roomID);

                roomRef.get().addOnCompleteListener(roomTask -> {
                    if (roomTask.isSuccessful() && roomTask.getResult().exists()) {
                        GameRoom room = roomTask.getResult().getValue(GameRoom.class);

                        boolean isAlreadyIn = false;
                        if (room.getPlayers() != null) {
                            for (User u : room.getPlayers()) {
                                if (u.getUid().equals(myUid)) {
                                    isAlreadyIn = true;
                                    u.setChips(finalChips);
                                    break;
                                }
                            }
                        }

                        if (isAlreadyIn) {
                            roomRef.setValue(room).addOnSuccessListener(unused -> {
                                Intent intent = new Intent(LobbyActivity.this, WaitingActivity.class);
                                intent.putExtra("ROOM_ID", roomID); // משתמשים ב-roomID!
                                intent.putExtra("GAME_MODE", "ONLINE");
                                startActivity(intent);
                            });
                        } else {
                            if (room.getPlayers() == null) {
                                room.setPlayers(new ArrayList<>());
                            }
                            room.getPlayers().add(myProfile);
                            roomRef.setValue(room).addOnSuccessListener(unused -> {
                                Intent intent = new Intent(LobbyActivity.this, WaitingActivity.class);
                                intent.putExtra("ROOM_ID", roomID); // משתמשים ב-roomID!
                                intent.putExtra("GAME_MODE", "ONLINE");
                                startActivity(intent);
                            });
                        }
                    } else {
                        Toast.makeText(LobbyActivity.this, "Room not found", Toast.LENGTH_SHORT).show();
                    }
                });
            } else {
                Toast.makeText(this, "Error loading user profile", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void loadPublicRooms() {
        DatabaseReference roomsRef = FirebaseDatabase.getInstance().getReference("Rooms");
        roomsRef.addValueEventListener(new ValueEventListener() {

            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                publicRooms.clear();
                for (DataSnapshot roomSnapshot : snapshot.getChildren()) {
                    GameRoom room = roomSnapshot.getValue(GameRoom.class);
                    if (room != null && !room.isPrivate() && !room.isGameActive()) {
                        // בדיקה בטוחה: אם יש שחקנים ניקח את הגודל, אם לא אז הגודל הוא 0
                        int currentPlayers = (room.getPlayers() != null) ? room.getPlayers().size() : 0;
                        if (currentPlayers < 4) {
                            publicRooms.add(room);
                        }
                    }
                }
                roomAdapter.notifyDataSetChanged();
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {

            }
        });

    }
}