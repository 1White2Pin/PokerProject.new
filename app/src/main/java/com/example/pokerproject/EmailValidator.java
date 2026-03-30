package com.example.pokerproject;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class EmailValidator {
    private static final String EMAIL_REGEX =
            "^(?=.{1,64}@)[A-Za-z0-9_-]+(\\.[A-Za-z0-9_-]+)*@"
                    + "[^-][A-Za-z0-9-]+(\\.[A-Za-z0-9-]+)*(\\.[A-Za-z]{2,})$";

    private static final Pattern EMAIL_PATTERN = Pattern.compile(EMAIL_REGEX);

    /**
     * Checks if the given email address is valid.
     *
     * @param email The email address to validate.
     * @return True if the email is valid, false otherwise.
     */
    public static boolean isValidEmail(String email) {
        if (email == null || email.isEmpty()) {
            return false; // Empty or null email is not valid
        }

        Matcher matcher = EMAIL_PATTERN.matcher(email);
        return matcher.matches();
    }

    // Example usage
    public static void main(String[] args) {
        String[] emails = {
                "test@example.com",            // Valid
                "test.user@example.co.uk",      // Valid
                "test_user123@my-domain.com",   // Valid
                "test..user@example.com",       // Invalid
                "test@.com",                    // Invalid
                "@example.com",                 // Invalid
                "test@example.",               // Invalid
                "test@example..com",               // Invalid
                "test",                         // Invalid
                "test@example",                         // Invalid
                "",                             // Invalid
                null                            // Invalid
        };

        for (String email : emails) {
            System.out.println(email + " : " + isValidEmail(email));
        }
    }
}
