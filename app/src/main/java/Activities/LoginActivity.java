package Activities;

import android.app.ProgressDialog;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.pokerproject.R;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

public class LoginActivity extends AppCompatActivity {

    // הגדרת משתנים לרכיבי המסך (UI)
    private EditText etEmail, etPassword;
    private Button btnLogin;
    private TextView tvRegisterLink;

    // משתנה לאובייקט האימות של פיירבייס (דרכו מבצעים את ההתחברות)
    private FirebaseAuth mAuth;

    // חלון טעינה שקופץ בזמן שהאפליקציה חושבת/מתחברת
    private ProgressDialog progressDialog;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        // אתחול החיבור למערכת האימות של פיירבייס
        mAuth = FirebaseAuth.getInstance();

        // הגדרת חלון הטעינה (ספינר) שיופיע בזמן ההתחברות
        progressDialog = new ProgressDialog(this);
        progressDialog.setMessage("Logging in...");
        progressDialog.setCancelable(false); // מונע מהמשתמש לסגור את חלון הטעינה על ידי לחיצה מחוץ אליו

        // קישור המשתנים לרכיבים בקובץ ה-XML
        etEmail = findViewById(R.id.etEmail);
        etPassword = findViewById(R.id.etPassword);
        btnLogin = findViewById(R.id.btnLogin);
        tvRegisterLink = findViewById(R.id.tvRegisterLink);

        // מאזין ללחיצה על כפתור ההתחברות
        btnLogin.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                performLogin(); // מפעיל את פונקציית ההתחברות
            }
        });

        // מאזין ללחיצה על הטקסט שמעביר למסך ההרשמה (למי שאין חשבון)
        tvRegisterLink.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(LoginActivity.this, RegisterActivity.class);
                startActivity(intent);
            }
        });
    }

    // --- עדכון: בדיקה גם בכניסה אוטומטית ---
    // הפעולה הזו רצה אוטומטית ברגע שהמסך עולה (לפני שהמשתמש מספיק ללחוץ על משהו)
    @Override
    public void onStart() {
        super.onStart();
        // שולף את המשתמש השמור בטלפון (אם הוא לא עשה התנתקות בפעם הקודמת)
        FirebaseUser currentUser = mAuth.getCurrentUser();

        // אם המשתמש מחובר (לא null) + המייל שלו כבר אומת בעבר -> דלג ישר לתפריט!
        if (currentUser != null && currentUser.isEmailVerified()) {
            goToMenuActivity();
        }
    }

    // הפעולה המרכזית שמבצעת את תהליך ההתחברות מול השרת
    private void performLogin() {
        // משיכת הטקסט שהמשתמש הקליד בשדות (וחיתוך רווחים מיותרים עם trim)
        String email = etEmail.getText().toString().trim();
        String password = etPassword.getText().toString().trim();

        // 1. חסמים ובדיקות תקינות קלט (Validation)
        if (email.isEmpty()) {
            etEmail.setError("Email is required"); // מציג הודעת שגיאה אדומה בשדה
            etEmail.requestFocus(); // מקפיץ את הסמן חזרה לשדה המייל
            return; // עוצר את הפונקציה כדי לא לשלוח סתם בקשה ריקה לפיירבייס
        }

        if (password.isEmpty()) {
            etPassword.setError("Password is required");
            etPassword.requestFocus();
            return;
        }

        // אם הכל תקין, מציג את חלון הטעינה
        progressDialog.show();

        // 2. פנייה לפיירבייס בבקשה להתחבר עם המייל והסיסמה
        mAuth.signInWithEmailAndPassword(email, password)
                .addOnCompleteListener(this, task -> {
                    // ברגע שפיירבייס עונה (בין אם הצליח ובין אם נכשל), מעלימים את חלון הטעינה
                    progressDialog.dismiss();

                    if (task.isSuccessful()) {
                        // ההתחברות הטכנית (שם וסיסמה נכונים) הצליחה, עכשיו בודקים אימות מייל
                        FirebaseUser user = mAuth.getCurrentUser();
                            Toast.makeText(LoginActivity.this, "Login Successful!", Toast.LENGTH_SHORT).show();
                            goToMenuActivity();


                    } else {
                        // ההתחברות נכשלה (סיסמה שגויה, מייל לא קיים וכו')
                        Toast.makeText(LoginActivity.this, "Authentication Failed: " + task.getException().getMessage(),
                                Toast.LENGTH_LONG).show();
                    }
                });
    }

    // פונקציית עזר: מעבירה את המשתמש למסך התפריט הראשי
    private void goToMenuActivity() {
        Intent intent = new Intent(LoginActivity.this, MenuActivity.class);
        startActivity(intent);

        // finish מוחק את מסך ה-Login מההיסטוריה (Stack) של האפליקציה.
        // ככה שאם המשתמש ילחץ על כפתור ה"חזור" בטלפון שלו מהתפריט, הוא יצא מהאפליקציה ולא יחזור למסך ההתחברות.
        finish();
    }
}