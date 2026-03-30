package com.example.pokerproject;

import android.Manifest;
import android.app.ProgressDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.view.View;
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
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;

import java.io.File;
import java.io.IOException;

public class RegisterActivity extends AppCompatActivity {

    private EditText etEmail, etPass, etNickname, etAge, etPhone;
    private Button btnRegister, btnTakePic;
    private ImageButton btnCloseDialog;
    private ImageView ivProfile;

    private ProgressDialog progressDialog;

    private FirebaseAuth mAuth;
    private DatabaseReference mDatabase;
    private StorageReference mStorage;

    private Uri imageUri;

    private final ActivityResultLauncher<String> requestPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), isGranted -> {
                if (isGranted) openCamera();
                else Toast.makeText(this, "Camera permission needed", Toast.LENGTH_SHORT).show();
            });

    private final ActivityResultLauncher<Uri> takePictureLauncher =
            registerForActivityResult(new ActivityResultContracts.TakePicture(), isSuccess -> {
                if (isSuccess && imageUri != null) {
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

        mAuth = FirebaseAuth.getInstance();
        mDatabase = FirebaseDatabase.getInstance().getReference("Users");
        mStorage = FirebaseStorage.getInstance().getReference("ProfileImages");

        progressDialog = new ProgressDialog(this);
        progressDialog.setCancelable(false);

        etEmail = findViewById(R.id.etEmail);
        etPass = findViewById(R.id.etPass);
        etNickname = findViewById(R.id.etFirstname);
        etAge = findViewById(R.id.etAge);
        etPhone = findViewById(R.id.etPhone);
        btnRegister = findViewById(R.id.btnRegister);
        btnTakePic = findViewById(R.id.btnTakePic);
        btnCloseDialog = findViewById(R.id.btnCloseDialog);
        ivProfile = findViewById(R.id.ivProfile);

        btnCloseDialog.setOnClickListener(v -> finish());
        btnTakePic.setOnClickListener(v -> checkPermissionAndOpenCamera());
        btnRegister.setOnClickListener(v -> validateAndRegister());
    }

    private void checkPermissionAndOpenCamera() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            openCamera();
        } else {
            requestPermissionLauncher.launch(Manifest.permission.CAMERA);
        }
    }

    private void openCamera() {
        try {
            File storageDir = getExternalFilesDir(Environment.DIRECTORY_PICTURES);
            File photoFile = File.createTempFile("profile_pic", ".jpg", storageDir);
            imageUri = FileProvider.getUriForFile(this, getApplicationContext().getPackageName() + ".provider", photoFile);
            takePictureLauncher.launch(imageUri);
        } catch (IOException e) {
            Toast.makeText(this, "Error creating file for image", Toast.LENGTH_SHORT).show();
        }
    }

    private void validateAndRegister() {
        String email = etEmail.getText().toString().trim();
        String password = etPass.getText().toString().trim();
        String nickname = etNickname.getText().toString().trim();
        String ageStr = etAge.getText().toString().trim();
        String phoneStr = etPhone.getText().toString().trim();

        // 1. קודם כל בודקים שאף שדה לא נשאר ריק
        if (email.isEmpty() || password.isEmpty() || nickname.isEmpty() || ageStr.isEmpty() || phoneStr.isEmpty()) {
            Toast.makeText(this, "Please fill all fields", Toast.LENGTH_SHORT).show();
            return;
        }

        if (!EmailValidator.isValidEmail(email)) {
            etEmail.setError("Invalid email address");
            etEmail.requestFocus(); // מקפיץ את המשתמש חזרה לשדה האימייל
            return;
        }

        // 3. בדיקת אורך סיסמה
        if (password.length() < 6) {
            etPass.setError("Password must be 6+ chars");
            etPass.requestFocus();
            return;
        }

        if (!IsraeliPhoneNumberValidator.isValidIsraeliPhoneNumber(phoneStr)) {
            etPhone.setError("Invalid Israeli phone number (e.g. 0501234567)");
            etPhone.requestFocus(); // מקפיץ את המשתמש חזרה לשדה הטלפון
            return;
        }

        progressDialog.setMessage("Creating Account...");
        progressDialog.show();

        mAuth.createUserWithEmailAndPassword(email, password)
                .addOnCompleteListener(this, task -> {
                    if (task.isSuccessful()) {
                        FirebaseUser firebaseUser = mAuth.getCurrentUser();
                        uploadImageAndSaveData(firebaseUser, nickname, ageStr, email);
                    } else {
                        progressDialog.dismiss();
                        Toast.makeText(RegisterActivity.this, "Reg Failed: " + task.getException().getMessage(), Toast.LENGTH_LONG).show();
                    }
                });
    }

    private void uploadImageAndSaveData(FirebaseUser user, String nickname, String age, String email) {
        if (imageUri == null) {
            saveUserToRealtimeDatabase(user, nickname, age, email, "");
            return;
        }

        StorageReference fileRef = mStorage.child(user.getUid() + ".jpg");
        fileRef.putFile(imageUri).addOnSuccessListener(taskSnapshot -> {
            fileRef.getDownloadUrl().addOnSuccessListener(uri -> {
                saveUserToRealtimeDatabase(user, nickname, age, email, uri.toString());
            });
        }).addOnFailureListener(e -> {
            progressDialog.dismiss();
            Toast.makeText(this, "Image Upload Failed", Toast.LENGTH_SHORT).show();
        });
    }

    private void saveUserToRealtimeDatabase(FirebaseUser firebaseUser, String nickname, String age, String email, String imageURL) {
        String uid = firebaseUser.getUid();

        // יצירת אובייקט השחקן (שמנו 1000 צ'יפים התחלתיים כברירת מחדל)
        User newUser = new User(uid, email, nickname, age, imageURL, 1000);

        mDatabase.child(uid).setValue(newUser)
                .addOnCompleteListener(task -> {
                    progressDialog.dismiss();

                    if (task.isSuccessful()) {
                        Toast.makeText(RegisterActivity.this, "Account Created Successfully!", Toast.LENGTH_SHORT).show();

                        // מעבר ישיר ללובי או למסך ההתחברות (במקרה הזה החזרנו אותו להתחברות)
                        mAuth.signOut();
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