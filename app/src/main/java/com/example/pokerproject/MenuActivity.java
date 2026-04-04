package com.example.pokerproject;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

import java.sql.DatabaseMetaData;

public class MenuActivity extends AppCompatActivity implements View.OnClickListener {
    private Button btnOnline, btnOffline, btnLogOut;
    TextView tvWelcome;
    FirebaseAuth mAuth;
    DatabaseReference mDatabase;



    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_menu);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.mainMenu), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        btnOnline = findViewById(R.id.btnPlayOnline);
        btnOffline = findViewById(R.id.btnPlayOffline);
        btnLogOut = findViewById(R.id.btnLogout);

        mAuth = FirebaseAuth.getInstance();


        btnOnline.setOnClickListener(this);
        btnOffline.setOnClickListener(this);
        btnLogOut.setOnClickListener(this);

        if(mAuth.getCurrentUser() == null)
        {
            Intent intent = new Intent(MenuActivity.this, LoginActivity.class);
            startActivity(intent);
            finish();
            return;

        }

        tvWelcome = findViewById(R.id.tvWelcome);

        String uid = mAuth.getCurrentUser().getUid();
        mDatabase = FirebaseDatabase.getInstance().getReference("Users").child(uid);
        mDatabase.get().addOnCompleteListener(task -> {

            // 1. בדיקה אם התקשורת הצליחה
            if (task.isSuccessful()) {

                // 2. בדיקה אם המידע באמת קיים בנתיב הזה
                if (task.getResult().exists()) {

                    // 3. המרה מהפורמט של פיירבייס לאובייקט User שלנו
                    DataSnapshot dataSnapshot = task.getResult();
                    User user = dataSnapshot.getValue(User.class);

                    // 4. עדכון המסך (רק אם ההמרה הצליחה)
                    if (user != null) {
                        tvWelcome.setText("Welcome, " + user.getNickname());
                    }
                }
            } else {
                // כאן אפשר לשים טואסט אם הייתה שגיאה בתקשורת
                Toast.makeText(this, "Error getting user data", Toast.LENGTH_SHORT).show();
            }
        });
    }

    @Override
    public void onClick(View view) {
        if(view.getId() == R.id.btnPlayOnline)
        {
            Intent intent = new Intent(MenuActivity.this, LobbyActivity.class);
            startActivity(intent);
            finish();
        }
        else if(view.getId() == R.id.btnPlayOffline)
        {
            Intent intent = new Intent(MenuActivity.this, OfflineGameActivity.class);
            intent.putExtra("isOnline", false);
            startActivity(intent);
            finish();
        }
        else if(view.getId() == R.id.btnLogout)
        {
            mAuth.signOut();
            Intent intent = new Intent(MenuActivity.this, LoginActivity.class);
            startActivity(intent);
            finish();
        }
    }
}