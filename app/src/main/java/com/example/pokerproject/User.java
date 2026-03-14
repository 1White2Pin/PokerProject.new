package com.example.pokerproject;

import java.util.ArrayList;

public class User {
    private String uid;
    private String email;
    private String nickname;
    private String age;
    private String imageURL;
    private int chips;

    private int currentBet;  // כמה הימר בסיבוב הנוכחי
    private String status;   // Active, Folded, Check, Waiting
    private ArrayList<Card> hand;

    public User() {
    }

    public User(String uid, String email, String nickname, String age, String imageURL, int chips) {
        this.uid = uid;
        this.email = email;
        this.nickname = nickname;
        this.age = age;
        this.imageURL = imageURL;
        this.chips = chips;

        // --- אתחול ברירת מחדל ---
        this.currentBet = 0;
        this.status = "Waiting";
    }

    // --- Getters & Setters ---

    public String getUid() { return uid; }
    public void setUid(String uid) { this.uid = uid; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getNickname() { return nickname; }
    public void setNickname(String nickname) { this.nickname = nickname; }

    public String getAge() { return age; }
    public void setAge(String age) { this.age = age; }

    public String getImageURL() { return imageURL; }
    public void setImageURL(String imageURL) { this.imageURL = imageURL; }

    public int getChips() { return chips; }
    public void setChips(int chips) { this.chips = chips; }


    public int getCurrentBet() { return currentBet; }
    public void setCurrentBet(int currentBet) { this.currentBet = currentBet; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public ArrayList<Card> getHand() {
        return hand;
    }

    public void setHand(ArrayList<Card> hand) {
        this.hand = hand;
    }
}