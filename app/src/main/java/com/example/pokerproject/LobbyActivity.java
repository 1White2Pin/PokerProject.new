package com.example.pokerproject;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

public class LobbyActivity extends AppCompatActivity implements View.OnClickListener {
    // הגדרת המשתנים
    Button btnCreate, btnJoin;
    EditText etRoomCode;
    FirebaseAuth mAuth;
    DatabaseReference mDatabase;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_lobby); // 1. חיבור לעיצוב XML

        // 2. אתחול רכיבי המסך (לפי ה-IDs מה-XML האחרון ששלחת)
        btnCreate = findViewById(R.id.btnCreateRoom);
        btnJoin = findViewById(R.id.btnJoinRoom);
        etRoomCode = findViewById(R.id.etRoomCode);

        // 3. אתחול פיירבייס
        mAuth = FirebaseAuth.getInstance();
        // אנחנו מצביעים לתיקיית "Users" כדי שנוכל לשלוף את המידע על עצמנו
        mDatabase = FirebaseDatabase.getInstance().getReference("Users");

        // 4. הגדרת האזנה ללחיצות
        btnCreate.setOnClickListener(this);
        btnJoin.setOnClickListener(this);
    }

    @Override
    public void onClick(View view) {
        // וודא שהמשתמש מחובר
        if (mAuth.getCurrentUser() == null) {
            Toast.makeText(this, "User not logged in", Toast.LENGTH_SHORT).show();
            return;
        }

        if (view.getId() == R.id.btnCreateRoom) {
            // --- לוגיקת יצירת חדר ---

            // 1. קריאת נתונים על המשתמש הנוכחי
            mDatabase.child(mAuth.getUid()).get().addOnCompleteListener(task -> {
                if (task.isSuccessful() && task.getResult().exists()) {
                    User myProfile = task.getResult().getValue(User.class);

                    // 2. יצירת קוד רנדומלי
                    int randomCode = (int) (1000 + Math.random() * 9000);
                    String roomID = String.valueOf(randomCode);

                    // 3. יצירת אובייקט חדר
                    GameRoom newRoom = new GameRoom(roomID, mAuth.getUid());

                    // 4. הוספת השחקן לרשימה
                    newRoom.getPlayers().add(myProfile);

                    // 5. שמירה בפיירבייס ומעבר מסך
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
                    Toast.makeText(this, "Error getting user profile", Toast.LENGTH_SHORT).show();
                }
            });

        } else if (view.getId() == R.id.btnJoinRoom) {
            // --- לוגיקת הצטרפות לחדר ---

            String code = etRoomCode.getText().toString().trim();

            if (code.isEmpty()) {
                Toast.makeText(this, "Please enter a room code", Toast.LENGTH_SHORT).show();
                return;
            }

            // 1. קודם שולפים את המידע על עצמנו
            mDatabase.child(mAuth.getUid()).get().addOnCompleteListener(task -> {
                if (task.isSuccessful() && task.getResult().exists()) {
                    User myProfile = task.getResult().getValue(User.class);

                    DatabaseReference roomRef = FirebaseDatabase.getInstance().getReference("Rooms").child(code);

                    // 2. בודקים אם החדר קיים
                    roomRef.get().addOnCompleteListener(roomTask -> {
                        if(roomTask.isSuccessful()) {
                            if(roomTask.getResult().exists()) {
                                GameRoom room = roomTask.getResult().getValue(GameRoom.class);

                                // בדיקה אם אנחנו כבר שם (כדי למנוע כפילויות)
                                boolean isAlreadyIn = false;
                                for (User u : room.getPlayers()) {
                                    if (u.getUid().equals(mAuth.getUid())) {
                                        isAlreadyIn = true;
                                        break;
                                    }
                                }

                                if(isAlreadyIn) {
                                    // כבר בפנים? פשוט תיכנס
                                    Intent intent = new Intent(LobbyActivity.this, WaitingActivity.class);
                                    intent.putExtra("ROOM_ID", code);
                                    intent.putExtra("GAME_MODE", "ONLINE");
                                    startActivity(intent);
                                } else {
                                    // לא בפנים? תוסיף ותיכנס
                                    room.getPlayers().add(myProfile);
                                    roomRef.setValue(room).addOnSuccessListener(unused -> {
                                        Intent intent = new Intent(LobbyActivity.this, WaitingActivity.class);
                                        intent.putExtra("ROOM_ID", code);
                                        intent.putExtra("GAME_MODE", "ONLINE");
                                        startActivity(intent);
                                    });
                                }
                            } else {
                                Toast.makeText(this, "Room not found", Toast.LENGTH_SHORT).show();
                            }
                        } else {
                            Toast.makeText(this, "Error joining room", Toast.LENGTH_SHORT).show();
                        }
                    });
                }
            });
        }
    }
}