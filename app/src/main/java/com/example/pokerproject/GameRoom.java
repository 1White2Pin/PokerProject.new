package com.example.pokerproject;

import java.util.ArrayList;

public class GameRoom {
    private String roomID, hostId, gameStatus;
    private int pot;
    private ArrayList<User> players;
    private ArrayList<Card> communityCards;
    private int turnIndex;
    private boolean isGameActive;
    private int startingChips;
    private ArrayList<Card> deck;
    private String gameState;
    private int currentBet;
    private String winnerName = "";
    private int dealerIndex=0;

    public int getDealerIndex() {
        return dealerIndex;
    }
    public void setDealerIndex(int dealerIndex) {
        this.dealerIndex = dealerIndex;
    }


    public String getWinnerName() {
        return winnerName;
    }

    public void setWinnerName(String winnerName) {
        this.winnerName = winnerName;
    }


    public GameRoom()
    {
        this.roomID = "";
        this.hostId = "";
        this.gameStatus = "";
        this.pot = 0;
        this.players = new ArrayList<>();
        this.communityCards = new ArrayList<>();
        this.isGameActive = false;
        this.startingChips = 0;
        this.currentBet = 0;

    }

    public GameRoom(String roomID, String hostId)
    {
        this.roomID = roomID;
        this.hostId = hostId;
        this.gameStatus = "Waiting for players...";
        this.pot = 0;
        this.players = new ArrayList<>();
        this.communityCards = new ArrayList<>();
        this.turnIndex = 0;
        this.isGameActive = false;
        this.startingChips = 10000;
        this.currentBet = 0;
    }
        public int getCurrentBet() {
        return currentBet;
    }
    public void setCurrentBet(int currentBet) {
        this.currentBet = currentBet;
    }

    public void setRoomID(String roomID) {
        this.roomID = roomID;
    }
    public String getRoomID() {
        return roomID;
    }

    public ArrayList<Card> getCommunityCards() {
        return communityCards;
    }
    public void setCommunityCards(ArrayList<Card> communityCards) {
        this.communityCards = communityCards;
    }
    public ArrayList<User> getPlayers() {
        return players;
    }
    public void setPlayers(ArrayList<User> players) {
        this.players = players;
    }
    public String getHostId() {
        return hostId;
    }
    public void setHostId(String hostId) {
        this.hostId = hostId;
    }
    public String getGameStatus() {
        return gameStatus;
    }
    public void setGameStatus(String gameStatus) {
        this.gameStatus = gameStatus;
    }
    public int getPot() {
        return pot;
    }
    public void setPot(int pot) {
        this.pot = pot;
    }
    public int getTurnIndex() {
        return turnIndex;
    }
    public void setTurnIndex(int turnIndex) {
        this.turnIndex = turnIndex;
    }
    public boolean isGameActive() {
        return isGameActive;
    }
    public void setGameActive(boolean gameActive) {
        isGameActive = gameActive;
    }
    public int getStartingChips() {
        return startingChips;
    }

    public void setStartingChips(int startingChips) {
        this.startingChips = startingChips;
    }
    public ArrayList<Card> getDeck() {
        return deck;
    }

    public void setDeck(ArrayList<Card> deck) {
        this.deck = deck;
    }

    public String getGameState() {
        return gameState;
    }

    public void setGameState(String gameState) {
        this.gameState = gameState;
    }



}
