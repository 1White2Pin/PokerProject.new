package Helpers;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;

import Models.Card;
import Models.Suit;

public class HandEvaluator {

    // הפונקציה הראשית שמקבלת את כל 7 הקלפים (2 של השחקן + 5 מהשולחן)
    public static int evaluateHand(List<Card> cards) {
        // הגנת קריסה: אם אין מספיק קלפים כדי לייצר יד של 5, נחזיר ציון 0
        if (cards == null || cards.size() < 5) return 0;

        int bestScore = 0;

        // לולאות מקוננות: עוברות על כל הקומבינציות האפשריות של 5 קלפים מתוך ה-7
        // (במתמטיקה קוראים לזה 7 מעל 5, ויש בדיוק 21 קומבינציות כאלו)
        for (int i = 0; i < cards.size(); i++) {
            for (int j = i + 1; j < cards.size(); j++) {
                for (int k = j + 1; k < cards.size(); k++) {
                    for (int l = k + 1; l < cards.size(); l++) {
                        for (int m = l + 1; m < cards.size(); m++) {

                            // יוצרים יד חדשה של 5 קלפים מתוך הקומבינציה הנוכחית
                            List<Card> hand = new ArrayList<>();
                            hand.add(cards.get(i));
                            hand.add(cards.get(j));
                            hand.add(cards.get(k));
                            hand.add(cards.get(l));
                            hand.add(cards.get(m));

                            // שולחים את ה-5 קלפים להערכה, ושומרים את הציון הגבוה ביותר שמצאנו
                            int score = evaluate5Cards(hand);
                            if (score > bestScore) {
                                bestScore = score;
                            }
                        }
                    }
                }
            }
        }
        return bestScore; // מחזירים את היד הכי חזקה שאפשר להרכיב
    }

