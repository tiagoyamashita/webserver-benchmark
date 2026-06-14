package com.example.demo.auth;

import org.mindrot.jbcrypt.BCrypt;
import org.springframework.stereotype.Component;

/** BCrypt password hashing (compatible with standard $2a$ hashes). */
@Component
public class PasswordHasher {

  public String encode(String rawPassword) {
    return BCrypt.hashpw(rawPassword, BCrypt.gensalt());
  }

  public boolean matches(String rawPassword, String passwordHash) {
    if (rawPassword == null || passwordHash == null || passwordHash.isBlank()) {
      return false;
    }
    return BCrypt.checkpw(rawPassword, passwordHash);
  }
}
