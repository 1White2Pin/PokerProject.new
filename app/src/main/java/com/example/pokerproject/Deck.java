package com.example.pokerproject;

import java.util.ArrayList;
import java.util.Collections;

public class Deck
{
   private ArrayList<Card> cards = new ArrayList<>();
   public Deck() /** Constructor for the enum */
   {
       for (Suit suit : Suit.values())
       {
           for (Rank rank : Rank.values())
           {
               cards.add(new Card(suit, rank));
           }
       }

   }
   public void shuffle()
   {
       Collections.shuffle(cards);
   } /** Shuffles the deck */

   public Card drawCard()
   {
       if(cards.size() > 0)
       {
           Card card = cards.get(0);
           cards.remove(0);
           return card;
       }
       else
       {
           return null;
       }

   } /** Draws a card from the deck */


    


}
