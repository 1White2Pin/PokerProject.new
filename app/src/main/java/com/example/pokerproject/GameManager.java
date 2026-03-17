package com.example.pokerproject;

import java.util.ArrayList;

public class GameManager {
    private Deck deck;
    private Player player;
    private Player bot;
    ArrayList<Card> communityCards = new ArrayList<>();

    public GameManager() {

        deck = new Deck();
        deck.shuffle();
        player = new Player("Player", 1000);
        bot = new Player("Bot", 1000);
        communityCards = new ArrayList<>();

    }

    public void startNewGame() {

       player.clearHand();
       bot.clearHand();
       communityCards.clear();
       deck = new Deck();
       deck.shuffle();
       player.addCard(deck.drawCard());
       bot.addCard(deck.drawCard());
       player.addCard(deck.drawCard());
       bot.addCard(deck.drawCard());
    }

    public void Flop()
    {
        communityCards.add(deck.drawCard());
        communityCards.add(deck.drawCard());
        communityCards.add(deck.drawCard());

    }

    public void Turn()
    {
        communityCards.add(deck.drawCard());
    }

    public void River()
    {
        communityCards.add(deck.drawCard());
    }


    public Player getPlayer() { return player; }
    public Player getBot() { return bot; }
    public ArrayList<Card> getCommunityCards() { return communityCards; }

    @Override
    public String toString() {
        return "Board: " + communityCards.toString();
    }










}
