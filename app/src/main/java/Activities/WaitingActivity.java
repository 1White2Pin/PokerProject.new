package Activities;

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
import Models.GameRoom;
import com.example.pokerproject.R;
import Models.User;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;

// המחלקה יורשת מ-ValueEventListener כדי להאזין בזמן אמת לשינויים בחדר (מי נכנס, מי יצא, מתי מתחילים)
public class WaitingActivity extends AppCompatActivity implements View.OnClickListener, ValueEventListener {

    // רכיבי ממשק המשתמש
    private TextView tvRoomCode, tvChipsValue, tvWaitMessage;
    private Button btnStartGame, btnPlusChips, btnMinusChips;
    private LinearLayout adminPanel, guestMessage;

    // מזהים של החדר ושל השחקן
    private String roomId;
    private String myUid;

    // ניהול סכום הכניסה (Buy-in)
    private int currentChips = 500; // ברירת מחדל
    private int maxAllowedChips = 10000; // הגבלת מקסימום כדי שלא נדרוש משחקן יותר ממה שיש לו בבנק

    // האם אני המארח שפתח את החדר?
    private boolean amIHost = false;
    private DatabaseReference roomRef;
    private CheckBox cbIsPrivate;

    // מערכים לשמירת רכיבי ה-UI של 4 הכיסאות בחדר (תמונה, שם, וכפתור העפה)
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

        // שליפת ה-UID שלי מהמשתמש המחובר
        myUid = FirebaseAuth.getInstance().getCurrentUser().getUid();

        // קבלת מזהה החדר שהועבר מהמסך הקודם (הלובי)
        roomId = getIntent().getStringExtra("ROOM_ID");
        roomRef = FirebaseDatabase.getInstance().getReference("Rooms").child(roomId);

        // הגדרה קריטית למארח: מה קורה אם האפליקציה שלו קורסת או שהוא סוגר אותה פתאום?
        roomRef.get().addOnSuccessListener(snapshot -> {
            if (snapshot.exists()) {
                GameRoom room = snapshot.getValue(GameRoom.class);
                if (room != null && room.getHostId() != null && room.getHostId().equals(myUid)) {
                    // אם אני המארח, אני מגדיר לפיירבייס למחוק את החדר אוטומטית ברגע שהחיבור שלי מתנתק
                    roomRef.onDisconnect().removeValue();
                }
            }
        });

        // חיבור כל הרכיבים מהעיצוב לקוד
        tvRoomCode = findViewById(R.id.tvRoomCode);
        tvChipsValue = findViewById(R.id.tvChipsValue);
        tvWaitMessage = findViewById(R.id.tvWaitMessage);
        btnStartGame = findViewById(R.id.btnStartGame);
        btnPlusChips = findViewById(R.id.btnPlusChips);
        btnMinusChips = findViewById(R.id.btnMinusChips);
        adminPanel = findViewById(R.id.adminPanel); // פאנל השליטה (מוצג רק למארח)
        guestMessage = findViewById(R.id.guestMessage); // הודעת "ממתין למארח" (מוצגת לאורחים)
        cbIsPrivate = findViewById(R.id.cbIsPrivate);

        // חיבור הרכיבים של כל 4 הכיסאות במערך כדי שיהיה נוח לרוץ עליהם בלולאה אחר כך
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

        // מאזינים ללחיצות כפתורים
        btnMinusChips.setOnClickListener(this);
        btnPlusChips.setOnClickListener(this);
        btnStartGame.setOnClickListener(this);

        tvRoomCode.setText(roomId);
        tvChipsValue.setText(String.valueOf(currentChips));

        // הפעלת ההאזנה לחדר (שולח לפונקציה onDataChange כל פעם שמשהו זז בחדר)
        updateRoom();

