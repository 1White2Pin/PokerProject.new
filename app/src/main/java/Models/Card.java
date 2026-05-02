package Models;

public class Card {
    // 1. מחקנו את ה-final
    private Suit suit;
    private Rank rank;

    public Card() {
        // בנאי ריק - אפשר להשאיר ריק לגמרי או לתת ברירת מחדל
    }

    public Card(Suit suit, Rank rank) {
        this.suit = suit;
        this.rank = rank;
    }

    public Suit getSuit() { return suit; }
    public Rank getRank() { return rank; }

    // 2. הוספנו Setters (חשוב לפיירבייס!)
    public void setSuit(Suit suit) { this.suit = suit; }
    public void setRank(Rank rank) { this.rank = rank; }

    // ... שאר הפונקציות (toString, getImageResourceName) נשארות אותו דבר
    public String getImageResourceName() {
        return suit.name().toLowerCase() + "_" + rank.getName().toLowerCase();
    }
    // ...
}