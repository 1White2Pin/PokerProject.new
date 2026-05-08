package Activities;

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

import Models.GameRoom;
import com.example.pokerproject.R;
import Adapters.RoomAdapter;
import Models.User;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;

public class LobbyActivity extends AppCompatActivity implements View.OnClickListener {

    // רכיבי ממשק המשתמש (UI)
    Button btnCreate, btnJoin;
    EditText etRoomCode;
    TextView tvMyChips;

    // חיבור לפיירבייס
    FirebaseAuth mAuth;
    DatabaseReference mDatabase;

    // רכיבים לניהול רשימת החדרים הציבוריים (רשימה נגללת)
    RoomAdapter roomAdapter;
    RecyclerView rvPublicRooms;
    ArrayList<GameRoom> publicRooms;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_lobby);

        // קישור המשתנים לרכיבים במסך
        btnCreate = findViewById(R.id.btnCreateRoom);
        btnJoin = findViewById(R.id.btnJoinRoom);
        etRoomCode = findViewById(R.id.etRoomCode);
        tvMyChips = findViewById(R.id.tvMyChips);

        // הגדרת הרשימה הנגללת (RecyclerView) של החדרים הציבוריים
        rvPublicRooms = findViewById(R.id.rvPublicRooms);
        rvPublicRooms.setLayoutManager(new LinearLayoutManager(this)); // תצוגת רשימה אנכית

        publicRooms = new ArrayList<>();
        // יצירת ה"מתאם" (Adapter) שאחראי לקחת נתונים מהמערך ולצייר אותם על המסך
        // כשהמשתמש ילחץ על חדר ברשימה, תופעל פונקציית joinRoom עם מזהה החדר
        roomAdapter = new RoomAdapter(publicRooms, room -> {
            joinRoom(room.getRoomID());
        });
        rvPublicRooms.setAdapter(roomAdapter);

        // אתחול חיבורים לפיירבייס
        mAuth = FirebaseAuth.getInstance();
        mDatabase = FirebaseDatabase.getInstance().getReference("Users");
        FirebaseUser currentUser = mAuth.getCurrentUser();

        // טעינת כמות הצ'יפים של המשתמש מהבנק בפיירבייס והצגתם על המסך
        if(currentUser != null) {
            mDatabase.child(currentUser.getUid()).get().addOnCompleteListener(task -> {
                if (task.isSuccessful() && task.getResult().exists()) {
                    int chips = task.getResult().getValue(User.class).getChips();
                    tvMyChips.setText("My Chips: " + chips);
                } else {
                    Toast.makeText(this, "Error loading user profile", Toast.LENGTH_SHORT).show();
                }
            });
        }

        // הפעלת הפונקציה שמאזינה בזמן אמת לחדרים חדשים שנפתחים
        loadPublicRooms();

        // הגדרת מאזינים ללחיצות על כפתורי יצירה והצטרפות
        btnCreate.setOnClickListener(this);
        btnJoin.setOnClickListener(this);
    }

    // פונקציה שמרכזת את הטיפול בלחיצות כפתורים במסך
    @Override
    public void onClick(View view) {
        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser == null) {
            Toast.makeText(this, "User not logged in", Toast.LENGTH_SHORT).show();
            return; // הגנה: אם המשתמש מנותק, עוצרים כאן
        }

        String myUid = currentUser.getUid();

        // === טיפול בלחיצה על "צור חדר" (Create Room) ===
        if (view.getId() == R.id.btnCreateRoom) {

            // 1. קוראים את הפרופיל העדכני של המשתמש מהפיירבייס (כדי לדעת מה השם ומה היתרה שלו)
            mDatabase.child(myUid).get().addOnCompleteListener(task -> {
                if (task.isSuccessful() && task.getResult().exists()) {

                    User dbUser = task.getResult().getValue(User.class);

                    // שולפים את השם האמיתי מהמסד נתונים
                    String realNickname = dbUser.getNickname();
                    if (realNickname == null || realNickname.isEmpty()) {
                        realNickname = "Guest"; // גיבוי למקרה שאין שם
                    }

                    // שולפים את הצ'יפים ושומרים אותם שוב בבנק (לרענון הנתונים)
                    int startingChips = dbUser.getChips();
                    mDatabase.child(myUid).child("chips").setValue(startingChips);

                    final int finalChips = startingChips;

                    // 2. יוצרים אובייקט של השחקן שלנו שיוכנס לחדר המשחק
                    User myProfile = new User(myUid, realNickname, finalChips);

                    // 3. הגרלת קוד חדר ייחודי בעל 4 ספרות (מ-1000 עד 9999)
                    int randomCode = (int) (1000 + Math.random() * 9000);
                    String roomID = String.valueOf(randomCode);

                    // 4. יצירת אובייקט חדר חדש והוספת השחקן שלנו בתור הראשון (המארח)
                    GameRoom newRoom = new GameRoom(roomID, myUid, false);
                    newRoom.getPlayers().add(myProfile);
                    newRoom.setHostName(dbUser.getNickname());

                    // 5. שמירת החדר החדש במסד הנתונים תחת התיקייה "Rooms"
                    FirebaseDatabase.getInstance().getReference("Rooms")
                            .child(roomID)
                            .setValue(newRoom)
                            .addOnSuccessListener(unused -> {
                                // החדר נשמר בהצלחה! מעבירים את המשתמש למסך ההמתנה (WaitingRoom)
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

            // === טיפול בלחיצה על הצטרפות לחדר ספציפי (לפי קוד) ===
        } else if (view.getId() == R.id.btnJoinRoom) {
            String code = etRoomCode.getText().toString().trim();
            if (code.isEmpty()) {
                Toast.makeText(this, "Please enter a room code", Toast.LENGTH_SHORT).show();
                return;
            }
            // מפעיל את הפונקציה שאחראית להכניס אותנו לחדר
            joinRoom(code);
        }
    }

    // הפונקציה שאחראית על הצטרפות לחדר קיים (בין אם מהרשימה ובין אם מקוד שהוקלד)
    private void joinRoom(String roomID) {
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

                String realNickname = dbUser.getNickname();
                if (realNickname == null || realNickname.isEmpty()) {
                    realNickname = "Guest";
                }

                // בדיקת צ'יפים - אם נגמר לו הכסף (פשט את הרגל), נותנים לו 1000 במתנה כדי שיוכל להמשיך לשחק
                int startingChips = dbUser.getChips();
                if (startingChips <= 0) {
                    startingChips = 1000;
                    mDatabase.child(myUid).child("chips").setValue(startingChips);
                }

                final int finalChips = startingChips;

                // 2. יוצרים את אובייקט השחקן
                User myProfile = new User(myUid, realNickname, finalChips);

                // 3. מחפשים את החדר ב-Firebase לפי הקוד שהתקבל
                DatabaseReference roomRef = FirebaseDatabase.getInstance().getReference("Rooms").child(roomID);

                roomRef.get().addOnCompleteListener(roomTask -> {
                    if (roomTask.isSuccessful() && roomTask.getResult().exists()) {
                        GameRoom room = roomTask.getResult().getValue(GameRoom.class);

                        boolean isAlreadyIn = false;

                        // בדיקה: האם אני כבר נמצא בתוך החדר הזה? (מונע כפילויות אם לוחצים פעמיים)
                        if (room.getPlayers() != null) {
                            for (User u : room.getPlayers()) {
                                if (u.getUid().equals(myUid)) {
                                    isAlreadyIn = true;
                                    u.setChips(finalChips); // מעדכנים את הצ'יפים העדכניים
                                    break;
                                }
                            }
                        }

                        if (isAlreadyIn) {
                            // אם אני כבר בפנים, פשוט מעדכנים ועוברים למסך המתנה
                            roomRef.setValue(room).addOnSuccessListener(unused -> {
                                Intent intent = new Intent(LobbyActivity.this, WaitingActivity.class);
                                intent.putExtra("ROOM_ID", roomID);
                                intent.putExtra("GAME_MODE", "ONLINE");
                                startActivity(intent);
                            });
                        } else {
                            // אם אני חדש בחדר:
                            if (room.getPlayers() == null) {
                                room.setPlayers(new ArrayList<>()); // הגנה מקריסה אם הרשימה ריקה
                            }
                            // הוספת השחקן שלי לרשימת השחקנים בחדר
                            room.getPlayers().add(myProfile);
                            // עדכון החדר ב-Firebase ומעבר למסך המתנה
                            roomRef.setValue(room).addOnSuccessListener(unused -> {
                                Intent intent = new Intent(LobbyActivity.this, WaitingActivity.class);
                                intent.putExtra("ROOM_ID", roomID);
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

    // פונקציה להאזנה לחדרים ציבוריים והצגתם ברשימה (מופעלת אוטומטית כשהמסך עולה)
    private void loadPublicRooms() {
        DatabaseReference roomsRef = FirebaseDatabase.getInstance().getReference("Rooms");

        // addValueEventListener פועל כל הזמן ברקע! ברגע שמישהו יפתח חדר חדש, הרשימה תתעדכן אוטומטית אצלך
        roomsRef.addValueEventListener(new ValueEventListener() {

            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                publicRooms.clear(); // מנקים את הרשימה הישנה לפני שמציירים חדשה

                // עוברים על כל החדרים שקיימים בשרת
                for (DataSnapshot roomSnapshot : snapshot.getChildren()) {
                    GameRoom room = roomSnapshot.getValue(GameRoom.class);

                    // סינון החדרים: מציגים רק חדרים שהם פומביים (!isPrivate) והמשחק בהם עדיין לא התחיל (!isGameActive)
                    if (room != null && !room.isPrivate() && !room.isGameActive()) {

                        // מחשבים כמה שחקנים יש בחדר. מציגים אותו רק אם יש בו פחות מ-4 שחקנים (יש מקום פנוי)
                        int currentPlayers = (room.getPlayers() != null) ? room.getPlayers().size() : 0;
                        if (currentPlayers < 4) {
                            publicRooms.add(room);
                        }
                    }
                }
                // מודיעים למתאם (Adapter) שהנתונים השתנו כדי שירענן את התצוגה על המסך
                roomAdapter.notifyDataSetChanged();
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                // קורה אם יש שגיאת התחברות (למשל אין אינטרנט או הרשאות לפיירבייס)
            }
        });
    }
}