package Models;

import java.util.ArrayList;

// מחלקה שמייצגת את חדר המשחק ואת כל המידע שצריך להסתנכרן בין השחקנים
public class GameRoom {

    // --- משתנים מזהים והגדרות חדר ---
    private String roomID;       // קוד החדר (המספר בן 4 הספרות שדרכו מצטרפים)
    private String hostId;       // מזהה המשתמש (UID) של מי שפתח את החדר
    private String hostName;     // השם של מי שפתח את החדר (מוצג בלובי)
    private boolean isPrivate = false; // האם החדר פרטי (עם סיסמה/מוסתר) או פתוח לכולם

    // --- נתוני קופה והימורים ---
    private int pot;             // הקופה המרכזית: סך כל הצ'יפים שהשחקנים שמו באמצע
    private int currentBet;      // "תג המחיר" הנוכחי בסיבוב: ההימור הכי גבוה שמישהו שם, שאר השחקנים חייבים להשוות (Call) אליו
    private int startingChips;   // כמות הצ'יפים ההתחלתית שמקבל כל שחקן שנכנס לחדר

    // --- נתוני קלפים ושחקנים ---
    private ArrayList<User> players;        // רשימת השחקנים שיושבים כרגע בשולחן
    private ArrayList<Card> communityCards; // קלפי הקהילה (הקלפים הפתוחים באמצע השולחן - פלופ, טרן, ריבר)
    private ArrayList<Card> deck;           // חבילת הקלפים המלאה של המשחק (ממנה שולפים קלפים לשחקנים ולשולחן)

    // --- ניהול תורות ומצב משחק ---
    private int turnIndex;       // מצביע על תורו של מי עכשיו (המיקום של השחקן ברשימת ה-players)
    private int dealerIndex = 0; // מצביע על מי ה"דילר" הנוכחי (כדי לדעת מי משלם בליינדים וממי מתחיל התור)
    private boolean isGameActive; // האם המשחק רץ כרגע (true) או שעדיין ממתינים לשחקנים בלובי (false)
    private String gameStatus;   // טקסט סטטוס כללי (למשל "Waiting for players...")
    private String gameState;    // השלב הספציפי של הסיבוב הנוכחי (PreFlop, Flop, Turn, River, Showdown)
    private String winnerName = ""; // שומר את שם המנצח בסוף הסיבוב כדי להציג אותו על המסך הגדול

    // ==========================================
    // בנאי ריק (Empty Constructor)
    // חובה בפיירבייס! כשפיירבייס מוריד נתונים מהענן, הוא קודם יוצר חדר ריק ואז ממלא אותו בעזרת ה-Setters.
    // ==========================================
    public GameRoom() {
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

    // ==========================================
    // בנאי מלא
    // משמש אותנו כשאנחנו לוחצים על "Create Room" ורוצים לאתחל חדר חדש מאפס
    // ==========================================
    public GameRoom(String roomID, String hostId, boolean isPrivate) {
        this.roomID = roomID;
        this.hostId = hostId;
        this.gameStatus = "Waiting for players...";
        this.pot = 0;
        this.players = new ArrayList<>();
        this.communityCards = new ArrayList<>();
        this.turnIndex = 0;
        this.isGameActive = false;
        this.startingChips = 500; // ברירת מחדל ישנה (אצלך בקוד כבר דאגנו למשוך את היתרה האמיתית)
        this.currentBet = 0;
        this.isPrivate = isPrivate;
    }

    // ==========================================
    // Getters & Setters
    // פעולות המאפשרות לקרוא (Get) ולעדכן (Set) את המשתנים הפרטיים של החדר.
    // פיירבייס משתמש בהם אוטומטית כשהוא שומר או קורא נתונים.
    // ==========================================

    public int getDealerIndex() { return dealerIndex; }
    public void setDealerIndex(int dealerIndex) { this.dealerIndex = dealerIndex; }

    public String getHostName(){ return hostName; }
    public void setHostName(String hostName){ this.hostName = hostName; }

    public String getWinnerName() { return winnerName; }
    public void setWinnerName(String winnerName) { this.winnerName = winnerName; }

    public int getCurrentBet() { return currentBet; }
    public void setCurrentBet(int currentBet) { this.currentBet = currentBet; }

    public String getRoomID() { return roomID; }
    public void setRoomID(String roomID) { this.roomID = roomID; }

    public ArrayList<Card> getCommunityCards() { return communityCards; }
    public void setCommunityCards(ArrayList<Card> communityCards) { this.communityCards = communityCards; }

    public ArrayList<User> getPlayers() { return players; }
    public void setPlayers(ArrayList<User> players) { this.players = players; }

    public String getHostId() { return hostId; }
    public void setHostId(String hostId) { this.hostId = hostId; }

    public String getGameStatus() { return gameStatus; }
    public void setGameStatus(String gameStatus) { this.gameStatus = gameStatus; }

    public int getPot() { return pot; }
    public void setPot(int pot) { this.pot = pot; }

    public int getTurnIndex() { return turnIndex; }
    public void setTurnIndex(int turnIndex) { this.turnIndex = turnIndex; }

    public boolean isGameActive() { return isGameActive; }
    public void setGameActive(boolean gameActive) { isGameActive = gameActive; }

    public int getStartingChips() { return startingChips; }
    public void setStartingChips(int startingChips) { this.startingChips = startingChips; }

    public ArrayList<Card> getDeck() { return deck; }
    public void setDeck(ArrayList<Card> deck) { this.deck = deck; }

    public String getGameState() { return gameState; }
    public void setGameState(String gameState) { this.gameState = gameState; }

    public boolean isPrivate() { return isPrivate; }
    public void setPrivate(boolean isPrivate) { this.isPrivate = isPrivate; }
}