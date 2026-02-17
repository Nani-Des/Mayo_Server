package com.mayo.auth;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

/**
 * Utility to generate BCrypt hashes for testing
 */
public class BcryptHashGenerator {
    public static void main(String[] args) {
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
        String rawPassword = "password123";
        String encodedPassword = encoder.encode(rawPassword);
        System.out.println("Raw Password: " + rawPassword);
        System.out.println("BCrypt Hash: " + encodedPassword);
        
        // Verify the hash
        boolean matches = encoder.matches(rawPassword, encodedPassword);
        System.out.println("Verification: " + matches);
    }
}
