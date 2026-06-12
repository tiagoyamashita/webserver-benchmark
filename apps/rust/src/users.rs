//! User persistence consumed from Kafka `create-user` events.

use sqlx::PgPool;

const SOURCE: &str = "src/users.rs";

pub struct CreatedUser {
    pub id: i64,
    pub name: String,
    pub email: String,
    pub created_at: String,
}

/// Inserts a user row from a Kafka `create-user` event.
pub async fn create_user_from_event(
    pool: &PgPool,
    name: &str,
    email: &str,
    request_id: Option<&str>,
) -> Result<CreatedUser, sqlx::Error> {
    let trimmed_name = name.trim();
    let trimmed_email = email.trim();
    let id_for_log = request_id.unwrap_or("");

    tracing::info!(
        source = SOURCE,
        controller = "create_user_from_event",
        request_id = id_for_log,
        name = %trimmed_name,
        email = %trimmed_email,
        "create_user_from_event inserting user"
    );

    let row = crate::db::insert_user(pool, trimmed_name, trimmed_email, request_id).await?;

    tracing::info!(
        source = SOURCE,
        controller = "create_user_from_event",
        request_id = id_for_log,
        id = row.id,
        name = %row.name,
        email = %row.email,
        "create_user_from_event succeeded"
    );

    Ok(CreatedUser {
        id: row.id,
        name: row.name,
        email: row.email,
        created_at: row.created_at,
    })
}
