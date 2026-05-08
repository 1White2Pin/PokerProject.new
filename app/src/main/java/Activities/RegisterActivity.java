package Activities;

import android.Manifest;
import android.app.ProgressDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;

import com.bumptech.glide.Glide;
import Helpers.EmailValidator;
import Helpers.IsraeliPhoneNumberValidator;
import com.example.pokerproject.R;
import Models.User;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;

import java.io.File;
import java.io.IOException;

public class RegisterActivity extends AppCompatActivity {

    // משתנים לרכיבי המסך (שדות טקסט, כפתורים ותמונה)
    private EditText etEmail, etPass, etNickname, etAge, etPhone;
    private Button btnRegister, btnTakePic;
    private ImageButton btnCloseDialog;
    private ImageView ivProfile;

    // חלון טעינה שיוצג בזמן שהאפליקציה שומרת נתונים בשרת
    private ProgressDialog progressDialog;

    // משתנים לחיבור עם שירותי Firebase השונים
    private FirebaseAuth mAuth;           // אימות (הרשמה עם מייל וסיסמה)
    private DatabaseReference mDatabase;  // מסד נתונים (שמירת פרטי המשתמש)
    private StorageReference mStorage;    // אחסון קבצים (שמירת תמונת הפרופיל)

    // משתנה לשמירת הנתיב (כתובת) של התמונה שצולמה במכשיר
    private Uri imageUri;

    // --- מערכת ההרשאות והמצלמה החדשה של אנדרואיד ---

