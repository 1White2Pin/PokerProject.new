package Helpers;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;

import Models.Card;
import Models.Suit;

public class HandEvaluator {

    public static int evaluateHand(List<Card> cards) {
        // הגנת קריסה: אם אין מספיק קלפים, נחזיר 0
        if (cards == null || cards.size() < 5) return 0;

        int bestScore = 0;

        for (int i = 0; i < cards.size(); i++) {
            for (int j = i + 1; j < cards.size(); j++) {
                for (int k = j + 1; k < cards.size(); k++) {
                    for (int l = k + 1; l < cards.size(); l++) {
                        for (int m = l + 1; m < cards.size(); m++) {

                            List<Card> hand = new ArrayList<>();
                            hand.add(cards.get(i));
                            hand.add(cards.get(j));
                            hand.add(cards.get(k));
                            hand.add(cards.get(l));
                            hand.add(cards.get(m));

                            int score = evaluate5Cards(hand);
                            if (score > bestScore) {
                                bestScore = score;
                            }
                        }
                    }
                }
            }
        }
        return bestScore;
    }

    private static int evaluate5Cards(List<Card> hand) {
        // 1. מיון מהגדול לקטן
        Collections.sort(hand, (c1, c2) -> c2.getRank().getPoints() - c1.getRank().getPoints());

        // 2. בדיקת פלוש (צבע)
        boolean isFlush = true;
        Suit firstSuit = hand.get(0).getSuit();
        for (Card card : hand) {
            if (card.getSuit() != firstSuit) {
                isFlush = false;
                break;
            }
        }

        // 3. בדיקת רצף
        boolean isStraight = true;
        for (int i = 0; i < hand.size() - 1; i++) {
            if (hand.get(i).getRank().getPoints() - 1 != hand.get(i + 1).getRank().getPoints()) {
                isStraight = false;
                break;
            }
        }

        boolean isLowStraight = false;
        if (hand.get(0).getRank().getPoints() == 14 &&
                hand.get(1).getRank().getPoints() == 5 &&
                hand.get(2).getRank().getPoints() == 4 &&
                hand.get(3).getRank().getPoints() == 3 &&
                hand.get(4).getRank().getPoints() == 2) {
            isStraight = true;
            isLowStraight = true; // נסמן שזה רצף שמתחיל מ-5 כדי שהניקוד יהיה מדויק
        }

        // 4. ספירת זוגות/שלישיות בעזרת מילון
        HashMap<Integer, Integer> counts = new HashMap<>();
        for (Card card : hand) {
            int p = card.getRank().getPoints();
            counts.put(p, counts.getOrDefault(p, 0) + 1);
        }

        // 5. חילוץ המידע מהמילון - מי הקלפים החשובים שלנו?
        int maxCount = 0;
        int pairs = 0;
        int quadValue = 0, tripleValue = 0, pair1Value = 0, pair2Value = 0;

        for (int val : counts.keySet()) {
            int count = counts.get(val);
            if (count > maxCount) maxCount = count;

            if (count == 4) quadValue = val;           // מי הרביעייה?
            if (count == 3) tripleValue = val;         // מי השלישייה?
            if (count == 2) {                          // מי הזוג?
                pairs++;
                if (pair1Value == 0) {
                    pair1Value = val;
                } else {
                    pair2Value = val;
                    // מוודאים ש-pair1 יהיה תמיד הזוג הגבוה יותר
                    if (pair2Value > pair1Value) {
                        int temp = pair1Value;
                        pair1Value = pair2Value;
                        pair2Value = temp;
                    }
                }
            }
        }

        // 6. חישוב הניקוד הסופי (Score)
        int score = 0;

        if (isFlush && isStraight) { // קנטה פלוש
            // מתחיל מ-9 מיליון + הקלף הכי גבוה
            score = 9000000 + (isLowStraight ? 5 : hand.get(0).getRank().getPoints());
        }
        else if (maxCount == 4) { // רביעייה
            score = 8000000 + (quadValue * 1000);
            for (Card c : hand) if (c.getRank().getPoints() != quadValue) score += c.getRank().getPoints(); // קיקר
        }
        else if (maxCount == 3 && pairs == 1) { // פול האוס
            score = 7000000 + (tripleValue * 1000) + (pair1Value * 10);
        }
        else if (isFlush) { // צבע
            score = 6000000 + getKickersScore(hand);
        }
        else if (isStraight) { // קנטה
            score = 5000000 + (isLowStraight ? 5 : hand.get(0).getRank().getPoints());
        }
        else if (maxCount == 3) { // שלישייה
            score = 4000000 + (tripleValue * 10000);
            int mult = 100;
            for (Card c : hand) {
                if (c.getRank().getPoints() != tripleValue) {
                    score += c.getRank().getPoints() * mult;
                    mult /= 100;
                }
            }
        }
        else if (pairs == 2) { // שני זוגות
            score = 3000000 + (pair1Value * 10000) + (pair2Value * 100);
            for (Card c : hand) {
                if (c.getRank().getPoints() != pair1Value && c.getRank().getPoints() != pair2Value) {
                    score += c.getRank().getPoints();
                }
            }
        }
        else if (pairs == 1) { // זוג אחד
            score = 2000000 + (pair1Value * 100000);
            int mult = 1000;
            for (Card c : hand) {
                if (c.getRank().getPoints() != pair1Value) {
                    score += c.getRank().getPoints() * mult;
                    mult /= 100;
                }
            }
        }
        else { // קלף גבוה (High Card)
            score = 1000000 + getKickersScore(hand);
        }

        return score;
    }

    // פונקציית עזר לפלוש וקלף גבוה: נותנת ניקוד מדויק ל-5 קלפים שונים
    private static int getKickersScore(List<Card> hand) {
        int kickerScore = 0;
        int multiplier = 10000000; // מכפיל שמתחיל במספר עצום
        for (Card c : hand) {
            kickerScore += c.getRank().getPoints() * multiplier;
            multiplier /= 100; // כל קלף מקבל משקל קטן פי 100 מהקודם
        }
        return kickerScore;
    }
}