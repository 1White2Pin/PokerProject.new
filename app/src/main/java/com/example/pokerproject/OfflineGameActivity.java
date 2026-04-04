package com.example.pokerproject;

import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import java.util.ArrayList;

public class OfflineGameActivity extends AppCompatActivity implements View.OnClickListener, SeekBar.OnSeekBarChangeListener {
    PokerGameView pokerGameView;
    Button btnFold, btnCheck, btnRaise;
    TextView tvPotSize;
    LinearLayout layoutActionButtons, layoutBetting;
    Button btnCancelBet, btnConfirmBet, btnAllIn;
    SeekBar sbBetAmount;
    TextView tvBetAmount;
    GameRoom room = new GameRoom();


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });
        btnFold = findViewById(R.id.btnFold);
        btnCheck = findViewById(R.id.btnCheck);
        btnRaise = findViewById(R.id.btnRaise);
        tvPotSize = findViewById(R.id.tvPotSize);
        pokerGameView = findViewById(R.id.pokerGameView);
        layoutActionButtons = findViewById(R.id.layoutActionButtons);
        layoutBetting = findViewById(R.id.layoutBetting);
        btnCancelBet = findViewById(R.id.btnCancelBet);
        btnConfirmBet = findViewById(R.id.btnConfirmBet);
        sbBetAmount = findViewById(R.id.sbBetAmount);
        tvBetAmount = findViewById(R.id.tvBetAmount);
        btnAllIn = findViewById(R.id.btnAllIn);
        btnFold.setOnClickListener(this);
        btnCheck.setOnClickListener(this);
        btnRaise.setOnClickListener(this);
        btnCancelBet.setOnClickListener(this);
        btnConfirmBet.setOnClickListener(this);
        btnAllIn.setOnClickListener(this);
        sbBetAmount.setOnSeekBarChangeListener(this);
        initLocalGame();
        resetRoomForNextRound(room);
        pokerGameView.updateGame(room, "My_UID");


    }

    @Override
    public void onClick(View view) {
        if (view.getId() == R.id.btnFold) {
            for (User player : room.getPlayers()) {

                if (player.getUid().equals("My_UID")) {
                    player.setStatus("Folded");
                    break;
                }
            }

            advanceGameRound(room);
            updateUI();
        }

        else if(view.getId() == R.id.btnCheck)
        {
            for (User player : room.getPlayers()) {
                if (player.getUid().equals("My_UID")) {
                    if (room.getCurrentBet() > 0) {
                        int amountToCall = room.getCurrentBet() - player.getCurrentBet();
                        if (amountToCall > 0) {
                            if (amountToCall >= player.getChips()) {
                                amountToCall = player.getChips();
                            }
                            player.setChips(player.getChips() - amountToCall);
                            room.setPot(room.getPot() + amountToCall);
                            player.setCurrentBet(player.getCurrentBet() + amountToCall);
                            Toast.makeText(OfflineGameActivity.this, "Called " + amountToCall, Toast.LENGTH_SHORT).show();
                        }
                    }
                    player.setStatus("Checked");
                    break;
                }

            }
            advanceGameRound(room);
        }

        else if(view.getId() == R.id.btnRaise)
        {
            // קודם מוצאים את השחקן שלנו כדי לדעת מה המקסימום צ'יפים שהוא יכול להמר
            for (User player : room.getPlayers()) {
                if (player.getUid().equals("My_UID")) {
                    sbBetAmount.setMax(player.getChips() + player.getCurrentBet());
                    sbBetAmount.setProgress(room.getCurrentBet());
                    tvBetAmount.setText(String.valueOf(room.getCurrentBet()));
                    break;
                }
            }
            // מחליפים תצוגה
            layoutActionButtons.setVisibility(View.GONE);
            layoutBetting.setVisibility(View.VISIBLE);
        }

        else if(view.getId() == R.id.btnConfirmBet)
        {
            int finalBetAmount = sbBetAmount.getProgress();
            if (finalBetAmount <= 0) {
                Toast.makeText(this, "Bet must be greater than 0", Toast.LENGTH_SHORT).show();
                return;
            }

            if (finalBetAmount < room.getCurrentBet()) {
                Toast.makeText(OfflineGameActivity.this, "Not enough chips to call!", Toast.LENGTH_SHORT).show();
                return;
            }

            for (User player : room.getPlayers()) {
                if (player.getUid().equals("My_UID")) {
                    int amountToAdd = finalBetAmount - player.getCurrentBet();

                    if (amountToAdd > player.getChips()) {
                        amountToAdd = player.getChips();
                    }

                    room.setCurrentBet(finalBetAmount);
                    room.setPot(room.getPot() + amountToAdd);
                    player.setChips(player.getChips() - amountToAdd);
                    player.setCurrentBet(finalBetAmount);
                    player.setStatus("Checked");
                }
                // בגלל שעשינו העלאה, כל שאר השחקנים צריכים להגיב מחדש
                else if (!player.getStatus().equals("Folded") && !player.getStatus().equals("Out")) {
                    player.setStatus("Waiting");
                }
            }

            // מחזירים תצוגה, מעבירים תור ומרעננים מסך
            layoutBetting.setVisibility(View.GONE);
            layoutActionButtons.setVisibility(View.VISIBLE);
            advanceGameRound(room);
            updateUI();
        }

        else if(view.getId() == R.id.btnAllIn)
        {
            for (User player : room.getPlayers()) {
                if (player.getUid().equals("My_UID")) {
                    int allInAmount = player.getChips();
                    int totalBet = player.getCurrentBet() + allInAmount;

                    if (totalBet > room.getCurrentBet()) {
                        room.setCurrentBet(totalBet);
                    }
                    room.setPot(room.getPot() + allInAmount);
                    player.setChips(0);
                    player.setCurrentBet(totalBet);
                    player.setStatus("Checked");
                }
                // גם פה, כולם חייבים להגיב ל-All In שלך
                else if (!player.getStatus().equals("Folded") && !player.getStatus().equals("Out")) {
                    player.setStatus("Waiting");
                }
            }

            // מחזירים תצוגה, מעבירים תור ומרעננים מסך
            layoutBetting.setVisibility(View.GONE);
            layoutActionButtons.setVisibility(View.VISIBLE);
            advanceGameRound(room);
            updateUI();
        }


    }

    @Override
    public void onProgressChanged(SeekBar seekBar, int i, boolean b) {

    }

    @Override
    public void onStartTrackingTouch(SeekBar seekBar) {

    }

    @Override
    public void onStopTrackingTouch(SeekBar seekBar) {

    }

    private void initLocalGame()
    {
        ArrayList<User> players = new ArrayList<>();
        players.add(new User("My_UID", "Me", 1000));
        players.add(new User("BOT_1", "Bot1", 1000));
        players.add(new User("BOT_2", "Bot2", 1000));
        players.add(new User("BOT_3", "Bot3", 1000));
        room.setPlayers(players);

    }
    private void resetRoomForNextRound(GameRoom room) {
        room.setGameState("PreFlop");
        if (room.getCommunityCards() != null) room.getCommunityCards().clear();

        for (User player : room.getPlayers()) {
            player.setChipsBeforeRound(player.getChips());

            player.setCurrentBet(0);
            player.setHand(new ArrayList<>());
            if (player.getChips() <= 0) {
                player.setStatus("Out");
            } else {
                player.setStatus("Waiting");
            }
        }

        room.setDealerIndex((room.getDealerIndex() + 1) % room.getPlayers().size());
        int smallBlindIndex, bigBlindIndex, dealerTurnIndex;

        if(room.getPlayers().size() == 2) {
            smallBlindIndex = room.getDealerIndex();
            bigBlindIndex = (room.getDealerIndex() + 1) % room.getPlayers().size();
            dealerTurnIndex = smallBlindIndex;
        } else {
            smallBlindIndex = (room.getDealerIndex() + 1) % room.getPlayers().size();
            bigBlindIndex = (room.getDealerIndex() + 2) % room.getPlayers().size();
            dealerTurnIndex = (bigBlindIndex + 1) % room.getPlayers().size();
        }

        int sbAmount = 100;
        int bbAmount = 200;

        User sbPlayer = room.getPlayers().get(smallBlindIndex);
        int actualSb = Math.min(sbAmount, sbPlayer.getChips());
        sbPlayer.setChips(sbPlayer.getChips() - actualSb);
        sbPlayer.setCurrentBet(actualSb);

        User bbPlayer = room.getPlayers().get(bigBlindIndex);
        int actualBb = Math.min(bbAmount, bbPlayer.getChips());
        bbPlayer.setChips(bbPlayer.getChips() - actualBb);
        bbPlayer.setCurrentBet(actualBb);

        room.setPot(actualSb + actualBb);
        room.setCurrentBet(actualBb);
        room.setTurnIndex(dealerTurnIndex);

        Deck newDeck = new Deck();
        newDeck.shuffle();
        ArrayList<Card> deckList = new ArrayList<>();
        Card c;
        while ((c = newDeck.drawCard()) != null) deckList.add(c);
        room.setDeck(deckList);

        for (User player : room.getPlayers()) {
            if(!player.getStatus().equals("Out")) {
                ArrayList<Card> newHand = new ArrayList<>();
                newHand.add(deckList.remove(0));
                newHand.add(deckList.remove(0));
                player.setHand(newHand);
            }
        }
    }

    private void updateUI() {
        // 1. מעדכנים את הטקסט של הקופה למעלה
        tvPotSize.setText("Pot: " + room.getPot());

        // 2. אומרים לקנבס המיוחד שלך לצייר מחדש את השולחן, הקלפים והשחקנים
        pokerGameView.updateGame(room, "My_UID");

        // אם המשחק כרגע בשלב חשיפת קלפים (Showdown), הכל נשאר כבוי
        if (room.getGameState().equals("Showdown")) {
            btnFold.setEnabled(false);
            btnCheck.setEnabled(false);
            btnRaise.setEnabled(false);
            return;
        }

        // 3. בודקים של מי התור עכשיו
        User currentPlayer = room.getPlayers().get(room.getTurnIndex());

        if (currentPlayer.getUid().equals("My_UID")) {
            // התור שלך! מדליקים את הכפתורים כדי שתוכל לשחק
            btnFold.setEnabled(true);
            btnCheck.setEnabled(true);
            btnRaise.setEnabled(true);

            // טריק קטן: אם מישהו העלה הימור, נשנה את הכפתור מ-Check ל-Call
            if (room.getCurrentBet() > 0 && room.getCurrentBet() > currentPlayer.getCurrentBet()) {
                btnCheck.setText("Call");
            } else {
                btnCheck.setText("Check");
            }

        } else {
            // התור של בוט! מכבים לך את הכפתורים כדי שלא תוכל להתערב לו
            btnFold.setEnabled(false);
            btnCheck.setEnabled(false);
            btnRaise.setEnabled(false);

            // מפעילים את הבוט! שמתי השהייה של שנייה וחצי כדי שזה ייראה כאילו הוא חושב
            new android.os.Handler().postDelayed(() -> {
                playBotTurn(); // (את הפונקציה הזו נכניס מיד בהמשך)
            }, 1500);
        }
    }

    // ══════════════════════════════════════════════════════
    //  ניהול סיבובים - אופליין! (PreFlop -> Flop -> Turn -> River)
    // ══════════════════════════════════════════════════════
    private void advanceGameRound(GameRoom room) {
        boolean isRoundComplete = true;
        for (User player : room.getPlayers()) {
            if (!player.getStatus().equals("Checked") && !player.getStatus().equals("Folded") && !player.getStatus().equals("Out")) {
                isRoundComplete = false;
                break;
            }
        }

        if (!isRoundComplete) {
            // הסיבוב לא נגמר, מעבירים תור לבא בתור ומרעננים מסך
            room.setTurnIndex(getNextActivePlayerIndex(room));
            updateUI();
        } else {
            // כולם סיימו להמר! עוברים לשלב הבא
            if (room.getGameState().equalsIgnoreCase("River")) {
                handleShowdown(room);
            } else {
                ArrayList<Card> deck = room.getDeck();
                ArrayList<Card> communityCards = room.getCommunityCards();
                if (communityCards == null) communityCards = new ArrayList<>();

                if (room.getGameState().equalsIgnoreCase("PreFlop")) {
                    for (int i = 0; i < 3; i++) {
                        if (deck != null && !deck.isEmpty()) communityCards.add(deck.remove(0));
                    }
                    room.setGameState("Flop");
                } else if (room.getGameState().equalsIgnoreCase("Flop")) {
                    if (deck != null && !deck.isEmpty()) communityCards.add(deck.remove(0));
                    room.setGameState("Turn");
                } else if (room.getGameState().equalsIgnoreCase("Turn")) {
                    if (deck != null && !deck.isEmpty()) communityCards.add(deck.remove(0));
                    room.setGameState("River");
                }
                room.setCommunityCards(communityCards);

                int activePlayers = 0;
                int playersWithChipsCount = 0;
                for (User player : room.getPlayers()) {
                    if (!player.getStatus().equals("Folded") && !player.getStatus().equals("Out")) {
                        activePlayers++;
                        if (player.getChips() > 0) playersWithChipsCount++;
                    }
                }

                if (activePlayers >= 2 && playersWithChipsCount <= 1) {
                    updateUI();
                    new android.os.Handler().postDelayed(() -> {
                        advanceGameRound(room);
                    }, 1500);
                } else {
                    for (User player : room.getPlayers()) {
                        if (!player.getStatus().equals("Folded") && !player.getStatus().equals("Out")) {
                            player.setStatus("Waiting");
                        }
                        player.setCurrentBet(0);
                    }
                    room.setCurrentBet(0);
                    room.setTurnIndex(getFirstActivePlayerIndex(room));
                    updateUI(); // מרעננים מסך אחרי פתיחת קלפים
                }
            }
        }
    }

    // ══════════════════════════════════════════════════════
    //  SHOWDOWN — חשיפת קלפים, חלוקת כסף, ואיפוס לסיבוב הבא
    // ══════════════════════════════════════════════════════
    private void handleShowdown(GameRoom room) {
        if (room.getPlayers() == null) return;

        java.util.HashMap<String, Integer> playerScores = new java.util.HashMap<>();
        for (User u : room.getPlayers()) {
            if (!u.getStatus().equals("Folded") && !u.getStatus().equals("Out")) {
                ArrayList<Card> sevenCards = new ArrayList<>();
                if (u.getHand() != null) sevenCards.addAll(u.getHand());
                if (room.getCommunityCards() != null) sevenCards.addAll(room.getCommunityCards());
                playerScores.put(u.getUid(), HandEvaluator.evaluateHand(sevenCards));
            } else {
                playerScores.put(u.getUid(), 0);
            }
        }

        ArrayList<User> sortedPlayers = new ArrayList<>(room.getPlayers());
        java.util.Collections.sort(sortedPlayers, (u1, u2) -> u1.getCurrentBet() - u2.getCurrentBet());

        ArrayList<User> winners = new ArrayList<>();
        int bestScore = 0;
        for (User u : room.getPlayers()) {
            if (!u.getStatus().equals("Folded") && !u.getStatus().equals("Out")) {
                int score = playerScores.get(u.getUid());
                if (score > bestScore) bestScore = score;
            }
        }
        for (User u : room.getPlayers()) {
            if (!u.getStatus().equals("Folded") && !u.getStatus().equals("Out") && playerScores.get(u.getUid()) == bestScore) {
                winners.add(u);
            }
        }

        int previousInvested = 0;
        for (int i = 0; i < sortedPlayers.size(); i++) {
            User current = sortedPlayers.get(i);
            int currentInvested = current.getCurrentBet();
            int layerAmount = currentInvested - previousInvested;

            if (layerAmount > 0) {
                int layerPot = 0;
                ArrayList<User> eligiblePlayers = new ArrayList<>();

                for (int j = i; j < sortedPlayers.size(); j++) {
                    layerPot += layerAmount;
                    if (!sortedPlayers.get(j).getStatus().equals("Folded") && !sortedPlayers.get(j).getStatus().equals("Out")) {
                        eligiblePlayers.add(sortedPlayers.get(j));
                    }
                }

                if (!eligiblePlayers.isEmpty() && layerPot > 0) {
                    int layerBest = 0;
                    for (User p : eligiblePlayers) {
                        int s = playerScores.get(p.getUid());
                        if (s > layerBest) layerBest = s;
                    }
                    ArrayList<User> layerWinners = new ArrayList<>();
                    for (User p : eligiblePlayers) {
                        if (playerScores.get(p.getUid()) == layerBest) layerWinners.add(p);
                    }
                    int splitAmount = layerPot / layerWinners.size();
                    for (User w : layerWinners) w.setChips(w.getChips() + splitAmount);
                }
                previousInvested = currentInvested;
            }
        }

        // שימו לב: נמחק כאן הקוד שמעדכן את הבנק האמיתי בפיירבייס! אנחנו באופליין.

        StringBuilder msg = new StringBuilder();
        if (winners.size() == 1) {
            msg.append("🏆 ").append(winners.get(0).getNickname()).append(" Wins! 🏆");
        } else {
            msg.append("🤝 Tie! ");
            for (User w : winners) msg.append(w.getNickname()).append(" ");
        }

        room.setWinnerName(msg.toString());
        room.setGameState("Showdown");
        updateUI(); // מציג את המנצח ואת הקלפים של כולם

        // מחכים 4 שניות, ואז מאפסים את הכל לסיבוב חדש
        new android.os.Handler().postDelayed(() -> {
            resetRoomForNextRound(room);
            room.setWinnerName("");
            updateUI();
        }, 4000);
    }

    // ══════════════════════════════════════════════════════
    //  פונקציות עזר קטנות למציאת השחקן הבא
    // ══════════════════════════════════════════════════════
    private int getNextActivePlayerIndex(GameRoom room) {
        int nextIndex = room.getTurnIndex() + 1;
        if (nextIndex >= room.getPlayers().size()) nextIndex = 0;
        while (room.getPlayers().get(nextIndex).getStatus().equals("Folded") || room.getPlayers().get(nextIndex).getStatus().equals("Out")) {
            nextIndex++;
            if (nextIndex >= room.getPlayers().size()) nextIndex = 0;
        }
        return nextIndex;
    }

    private int getFirstActivePlayerIndex(GameRoom room) {
        int firstPlayer = 0;
        while (firstPlayer < room.getPlayers().size() && (room.getPlayers().get(firstPlayer).getStatus().equals("Folded") || room.getPlayers().get(firstPlayer).getStatus().equals("Out"))) {
            firstPlayer++;
        }
        return firstPlayer;
    }


    private void playBotTurn()
    {

    }
}