package com.example.pokerproject;

import java.util.ArrayList;

public class Player {
    String name;
    int chips;
    ArrayList<Card> hand = new ArrayList<>();
    public Player(String name, int chips)
    {
        this.name = name;
        this.chips = chips;
    }

    public void addCard(Card card)
    {
        if(card != null)
        {
            hand.add(card);

        }


    }

    public void clearHand()
    {
        hand.clear();
    }

    public ArrayList<Card> getHand() {
        return hand;
    }






}
