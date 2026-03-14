package com.example.pokerproject;

public enum Rank
{
    ACE(1,"ACE",14),
    TWO(2,"TWO",2),
    THREE(3,"THREE",3),
    FOUR(4,"FOUR",4),
    FIVE(5,"FIVE",5),
    SIX(6,"SIX",6),
    SEVEN(7,"SEVEN",7),
    EIGHT(8,"EIGHT",8),
    NINE(9,"NINE",9),
    TEN(10,"TEN",10),
    JACK(11,"JACK",11),
    QUEEN(12,"QUEEN",12),
    KING(13,"KING",13);

    private final int value;
    private final String name;
    private final int points;

    Rank(int value, String name, int points)    /** Constructor for the enum */
    {
        this.value = value;
        this.name = name;
        this.points = points;

    }
    public int getValue()
    {
        return value;
    } /** Getter for value */
    public String getName()
    {
        return name;
    } /** Getter for name */
    public int getPoints()
    {
        return points;
    } /** Getter for points */









}