        // מאזין לתיבת הסימון (Checkbox) של "חדר פרטי"
        cbIsPrivate.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (amIHost) {
                // מעדכן בשרת האם החדר צריך להופיע ברשימה הציבורית בלובי או להיות מוסתר
                roomRef.child("private").setValue(isChecked);
            }
        });
    }

    // מרכז הלחיצות של הכפתורים בחדר
    @Override
    public void onClick(View view) {

        // כפתור הורדת סכום כניסה
        if( view.getId() == R.id.btnMinusChips) {
            if(currentChips > 100) {
                currentChips -= 100;
                tvChipsValue.setText(String.valueOf(currentChips));
            } else {
                Toast.makeText(this, "Minimum buy-in is 100", Toast.LENGTH_SHORT).show();
            }
        }

        // כפתור העלאת סכום כניסה (עם הגבלה חכמה)
        if(view.getId() == R.id.btnPlusChips) {
            if(currentChips + 100 <= maxAllowedChips) {
                currentChips += 100;
                tvChipsValue.setText(String.valueOf(currentChips));
            } else {
                Toast.makeText(this, "Cannot increase further! The lowest player balance is " + maxAllowedChips, Toast.LENGTH_SHORT).show();
            }
        }

        // כפתור תחילת משחק (מופיע רק למארח)
        if(view.getId() == R.id.btnStartGame) {
            roomRef.get().addOnSuccessListener(dataSnapshot -> {
                GameRoom room = dataSnapshot.getValue(GameRoom.class);

                if (room != null) {
                    // מעדכנים לכל השחקנים בחדר שכמות הצ'יפים שלהם לסיבוב שווה לסכום הכניסה שקבענו
                    if (room.getPlayers() != null) {
                        for (User player : room.getPlayers()) {
                            player.setChips(currentChips);
                        }
                    }

                    room.setStartingChips(currentChips); // שומרים את ההגדרה בחדר
                    room.setGameActive(true); // מעדכנים שהמשחק התחיל! (זה מה שיקפיץ את כולם למסך הבא)
                    roomRef.setValue(room); // דוחפים את הכל לפיירבייס
                }
            });
        }
    }

    // הפעלת המאזין לשינויים בחדר
    public void updateRoom(){
        roomRef.addValueEventListener(this);
    }

    // =========================================================
    // הפונקציה שמופעלת אוטומטית כל פעם שמשהו משתנה בחדר בפיירבייס
    // (שחקן נכנס/יצא, סכום כניסה השתנה, המשחק התחיל, החדר נסגר...)
    // =========================================================
    @Override
    public void onDataChange(@NonNull DataSnapshot snapshot) {

        // 1. הגנה: אם החדר נמחק (כי המארח יצא), זורקים את כולם החוצה ללובי
        if(!snapshot.exists()) {
            Toast.makeText(this, "The host has closed the room.", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        GameRoom room = snapshot.getValue(GameRoom.class);

        if (room != null) {
            // בודקים האם אני המארח לפי ה-UID
            if (room.getHostId() != null) {
                amIHost = room.getHostId().equals(myUid);
            }

            // חישוב המקסימום האפשרי לסכום כניסה, כדי לא להעמיס על שחקנים "עניים"
            if (room.getPlayers() != null) {
                int minBankroll = Integer.MAX_VALUE; // מתחילים ממספר עצום
                for (User p : room.getPlayers()) {
                    if (p.getChips() < minBankroll) {
                        minBankroll = p.getChips(); // מוצאים את השחקן עם הכי מעט צ'יפים אמיתיים
                    }
                }

                maxAllowedChips = minBankroll;

                // אם סכום הכניסה שנקבע גדול ממה שיש לאחד השחקנים, מורידים אותו אוטומטית
                if (currentChips > maxAllowedChips) {
                    currentChips = maxAllowedChips;
                    if(currentChips < 500) currentChips = 500; // רצפת מינימום קשיחה

                    if (amIHost) {
                        tvChipsValue.setText(String.valueOf(currentChips));
                        Toast.makeText(this, "Buy-in automatically adjusted to match the lowest player balance", Toast.LENGTH_SHORT).show();
                    }
                }
            }

            // עדכון חזותי למארח מול אורח
            if(amIHost) {
                adminPanel.setVisibility(View.VISIBLE); // מציג כפתורי שליטה (+ - ו-Start)
                guestMessage.setVisibility(View.GONE);
                btnStartGame.setVisibility(View.VISIBLE);
            } else {
                adminPanel.setVisibility(View.GONE);
                guestMessage.setVisibility(View.VISIBLE); // מציג "המארח בוחר את סכום הכניסה..."
                btnStartGame.setVisibility(View.GONE);
                // מתעדכן בסכום שהמארח קבע הרגע
                tvChipsValue.setText(String.valueOf(room.getStartingChips()));
            }

            // 2. מעבר למשחק: אם המארח לחץ Start, המשתנה isGameActive יהיה true
            if(room.isGameActive()) {
                Intent intent = new Intent(WaitingActivity.this, OnlineActivity.class);
                intent.putExtra("roomId", roomId);
                roomRef.removeEventListener(this); // מנתקים את ההאזנה לחדר ההמתנה
                startActivity(intent); // עוברים למסך השולחן הירוק
                finish(); // סוגרים את חדר ההמתנה כדי שלא נוכל לחזור אליו עם "אחורה"
            }

            // 3. בדיקת "בעיטה" (Kick): האם העיפו אותי?
            if (!amIHost) {
                boolean amIStillIn = false;

                if (room.getPlayers() != null) {
                    for(User u : room.getPlayers()) {
                        if(u.getUid().equals(myUid)) {
                            amIStillIn = true; // אני עדיין ברשימה, הכל טוב
                            break;
                        }
                    }
                }

                // אם סרקו את כל הרשימה ולא מצאו אותי, סימן שהמארח זרק אותי מהמערך
                if(!amIStillIn) {
                    Toast.makeText(WaitingActivity.this, "You were kicked from the room", Toast.LENGTH_SHORT).show();
                    finish(); // חוזרים ללובי
                    return;
                }
            }

            // 4. ציור השחקנים על הכיסאות ("המנקה" מנקה הכל קודם)
            for(int i = 0; i < 4; i++) {
                tvPlayerNames[i].setText("Empty");
                // אם רוצים, פה מגדירים תמונת צללית לכיסא ריק ivPlayers[i].setImageResource(...)
                btnKicks[i].setVisibility(View.GONE);
                btnKicks[i].setOnClickListener(null);
            }

            // 5. הושבת השחקנים מחדש לפי הרשימה המעודכנת מפיירבייס
            if(room.getPlayers() != null) {
                for (int i = 0; i < room.getPlayers().size(); i++) {
                    if (i >= 4) break; // הגנה כדי שלא נחרוג ממערך הכיסאות (המקסימום הוא 4)

                    User player = room.getPlayers().get(i);
                    tvPlayerNames[i].setText(player.getNickname());

                    // טעינת תמונת הפרופיל (אם יש לו כזו) בעזרת Glide
                    if(player.getImageURL() != null && !player.getImageURL().isEmpty()) {
                        Glide.with(this).load(player.getImageURL()).into(ivPlayers[i]);
                    }

                    // כפתור ה-Kick: אם אני המארח, והשחקן בכיסא הזה הוא לא אני - תראה לי את הכפתור להעיף אותו
                    if(amIHost && !player.getUid().equals(myUid)) {
                        btnKicks[i].setVisibility(View.VISIBLE);
                        btnKicks[i].setOnClickListener(v -> kickPlayer(player)); // מאזין ייחודי לשחקן הזה
                    } else {
                        btnKicks[i].setVisibility(View.GONE);
                        btnKicks[i].setOnClickListener(null);
                    }
                }
            }
        }
    }

    // פונקציה למחיקת שחקן מהחדר (מחיקה מהשרת מפעילה תגובת שרשרת שזורקת אותו למסך הקודם)
    private void kickPlayer(User player) {
        // ניגשים רק לרשימת השחקנים בפיירבייס
        roomRef.child("players").get().addOnCompleteListener(task -> {
            if (task.isSuccessful() && task.getResult() != null) {
                ArrayList<User> remainingPlayers = new ArrayList<>();
                DataSnapshot snapshot = task.getResult();

                // בונים רשימה חדשה ונקייה
                for(DataSnapshot child : snapshot.getChildren()) {
                    User user = child.getValue(User.class);
                    // מוסיפים את כולם חוץ מהשחקן שאנחנו מעיפים כעת
                    if(user != null && !user.getUid().equals(player.getUid())) {
                        remainingPlayers.add(user);
                    }
                }
                // דורסים את הרשימה הישנה בשרת עם הרשימה החדשה (הקצרה יותר)
                roomRef.child("players").setValue(remainingPlayers);
            }
        });
    }

    // מתודה חובה בממשק של ValueEventListener
    @Override
    public void onCancelled(@NonNull DatabaseError error) {
    }
}