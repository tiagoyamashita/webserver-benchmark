use bcrypt::{hash, verify, BcryptError, DEFAULT_COST};

pub fn hash_password(raw: &str) -> Result<String, BcryptError> {
    hash(raw, DEFAULT_COST)
}

pub fn verify_password(raw: &str, password_hash: &str) -> bool {
    if raw.is_empty() || password_hash.is_empty() {
        return false;
    }
    verify(raw, password_hash).unwrap_or(false)
}
