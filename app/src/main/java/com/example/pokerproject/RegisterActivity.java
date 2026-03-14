package com.example.pokerproject;

import android.Manifest;
import android.app.AlertDialog; // הוספתי את זה בשביל הדיאלוג
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.provider.MediaStore;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;

import java.io.ByteArrayOutputStream;

public class RegisterActivity extends AppCompatActivity {

    private EditText etEmail, etPass, etNickname, etAge;
    private Button btnRegister, btnTakePic;
    private ImageButton btnCloseDialog;
    private ImageView ivProfile;

    private ProgressDialog progressDialog;

    private FirebaseAuth mAuth;
    private DatabaseReference mDatabase;
    private StorageReference mStorage;

    private Bitmap capturedImageBitmap = null;

    private final ActivityResultLauncher<String> requestPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), isGranted -> {
                if (isGranted) openCamera();
                else Toast.makeText(this, "Camera permission needed", Toast.LENGTH_SHORT).show();
            });

    private final ActivityResultLauncher<Intent> cameraLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                    Bundle extras = result.getData().getExtras();
                    if (extras != null) {
                        capturedImageBitmap = (Bitmap) extras.get("data");
                        ivProfile.setImageBitmap(capturedImageBitmap);
                    }
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_register);

        mAuth = FirebaseAuth.getInstance();
        mDatabase = FirebaseDatabase.getInstance().getReference("Users");
        mStorage = FirebaseStorage.getInstance().getReference("ProfileImages");

        progressDialog = new ProgressDialog(this);
        progressDialog.setMessage("Creating Account...");
        progressDialog.setCancelable(false);

        etEmail = findViewById(R.id.etEmail);
        etPass = findViewById(R.id.etPass);
        etNickname = findViewById(R.id.etFirstname);
        etAge = findViewById(R.id.etAge);
        btnRegister = findViewById(R.id.btnRegister);
        btnTakePic = findViewById(R.id.btnTakePic);
        btnCloseDialog = findViewById(R.id.btnCloseDialog);
        ivProfile = findViewById(R.id.ivProfile);

        btnCloseDialog.setOnClickListener(v -> finish());
        btnTakePic.setOnClickListener(v -> checkPermissionAndOpenCamera());
        btnRegister.setOnClickListener(v -> registerUser());
    }

    private void checkPermissionAndOpenCamera() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            openCamera();
        } else {
            requestPermissionLauncher.launch(Manifest.permission.CAMERA);
        }
    }

    private void openCamera() {
        Intent takePictureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        try { cameraLauncher.launch(takePictureIntent); }
        catch (Exception e) { Toast.makeText(this, "Error opening camera", Toast.LENGTH_SHORT).show(); }
    }

    private void registerUser() {
        String email = etEmail.getText().toString().trim();
        String password = etPass.getText().toString().trim();
        String nickname = etNickname.getText().toString().trim();
        String ageStr = etAge.getText().toString().trim();

        if (email.isEmpty() || password.isEmpty() || nickname.isEmpty() || ageStr.isEmpty()) {
            Toast.makeText(this, "Please fill all fields", Toast.LENGTH_SHORT).show();
            return;
        }
        if (password.length() < 6) {
            etPass.setError("Password must be 6+ chars");
            return;
        }

        progressDialog.show();

        mAuth.createUserWithEmailAndPassword(email, password)
                .addOnCompleteListener(this, task -> {
                    if (task.isSuccessful()) {
                        FirebaseUser firebaseUser = mAuth.getCurrentUser();

                        if (firebaseUser != null) {
                            firebaseUser.sendEmailVerification();
                            // לא צריך טואסט כאן, הדיאלוג בסוף יגיד את זה
                        }

                        uploadImageAndSaveData(firebaseUser, nickname, ageStr, email);

                    } else {
                        progressDialog.dismiss();
                        Toast.makeText(RegisterActivity.this, "Reg Failed: " + task.getException().getMessage(), Toast.LENGTH_LONG).show();
                    }
                });
    }

    private void uploadImageAndSaveData(FirebaseUser user, String nickname, String age, String email) {
        if (capturedImageBitmap == null) {
            saveUserToRealtimeDatabase(user, nickname, age, email, "");
            return;
        }


        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        capturedImageBitmap.compress(Bitmap.CompressFormat.JPEG, 90, baos);
        byte[] data = baos.toByteArray();

        StorageReference fileRef = mStorage.child(user.getUid() + ".jpg");

        fileRef.putBytes(data).addOnSuccessListener(taskSnapshot -> {
            fileRef.getDownloadUrl().addOnSuccessListener(uri -> {
                String imageURL = uri.toString();
                saveUserToRealtimeDatabase(user, nickname, age, email, imageURL);
            });
        }).addOnFailureListener(e -> {
            progressDialog.dismiss();
            Toast.makeText(this, "Image Upload Failed", Toast.LENGTH_SHORT).show();
        });
    }

    private void saveUserToRealtimeDatabase(FirebaseUser firebaseUser, String nickname, String age, String email, String imageURL) {
        String uid = firebaseUser.getUid();
        User newUser = new User(uid, email, nickname, age, imageURL, 1000);

        mDatabase.child(uid).setValue(newUser)
                .addOnCompleteListener(task -> {
                    progressDialog.dismiss(); // סוגרים את הטעינה

                    if (task.isSuccessful()) {
                        // --- השינוי הגדול כאן ---

                        // 1. מנתקים את המשתמש (כדי שלא ייכנס אוטומטית)
                        mAuth.signOut();

                        // 2. מציגים את הדיאלוג
                        showVerificationDialog(email);

                    } else {
                        Toast.makeText(RegisterActivity.this, "DB Save Failed: " + task.getException().getMessage(), Toast.LENGTH_SHORT).show();
                    }
                });
    }

    // --- הפונקציה שמציגה את הדיאלוג ומחזירה ללוגין ---
    private void showVerificationDialog(String email) {
        new AlertDialog.Builder(this)
                .setTitle("Verify Your Email")
                .setMessage("A verification link has been sent to " + email + ".\nPlease check your inbox and verify your account to log in.")
                .setPositiveButton("OK, Go to Login", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        // מעבר למסך ההתחברות
                        Intent intent = new Intent(RegisterActivity.this, LoginActivity.class);
                        // מנקה את ההיסטוריה כדי שלא יחזרו להרשמה
                        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                        startActivity(intent);
                        finish();
                    }
                })
                .setCancelable(false) // אי אפשר לסגור בלי ללחוץ OK
                .show();
    }


}