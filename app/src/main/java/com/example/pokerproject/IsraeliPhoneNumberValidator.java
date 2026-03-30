package com.example.pokerproject;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class IsraeliPhoneNumberValidator {
    public static boolean isValidIsraeliPhoneNumber(String phoneNumber) {
        if (phoneNumber == null || phoneNumber.isEmpty()) {
            return false;
        }

        // Remove any non-digit characters (e.g., spaces, hyphens)
        String cleanedPhoneNumber = phoneNumber.replaceAll("[^\\d]", "");

        // Regular expression patterns for Israeli phone numbers
        // Pattern 1: 05X-XXXXXXX (Mobile numbers)
        // Pattern 2: 02-XXXXXXX (Jerusalem area code)
        // Pattern 3: 03-XXXXXXX (Central area code)
        // Pattern 4: 04-XXXXXXX (Haifa area code)
        // Pattern 5: 08-XXXXXXX (Southern area code)
        // Pattern 6: 09-XXXXXXX (Sharon area code)
        // Pattern 7: 07X-XXXXXXX (Other landlines and new mobile numbers)

        String regex = "^(0(5\\d|2|3|4|8|9|7[2-9])([\\d]{7}))$";

        // Create a Pattern object
        Pattern pattern = Pattern.compile(regex);

        // Create a Matcher object
        Matcher matcher = pattern.matcher(cleanedPhoneNumber);

        // Check if the phone number matches the pattern
        return matcher.matches();
    }
}
