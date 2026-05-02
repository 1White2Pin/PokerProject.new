package Activities;

import android.app.AlertDialog;
import android.os.Bundle;
import android.os.Handler;
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

import Models.Card;
import Models.Deck;
import Models.GameRoom;
import Helpers.HandEvaluator;
import Views.PokerGameView;
import com.example.pokerproject.R;
import Models.User;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Random;

public class OfflineGameActivity extends AppCompatActivity implements View.OnClickListener, SeekBar.OnSeekBarChangeListener {

    PokerGameView pokerGameView;
    Button btnFold, btnCheck, btnRaise;
    TextView tvPotSize;
    LinearLayout layoutActionButtons, layoutBetting;
    Button btnCancelBet, btnConfirmBet, btnAllIn;
    SeekBar sbBetAmount;
    TextView tvBetAmount;

    GameRoom room = new GameRoom();

    // בוט חושב בין 1.5 ל-3.5 שניות — כמו שחקן אמיתי
    private static final int BOT_THINK_MIN_MS = 1500;
    private static final int BOT_THINK_MAX_MS = 3500;
    // השהיה לפני פתיחת קלפי קהילה
    private static final int DEAL_DELAY_MS = 1200;
    // כמה שניות רואים את תוצאת ה-Showdown
    private static final int SHOWDOWN_DISPLAY_MS = 4000;

    private final Random random = new Random();

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

        btnFold    = findViewById(R.id.btnFold);
        btnCheck   = findViewById(R.id.btnCheck);
        btnRaise   = findViewById(R.id.btnRaise);
        tvPotSize  = findViewById(R.id.tvPotSize);
        pokerGameView      = findViewById(R.id.pokerGameView);
        layoutActionButtons = findViewById(R.id.layoutActionButtons);
        layoutBetting       = findViewById(R.id.layoutBetting);
        btnCancelBet  = findViewById(R.id.btnCancelBet);
        btnConfirmBet = findViewById(R.id.btnConfirmBet);
        sbBetAmount   = findViewById(R.id.sbBetAmount);
        tvBetAmount   = findViewById(R.id.tvBetAmount);
        btnAllIn      = findViewById(R.id.btnAllIn);

        btnFold.setOnClickListener(this);
        btnCheck.setOnClickListener(this);
        btnRaise.setOnClickListener(this);
        btnCancelBet.setOnClickListener(this);
        btnConfirmBet.setOnClickListener(this);
        btnAllIn.setOnClickListener(this);
        sbBetAmount.setOnSeekBarChangeListener(this);