    // 1. משגר (Launcher) לבקשת הרשאת מצלמה מהמשתמש
    private final ActivityResultLauncher<String> requestPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), isGranted -> {
                if (isGranted) openCamera(); // אם אישר, פותח את המצלמה
                else Toast.makeText(this, "Camera permission needed", Toast.LENGTH_SHORT).show(); // אם סירב
            });

    // 2. משגר (Launcher) להפעלת המצלמה וקבלת התוצאה (התמונה שצולמה)
    private final ActivityResultLauncher<Uri> takePictureLauncher =
            registerForActivityResult(new ActivityResultContracts.TakePicture(), isSuccess -> {
                if (isSuccess && imageUri != null) {
                    // התמונה צולמה בהצלחה! מסירים פילטרים מהתמונה ומציגים אותה באמצעות ספריית Glide
                    ivProfile.setImageTintList(null);
                    Glide.with(this).load(imageUri).into(ivProfile);
                } else {
                    Toast.makeText(this, "Camera canceled", Toast.LENGTH_SHORT).show();
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_register);

        // אתחול החיבור לכל שירותי פיירבייס
        mAuth = FirebaseAuth.getInstance();
        mDatabase = FirebaseDatabase.getInstance().getReference("Users"); // נשמור תחת התיקייה Users
        mStorage = FirebaseStorage.getInstance().getReference("ProfileImages"); // נשמור תמונות תחת ProfileImages

        // הגדרת חלון הטעינה
        progressDialog = new ProgressDialog(this);
        progressDialog.setCancelable(false); // המשתמש לא יכול לסגור אותו בלחיצה בצד

        // קישור משתנים לרכיבים ב-XML
        etEmail = findViewById(R.id.etEmail);
        etPass = findViewById(R.id.etPass);
        etNickname = findViewById(R.id.etFirstname);
        etAge = findViewById(R.id.etAge);
        etPhone = findViewById(R.id.etPhone);
        btnRegister = findViewById(R.id.btnRegister);
        btnTakePic = findViewById(R.id.btnTakePic);
        btnCloseDialog = findViewById(R.id.btnCloseDialog);
        ivProfile = findViewById(R.id.ivProfile);

        // הגדרת מאזינים ללחיצות (Click Listeners)
        btnCloseDialog.setOnClickListener(v -> finish()); // סוגר את חלון ההרשמה וחוזר אחורה
        btnTakePic.setOnClickListener(v -> checkPermissionAndOpenCamera()); // הפעלת מצלמה
        btnRegister.setOnClickListener(v -> validateAndRegister()); // התחלת תהליך ההרשמה
    }

    // פונקציה שבודקת האם יש לנו כבר אישור להשתמש במצלמה
    private void checkPermissionAndOpenCamera() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            openCamera(); // יש הרשאה - פותחים מצלמה
        } else {
            requestPermissionLauncher.launch(Manifest.permission.CAMERA); // אין הרשאה - מבקשים מהמשתמש
        }
    }

    // פונקציה שמכינה קובץ ריק ושולחת את אפליקציית המצלמה של הטלפון למלא אותו בתמונה
    private void openCamera() {
        try {
            // יצירת תיקייה וקובץ זמני עבור התמונה
            File storageDir = getExternalFilesDir(Environment.DIRECTORY_PICTURES);
            File photoFile = File.createTempFile("profile_pic", ".jpg", storageDir);

            // המרת הקובץ ל-URI (כתובת מאובטחת שהמצלמה יכולה לגשת אליה)
            imageUri = FileProvider.getUriForFile(this, getApplicationContext().getPackageName() + ".provider", photoFile);

            // הפעלת המצלמה
            takePictureLauncher.launch(imageUri);
        } catch (IOException e) {
            Toast.makeText(this, "Error creating file for image", Toast.LENGTH_SHORT).show();
        }
    }

    // הפונקציה המרכזית: בודקת שהקלט תקין ומתחילה את ההרשמה בפיירבייס
    private void validateAndRegister() {
        // משיכת כל הנתונים מהשדות וניקוי רווחים (trim)
        String email = etEmail.getText().toString().trim();
        String password = etPass.getText().toString().trim();
        String nickname = etNickname.getText().toString().trim();
        String ageStr = etAge.getText().toString().trim();
        String phoneStr = etPhone.getText().toString().trim();

        // 1. בדיקה שכל השדות מלאים
        if (email.isEmpty() || password.isEmpty() || nickname.isEmpty() || ageStr.isEmpty() || phoneStr.isEmpty()) {
            Toast.makeText(this, "Please fill all fields", Toast.LENGTH_SHORT).show();
            return; // עוצר את הפונקציה
        }

        // 2. בדיקת תקינות כתובת אימייל (בעזרת מחלקת העזר שבנית)
        if (!EmailValidator.isValidEmail(email)) {
            etEmail.setError("Invalid email address");
            etEmail.requestFocus(); // מקפיץ את הסמן חזרה לשדה
            return;
        }

        // 3. פיירבייס דורש סיסמה של לפחות 6 תווים
        if (password.length() < 6) {
            etPass.setError("Password must be 6+ chars");
            etPass.requestFocus();
            return;
        }

        // 4. בדיקת תקינות מספר טלפון ישראלי
        if (!IsraeliPhoneNumberValidator.isValidIsraeliPhoneNumber(phoneStr)) {
            etPhone.setError("Invalid Israeli phone number (e.g. 0501234567)");
            etPhone.requestFocus();
            return;
        }

        // הכל תקין! מציגים חלון טעינה ומתחילים את ההרשמה
        progressDialog.setMessage("Creating Account...");
        progressDialog.show();

        // יצירת משתמש במערכת האימות (Authentication) של Firebase
        mAuth.createUserWithEmailAndPassword(email, password)
                .addOnCompleteListener(this, task -> {
                    if (task.isSuccessful()) {
                        // ההרשמה במערכת האימות הצליחה!
                        FirebaseUser firebaseUser = mAuth.getCurrentUser();
                        // עוברים לשלב הבא: העלאת התמונה השמירת השם, הגיל והטלפון
                        uploadImageAndSaveData(firebaseUser, nickname, ageStr, email);
                    } else {
                        // נכשל (למשל האימייל כבר קיים במערכת)
                        progressDialog.dismiss();
                        Toast.makeText(RegisterActivity.this, "Reg Failed: " + task.getException().getMessage(), Toast.LENGTH_LONG).show();
                    }
                });
    }

    // פונקציה להעלאת תמונת הפרופיל ל-Storage (אחסון הענן של פיירבייס)
    private void uploadImageAndSaveData(FirebaseUser user, String nickname, String age, String email) {
        // אם המשתמש לא צילם תמונה, אנחנו מדלגים ישר לשמירת הנתונים בלי תמונה
        if (imageUri == null) {
            saveUserToRealtimeDatabase(user, nickname, age, email, "");
            return;
        }

        // יצירת נתיב ב-Storage תחת השם של המשתמש (ה-UID שלו)
        StorageReference fileRef = mStorage.child(user.getUid() + ".jpg");

        // העלאת הקובץ
        fileRef.putFile(imageUri).addOnSuccessListener(taskSnapshot -> {
            // לאחר שההעלאה הצליחה, אנחנו מבקשים את "הלינק החיצוני" (Download URL) של התמונה
            fileRef.getDownloadUrl().addOnSuccessListener(uri -> {
                // רק עכשיו, כשיש לנו לינק לתמונה, אנחנו הולכים לשמור את הכל במסד הנתונים
                saveUserToRealtimeDatabase(user, nickname, age, email, uri.toString());
            });
        }).addOnFailureListener(e -> {
            progressDialog.dismiss();
            Toast.makeText(this, "Image Upload Failed", Toast.LENGTH_SHORT).show();
        });
    }

    // השלב הסופי: שמירת כל הנתונים של המשתמש ב-Realtime Database
    private void saveUserToRealtimeDatabase(FirebaseUser firebaseUser, String nickname, String age, String email, String imageURL) {
        String uid = firebaseUser.getUid(); // תעודת הזהות הייחודית שפיירבייס נתן למשתמש

        // יצירת אובייקט השחקן (ברירת המחדל: מקבל 1000 צ'יפים בהרשמה)
        User newUser = new User(uid, email, nickname, age, imageURL, 1000);

        // שמירת האובייקט בתוך התיקייה "Users" תחת ה-UID של המשתמש
        mDatabase.child(uid).setValue(newUser)
                .addOnCompleteListener(task -> {
                    progressDialog.dismiss(); // מעלימים את הספינר

                    if (task.isSuccessful()) {
                        Toast.makeText(RegisterActivity.this, "Account Created Successfully!", Toast.LENGTH_SHORT).show();

                        // ההרשמה הסתיימה! מנתקים את המשתמש כדי שיעבור דרך מסך ההתחברות (ויאמת את המייל שלו שם)
                        mAuth.signOut();

                        // יצירת מעבר (Intent) למסך ההתחברות וניקוי היסטוריית המסכים כדי שלא יוכל לחזור אחורה להרשמה
                        Intent intent = new Intent(RegisterActivity.this, LoginActivity.class);
                        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                        startActivity(intent);
                        finish();
                    } else {
                        Toast.makeText(RegisterActivity.this, "DB Save Failed: " + task.getException().getMessage(), Toast.LENGTH_SHORT).show();
                    }
                });
    }
}