    // פונקציית הליבה: בודקת יד ספציפית של 5 קלפים ונותנת לה ציון
    private static int evaluate5Cards(List<Card> hand) {
        // 1. מיון הקלפים מהגדול לקטן (למשל: אס, מלך, שמונה, שלוש, שתיים)
        Collections.sort(hand, (c1, c2) -> c2.getRank().getPoints() - c1.getRank().getPoints());

        // 2. בדיקת פלוש (צבע): האם לכל 5 הקלפים יש את אותה צורה?
        boolean isFlush = true;
        Suit firstSuit = hand.get(0).getSuit();
        for (Card card : hand) {
            if (card.getSuit() != firstSuit) {
                isFlush = false;
                break;
            }
        }

        // 3. בדיקת רצף (קנטה): האם כל קלף קטן בנקודה אחת בדיוק מהקלף שלפניו?
        boolean isStraight = true;
        for (int i = 0; i < hand.size() - 1; i++) {
            if (hand.get(i).getRank().getPoints() - 1 != hand.get(i + 1).getRank().getPoints()) {
                isStraight = false;
                break;
            }
        }

        // מקרה קצה בפוקר: רצף נמוך (A-2-3-4-5) - האס מתפקד כקלף בעל ערך 1
        boolean isLowStraight = false;
        if (hand.get(0).getRank().getPoints() == 14 && // אס
                hand.get(1).getRank().getPoints() == 5 &&
                hand.get(2).getRank().getPoints() == 4 &&
                hand.get(3).getRank().getPoints() == 3 &&
                hand.get(4).getRank().getPoints() == 2) {
            isStraight = true;
            isLowStraight = true; // נסמן שזה רצף נמוך כדי לא לתת לאס כאן ניקוד של 14
        }

        // 4. ספירת הופעות (כמה פעמים מופיע כל מספר?) בעזרת מילון (HashMap)
        // לדוגמה: אם יש שני מלכים, המילון ישמור [מלך -> 2]
        HashMap<Integer, Integer> counts = new HashMap<>();
        for (Card card : hand) {
            int p = card.getRank().getPoints();
            counts.put(p, counts.getOrDefault(p, 0) + 1);
        }

        // 5. ניתוח המילון: האם יש לנו זוגות? שלישיות? רביעייה?
        int maxCount = 0; // כמה פעמים מופיע הקלף הכי נפוץ ביד
        int pairs = 0;    // כמות הזוגות
        int quadValue = 0, tripleValue = 0, pair1Value = 0, pair2Value = 0;

        for (int val : counts.keySet()) {
            int count = counts.get(val);
            if (count > maxCount) maxCount = count;

            if (count == 4) quadValue = val;           // מצאנו רביעייה
            if (count == 3) tripleValue = val;         // מצאנו שלישייה
            if (count == 2) {                          // מצאנו זוג
                pairs++;
                if (pair1Value == 0) {
                    pair1Value = val;
                } else {
                    pair2Value = val;
                    // מוודאים ש-pair1 תמיד ישמור את הזוג הגבוה יותר מבין השניים
                    if (pair2Value > pair1Value) {
                        int temp = pair1Value;
                        pair1Value = pair2Value;
                        pair2Value = temp;
                    }
                }
            }
        }

        // 6. חישוב הציון הסופי במרווחים של 10 מיליון
        // המרווחים הענקיים מבטיחים שיד טובה יותר (כמו פלוש) תמיד תנצח יד פחות טובה (כמו קנטה),
        // לא משנה איזה קלפי קיקר יש לשחקן השני.
        int score = 0;

        if (isFlush && isStraight) {
            // דירוג 9: קנטה פלוש (Straight Flush)
            score = 90000000 + (isLowStraight ? 5 : hand.get(0).getRank().getPoints());
        }
        else if (maxCount == 4) {
            // דירוג 8: פוקר / רביעייה (Four of a Kind)
            score = 80000000 + (quadValue * 100000);
            for (Card c : hand) {
                if (c.getRank().getPoints() != quadValue) {
                    score += c.getRank().getPoints() * 1000; // הקיקר (הקלף החמישי)
                }
            }
        }
        else if (maxCount == 3 && pairs == 1) {
            // דירוג 7: פול האוס (Full House)
            score = 70000000 + (tripleValue * 100000) + (pair1Value * 1000);
        }
        else if (isFlush) {
            // דירוג 6: צבע / פלוש (Flush)
            score = 60000000 + getKickersScore(hand);
        }
        else if (isStraight) {
            // דירוג 5: רצף / קנטה (Straight)
            score = 50000000 + (isLowStraight ? 5 : hand.get(0).getRank().getPoints());
        }
        else if (maxCount == 3) {
            // דירוג 4: שלישייה (Three of a Kind)
            score = 40000000 + (tripleValue * 100000);
            int mult = 1000;
            for (Card c : hand) {
                if (c.getRank().getPoints() != tripleValue) {
                    score += c.getRank().getPoints() * mult;
                    mult /= 100;
                }
            }
        }
        else if (pairs == 2) {
            // דירוג 3: שני זוגות (Two Pair)
            score = 30000000 + (pair1Value * 100000) + (pair2Value * 1000);
            for (Card c : hand) {
                if (c.getRank().getPoints() != pair1Value && c.getRank().getPoints() != pair2Value) {
                    score += c.getRank().getPoints() * 10; // הקיקר
                }
            }
        }
        else if (pairs == 1) {
            // דירוג 2: זוג אחד (One Pair)
            score = 20000000 + (pair1Value * 100000);
            int mult = 1000;
            for (Card c : hand) {
                if (c.getRank().getPoints() != pair1Value) {
                    score += c.getRank().getPoints() * mult;
                    mult /= 100;
                }
            }
        }
        else {
            // דירוג 1: קלף גבוה (High Card)
            score = 10000000 + getKickersScore(hand);
        }

        return score;
    }

    // פונקציית עזר: נותנת ניקוד מדויק לקיקרים (הקלפים הנותרים ביד שלא שייכים לקומבינציה המרכזית)
    // היא עוברת על הקלפים מהגדול לקטן, ונותנת לכל אחד משקל קטן פי 100 מהקודם כדי לשבור שוויונות במדויק.
    private static int getKickersScore(List<Card> hand) {
        int kickerScore = 0;
        int multiplier = 100000; // מכפיל שמתחיל ב-100 אלף כדי לא לעקוף לעולם את פער ה-10 מיליון
        for (Card c : hand) {
            kickerScore += c.getRank().getPoints() * multiplier;
            multiplier /= 100; // יורד בהדרגה: 100,000 -> 1,000 -> 10 -> 0
        }
        return kickerScore;
    }
}