        initLocalGame();
        resetRoomForNextRound(room);
        updateUI();
    }

    // ══════════════════════════════════════════════════════
    //  פעולות השחקן האנושי
    // ══════════════════════════════════════════════════════

    @Override
    public void onClick(View view) {
        if (view.getId() == R.id.btnFold) {
            setButtonsEnabled(false);
            for (User player : room.getPlayers()) {
                if (player.getUid().equals("My_UID")) {
                    player.setStatus("Folded");
                    break;
                }
            }
            advanceGameRound(room);
            updateUI();
        }

        else if (view.getId() == R.id.btnCheck) {
            setButtonsEnabled(false);
            for (User player : room.getPlayers()) {
                if (player.getUid().equals("My_UID")) {
                    if (room.getCurrentBet() > 0) {
                        int amountToCall = room.getCurrentBet() - player.getCurrentBet();
                        if (amountToCall > 0) {
                            if (amountToCall >= player.getChips()) amountToCall = player.getChips();
                            player.setChips(player.getChips() - amountToCall);
                            room.setPot(room.getPot() + amountToCall);
                            player.setCurrentBet(player.getCurrentBet() + amountToCall);
                        }
                    }
                    player.setStatus("Checked");
                    break;
                }
            }
            advanceGameRound(room);
            updateUI();
        }

        else if (view.getId() == R.id.btnRaise) {
            for (User player : room.getPlayers()) {
                if (player.getUid().equals("My_UID")) {
                    sbBetAmount.setMax(player.getChips() + player.getCurrentBet());
                    sbBetAmount.setProgress(room.getCurrentBet());
                    tvBetAmount.setText(String.valueOf(room.getCurrentBet()));
                    break;
                }
            }
            layoutActionButtons.setVisibility(View.GONE);
            layoutBetting.setVisibility(View.VISIBLE);
        }

        else if (view.getId() == R.id.btnCancelBet) {
            layoutActionButtons.setVisibility(View.VISIBLE);
            layoutBetting.setVisibility(View.GONE);
        }

        else if (view.getId() == R.id.btnConfirmBet) {
            int finalBetAmount = sbBetAmount.getProgress();
            if (finalBetAmount <= 0) {
                Toast.makeText(this, "Bet must be greater than 0", Toast.LENGTH_SHORT).show();
                return;
            }
            if (finalBetAmount < room.getCurrentBet()) {
                Toast.makeText(this, "Not enough to call!", Toast.LENGTH_SHORT).show();
                return;
            }
            setButtonsEnabled(false);
            for (User player : room.getPlayers()) {
                if (player.getUid().equals("My_UID")) {
                    int amountToAdd = finalBetAmount - player.getCurrentBet();
                    if (amountToAdd > player.getChips()) amountToAdd = player.getChips();
                    room.setCurrentBet(finalBetAmount);
                    room.setPot(room.getPot() + amountToAdd);
                    player.setChips(player.getChips() - amountToAdd);
                    player.setCurrentBet(finalBetAmount);
                    player.setStatus("Checked");
                } else if (!player.getStatus().equals("Folded") && !player.getStatus().equals("Out")) {
                    player.setStatus("Waiting");
                }
            }
            layoutBetting.setVisibility(View.GONE);
            layoutActionButtons.setVisibility(View.VISIBLE);
            advanceGameRound(room);
            updateUI();
        }

        else if (view.getId() == R.id.btnAllIn) {
            setButtonsEnabled(false);
            for (User player : room.getPlayers()) {
                if (player.getUid().equals("My_UID")) {
                    int allInAmount = player.getChips();
                    int totalBet = player.getCurrentBet() + allInAmount;
                    if (totalBet > room.getCurrentBet()) room.setCurrentBet(totalBet);
                    room.setPot(room.getPot() + allInAmount);
                    player.setChips(0);
                    player.setCurrentBet(totalBet);
                    player.setStatus("Checked");
                } else if (!player.getStatus().equals("Folded") && !player.getStatus().equals("Out")) {
                    player.setStatus("Waiting");
                }
            }
            layoutBetting.setVisibility(View.GONE);
            layoutActionButtons.setVisibility(View.VISIBLE);
            advanceGameRound(room);
            updateUI();
        }
    }

    // ══════════════════════════════════════════════════════
    //  UI
    // ══════════════════════════════════════════════════════

    private void updateUI() {
        tvPotSize.setText("Pot: " + room.getPot());
        pokerGameView.updateGame(room, "My_UID");

        if (room.getGameState().equals("Showdown")) {
            setButtonsEnabled(false);
            return;
        }

        User currentPlayer = room.getPlayers().get(room.getTurnIndex());

        if (currentPlayer.getUid().equals("My_UID")) {
            setButtonsEnabled(true);
            if (room.getCurrentBet() > 0 && room.getCurrentBet() > currentPlayer.getCurrentBet()) {
                btnCheck.setText("Call " + (room.getCurrentBet() - currentPlayer.getCurrentBet()));
            } else {
                btnCheck.setText("Check");
            }
        } else {
            setButtonsEnabled(false);
            // הבוט "חושב" — זמן אקראי בין MIN ל-MAX
            int thinkTime = BOT_THINK_MIN_MS + random.nextInt(BOT_THINK_MAX_MS - BOT_THINK_MIN_MS);
            new Handler().postDelayed(this::playBotTurn, thinkTime);
        }
    }

    private void setButtonsEnabled(boolean enabled) {
        btnFold.setEnabled(enabled);
        btnCheck.setEnabled(enabled);
        btnRaise.setEnabled(enabled);
    }

    // ══════════════════════════════════════════════════════
    //  בוט AI — קצת יותר חכם + מציג Toast
    // ══════════════════════════════════════════════════════

    private void playBotTurn() {
        User bot = room.getPlayers().get(room.getTurnIndex());

        if (bot.getStatus().equals("Folded") || bot.getStatus().equals("Out")) {
            advanceGameRound(room);
            updateUI();
            return;
        }

        int amountToCall = room.getCurrentBet() - bot.getCurrentBet();

        // הערכת חוזק יד
        ArrayList<Card> sevenHand = new ArrayList<>();
        if (bot.getHand() != null) sevenHand.addAll(bot.getHand());
        if (room.getCommunityCards() != null) sevenHand.addAll(room.getCommunityCards());
        int botScore = HandEvaluator.evaluateHand(sevenHand);

        // סף החלטה — יד חזקה: >3000, בינונית: >1500
        boolean strongHand  = botScore > 3000;
        boolean mediumHand  = botScore > 1500;
        boolean randomBluff = random.nextInt(10) == 0; // בלאף 10% מהזמן

        if (amountToCall == 0) {
            // אין הימור — Check או Raise
            if (strongHand || randomBluff) {
                // Raise בגובה 25%-75% מהפוט
                int raiseAmount = (int)(room.getPot() * (0.25 + random.nextDouble() * 0.5));
                raiseAmount = Math.max(200, Math.min(raiseAmount, bot.getChips()));
                int totalBet = bot.getCurrentBet() + raiseAmount;
                if (raiseAmount > 0 && totalBet > room.getCurrentBet()) {
                    room.setCurrentBet(totalBet);
                    room.setPot(room.getPot() + raiseAmount);
                    bot.setChips(bot.getChips() - raiseAmount);
                    bot.setCurrentBet(totalBet);
                    // כולם צריכים להגיב להעלאה
                    for (User p : room.getPlayers()) {
                        if (!p.getUid().equals(bot.getUid())
                                && !p.getStatus().equals("Folded")
                                && !p.getStatus().equals("Out")) {
                            p.setStatus("Waiting");
                        }
                    }
                    bot.setStatus("Checked");
                    Toast.makeText(this, bot.getNickname() + " raises " + raiseAmount, Toast.LENGTH_SHORT).show();
                } else {
                    bot.setStatus("Checked");
                    Toast.makeText(this, bot.getNickname() + " checks", Toast.LENGTH_SHORT).show();
                }
            } else {
                bot.setStatus("Checked");
                Toast.makeText(this, bot.getNickname() + " checks", Toast.LENGTH_SHORT).show();
            }
        } else {
            // יש הימור — Call, Raise, או Fold
            if (strongHand) {
                // Re-raise 30% מהזמן
                if (random.nextInt(10) < 3) {
                    int extra = (int)(room.getPot() * (0.3 + random.nextDouble() * 0.4));
                    extra = Math.min(extra, bot.getChips());
                    int totalBet = bot.getCurrentBet() + amountToCall + extra;
                    int pay = Math.min(amountToCall + extra, bot.getChips());
                    room.setPot(room.getPot() + pay);
                    bot.setChips(bot.getChips() - pay);
                    bot.setCurrentBet(bot.getCurrentBet() + pay);
                    if (bot.getCurrentBet() > room.getCurrentBet()) {
                        room.setCurrentBet(bot.getCurrentBet());
                        for (User p : room.getPlayers()) {
                            if (!p.getUid().equals(bot.getUid())
                                    && !p.getStatus().equals("Folded")
                                    && !p.getStatus().equals("Out")) {
                                p.setStatus("Waiting");
                            }
                        }
                    }
                    bot.setStatus("Checked");
                    Toast.makeText(this, bot.getNickname() + " re-raises!", Toast.LENGTH_SHORT).show();
                } else {
                    // Call
                    int actualCall = Math.min(amountToCall, bot.getChips());
                    bot.setChips(bot.getChips() - actualCall);
                    room.setPot(room.getPot() + actualCall);
                    bot.setCurrentBet(bot.getCurrentBet() + actualCall);
                    bot.setStatus("Checked");
                    Toast.makeText(this, bot.getNickname() + " calls " + actualCall, Toast.LENGTH_SHORT).show();
                }
            } else if (mediumHand || randomBluff) {
                // Call
                int actualCall = Math.min(amountToCall, bot.getChips());
                bot.setChips(bot.getChips() - actualCall);
                room.setPot(room.getPot() + actualCall);
                bot.setCurrentBet(bot.getCurrentBet() + actualCall);
                bot.setStatus("Checked");
                Toast.makeText(this, bot.getNickname() + " calls " + actualCall, Toast.LENGTH_SHORT).show();
            } else {
                // Fold
                bot.setStatus("Folded");
                Toast.makeText(this, bot.getNickname() + " folds", Toast.LENGTH_SHORT).show();
            }
        }

        advanceGameRound(room);
        updateUI();
    }

    // ══════════════════════════════════════════════════════
    //  מנוע המשחק
    // ══════════════════════════════════════════════════════

    private void advanceGameRound(GameRoom room) {
        boolean isRoundComplete = true;
        for (User player : room.getPlayers()) {
            if (!player.getStatus().equals("Checked")
                    && !player.getStatus().equals("Folded")
                    && !player.getStatus().equals("Out")) {
                isRoundComplete = false;
                break;
            }
        }

        if (!isRoundComplete) {
            room.setTurnIndex(getNextActivePlayerIndex(room));
            return;
        }

        // הסיבוב נגמר — מתקדמים לשלב הבא
        if (room.getGameState().equalsIgnoreCase("River")) {
            handleShowdown(room);
        } else {
            // השהייה לפני חשיפת קלפי קהילה
            new Handler().postDelayed(() -> {
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

                // בדיקה: כמה שחקנים פעילים נשארו
                int activePlayers = 0, playersWithChips = 0;
                for (User p : room.getPlayers()) {
                    if (!p.getStatus().equals("Folded") && !p.getStatus().equals("Out")) {
                        activePlayers++;
                        if (p.getChips() > 0) playersWithChips++;
                    }
                }

                if (activePlayers >= 2 && playersWithChips <= 1) {
                    // All-in situation — ממשיכים אוטומטית
                    updateUI();
                    new Handler().postDelayed(() -> advanceGameRound(room), DEAL_DELAY_MS);
                } else {
                    // איפוס הימורי סיבוב
                    for (User p : room.getPlayers()) {
                        if (!p.getStatus().equals("Folded") && !p.getStatus().equals("Out")) {
                            p.setStatus("Waiting");
                        }
                        p.setCurrentBet(0);
                    }
                    room.setCurrentBet(0);
                    room.setTurnIndex(getFirstActivePlayerIndex(room));
                    updateUI();
                }
            }, DEAL_DELAY_MS);
        }
    }

    private void handleShowdown(GameRoom room) {
        if (room.getPlayers() == null) return;

        // חישוב ציונים
        HashMap<String, Integer> playerScores = new HashMap<>();
        for (User u : room.getPlayers()) {
            if (!u.getStatus().equals("Folded") && !u.getStatus().equals("Out")) {
                ArrayList<Card> cards = new ArrayList<>();
                if (u.getHand() != null) cards.addAll(u.getHand());
                if (room.getCommunityCards() != null) cards.addAll(room.getCommunityCards());
                playerScores.put(u.getUid(), HandEvaluator.evaluateHand(cards));
            } else {
                playerScores.put(u.getUid(), 0);
            }
        }

        // מציאת המנצחים
        ArrayList<User> winners = new ArrayList<>();
        int bestScore = 0;
        for (User u : room.getPlayers()) {
            if (!u.getStatus().equals("Folded") && !u.getStatus().equals("Out")) {
                int score = playerScores.get(u.getUid());
                if (score > bestScore) bestScore = score;
            }
        }
        for (User u : room.getPlayers()) {
            if (!u.getStatus().equals("Folded") && !u.getStatus().equals("Out")
                    && playerScores.get(u.getUid()) == bestScore) {
                winners.add(u);
            }
        }

        // חלוקת הפוט (Side Pots)
        ArrayList<User> sortedPlayers = new ArrayList<>(room.getPlayers());
        sortedPlayers.sort((u1, u2) -> u1.getCurrentBet() - u2.getCurrentBet());
        int previousInvested = 0;
        for (int i = 0; i < sortedPlayers.size(); i++) {
            int currentInvested = sortedPlayers.get(i).getCurrentBet();
            int layerAmount = currentInvested - previousInvested;
            if (layerAmount > 0) {
                int layerPot = 0;
                ArrayList<User> eligible = new ArrayList<>();
                for (int j = i; j < sortedPlayers.size(); j++) {
                    layerPot += layerAmount;
                    if (!sortedPlayers.get(j).getStatus().equals("Folded")
                            && !sortedPlayers.get(j).getStatus().equals("Out")) {
                        eligible.add(sortedPlayers.get(j));
                    }
                }
                if (!eligible.isEmpty() && layerPot > 0) {
                    int lBest = 0;
                    for (User p : eligible) {
                        int s = playerScores.get(p.getUid());
                        if (s > lBest) lBest = s;
                    }
                    ArrayList<User> lWinners = new ArrayList<>();
                    for (User p : eligible) {
                        if (playerScores.get(p.getUid()) == lBest) lWinners.add(p);
                    }
                    int split = layerPot / lWinners.size();
                    for (User w : lWinners) w.setChips(w.getChips() + split);
                }
                previousInvested = currentInvested;
            }
        }

        // הכנת הודעת מנצח
        StringBuilder msg = new StringBuilder();
        boolean iWon = false;
        if (winners.size() == 1) {
            msg.append("🏆  ").append(winners.get(0).getNickname()).append("  wins!");
            if (winners.get(0).getUid().equals("My_UID")) iWon = true;
        } else {
            msg.append("🤝  Tie!\n");
            for (User w : winners) {
                msg.append(w.getNickname()).append("\n");
                if (w.getUid().equals("My_UID")) iWon = true;
            }
        }
        msg.append("\nPot: ").append(room.getPot());

        room.setWinnerName(msg.toString());
        room.setGameState("Showdown");
        updateUI();

        String title = iWon ? "You Win! 🎉" : "Round Over";
        String finalMsg = msg.toString();

        // מציגים דיאלוג אחרי קצת השהייה (כדי לראות את הקלפים)
        new Handler().postDelayed(() -> {
            if (isFinishing()) return;
            new AlertDialog.Builder(this)
                    .setTitle(title)
                    .setMessage(finalMsg)
                    .setCancelable(false)
                    .setPositiveButton("Next Round", (dialog, which) -> {
                        resetRoomForNextRound(room);
                        room.setWinnerName("");
                        updateUI();
                    })
                    .show();
        }, SHOWDOWN_DISPLAY_MS);
    }

    // ══════════════════════════════════════════════════════
    //  אתחול ועזרים
    // ══════════════════════════════════════════════════════

    private void initLocalGame() {
        ArrayList<User> players = new ArrayList<>();
        players.add(new User("My_UID",  "Me",       1000));
        players.add(new User("BOT_1",   "Bot Mike",  1000));
        players.add(new User("BOT_2",   "Bot Sarah", 1000));
        players.add(new User("BOT_3",   "Bot John",  1000));
        room.setPlayers(players);
    }

    private void resetRoomForNextRound(GameRoom room) {
        room.setGameState("PreFlop");
        if (room.getCommunityCards() != null) room.getCommunityCards().clear();

        for (User player : room.getPlayers()) {
            player.setChipsBeforeRound(player.getChips());
            player.setCurrentBet(0);
            player.setHand(new ArrayList<>());
            if (player.getChips() <= 0) player.setStatus("Out");
            else                         player.setStatus("Waiting");
        }

        room.setDealerIndex((room.getDealerIndex() + 1) % room.getPlayers().size());

        int sbIndex, bbIndex, firstToAct;
        if (room.getPlayers().size() == 2) {
            sbIndex    = room.getDealerIndex();
            bbIndex    = (room.getDealerIndex() + 1) % room.getPlayers().size();
            firstToAct = sbIndex;
        } else {
            sbIndex    = (room.getDealerIndex() + 1) % room.getPlayers().size();
            bbIndex    = (room.getDealerIndex() + 2) % room.getPlayers().size();
            firstToAct = (bbIndex + 1) % room.getPlayers().size();
        }

        int sbAmount = 100, bbAmount = 200;

        User sb = room.getPlayers().get(sbIndex);
        int actualSb = Math.min(sbAmount, sb.getChips());
        sb.setChips(sb.getChips() - actualSb);
        sb.setCurrentBet(actualSb);

        User bb = room.getPlayers().get(bbIndex);
        int actualBb = Math.min(bbAmount, bb.getChips());
        bb.setChips(bb.getChips() - actualBb);
        bb.setCurrentBet(actualBb);

        room.setPot(actualSb + actualBb);
        room.setCurrentBet(actualBb);
        room.setTurnIndex(firstToAct);

        // חלוקת קלפים
        Deck newDeck = new Deck();
        newDeck.shuffle();
        ArrayList<Card> deckList = new ArrayList<>();
        Card c;
        while ((c = newDeck.drawCard()) != null) deckList.add(c);
        room.setDeck(deckList);

        for (User player : room.getPlayers()) {
            if (!player.getStatus().equals("Out")) {
                ArrayList<Card> hand = new ArrayList<>();
                hand.add(deckList.remove(0));
                hand.add(deckList.remove(0));
                player.setHand(hand);
            }
        }
    }

    private int getNextActivePlayerIndex(GameRoom room) {
        int next = room.getTurnIndex() + 1;
        if (next >= room.getPlayers().size()) next = 0;
        while (room.getPlayers().get(next).getStatus().equals("Folded")
                || room.getPlayers().get(next).getStatus().equals("Out")) {
            next++;
            if (next >= room.getPlayers().size()) next = 0;
        }
        return next;
    }

    private int getFirstActivePlayerIndex(GameRoom room) {
        int i = 0;
        while (i < room.getPlayers().size()
                && (room.getPlayers().get(i).getStatus().equals("Folded")
                || room.getPlayers().get(i).getStatus().equals("Out"))) {
            i++;
        }
        return i;
    }

    @Override public void onProgressChanged(SeekBar seekBar, int i, boolean b) {
        tvBetAmount.setText(String.valueOf(i));
    }
    @Override public void onStartTrackingTouch(SeekBar seekBar) {}
    @Override public void onStopTrackingTouch(SeekBar seekBar) {}
}