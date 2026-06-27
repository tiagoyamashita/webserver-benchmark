use chrono::NaiveDateTime;
use sqlx::postgres::PgPoolOptions;
use sqlx::PgPool;

fn format_created_at(ts: NaiveDateTime) -> String {
    // Schema uses TIMESTAMP (no tz); treat stored values as UTC for JSON (matches other apps).
    ts.and_utc().to_rfc3339()
}

/// Same env vars as the Java `postgres` profile / root Compose `java` service.
pub fn database_url_from_env() -> Option<String> {
    let host = std::env::var("DB_HOST").ok()?;
    if host.trim().is_empty() {
        return None;
    }
    let port = std::env::var("DB_PORT").unwrap_or_else(|_| "5432".into());
    let dbname = std::env::var("DB_NAME").unwrap_or_else(|_| "demo".into());
    let user = std::env::var("DB_USERNAME").unwrap_or_else(|_| "postgres".into());
    let password = std::env::var("DB_PASSWORD").unwrap_or_else(|_| "postgres".into());
    Some(format!(
        "postgres://{user}:{password}@{host}:{port}/{dbname}"
    ))
}

pub async fn connect_pool() -> Result<PgPool, sqlx::Error> {
    let url = database_url_from_env().ok_or(sqlx::Error::Configuration(
        "DB_HOST not set (required for POST /api/items)".into(),
    ))?;
    PgPoolOptions::new()
        .max_connections(5)
        .connect(&url)
        .await
}

pub struct InsertedItem {
    pub id: i64,
    pub name: String,
    pub created_at: String,
}

pub struct ItemRow {
    pub id: i64,
    pub name: String,
    pub created_at: String,
}

pub async fn stamp_application_name(
    conn: &mut sqlx::PgConnection,
    request_id: Option<&str>,
) -> Result<(), sqlx::Error> {
    let app_name = match request_id {
        Some(id) => crate::request_id::postgres_application_name("webserver-benchmark-rust", id),
        None => "webserver-benchmark-rust".to_string(),
    };
    sqlx::query("SELECT set_config('application_name', $1, false)")
        .bind(app_name)
        .execute(conn)
        .await?;
    Ok(())
}

pub async fn list_items(pool: &PgPool, request_id: Option<&str>) -> Result<Vec<ItemRow>, sqlx::Error> {
    let mut conn = pool.acquire().await?;
    stamp_application_name(&mut conn, request_id).await?;
    let rows: Vec<(i64, String, NaiveDateTime)> = sqlx::query_as(
        "SELECT id, name, created_at FROM items ORDER BY id",
    )
    .fetch_all(&mut *conn)
    .await?;

    Ok(rows
        .into_iter()
        .map(|(id, name, created_at)| ItemRow {
            id,
            name,
            created_at: format_created_at(created_at),
        })
        .collect())
}

pub struct InsertedUser {
    pub id: i64,
    pub name: String,
    pub email: String,
    pub created_at: String,
}

pub struct UserRow {
    pub id: i64,
    pub name: String,
    pub email: String,
}

pub struct UserAuthRow {
    pub id: i64,
    pub name: String,
    pub email: String,
    pub password_hash: Option<String>,
}

pub async fn find_user_by_email(pool: &PgPool, email: &str) -> Result<Option<UserRow>, sqlx::Error> {
    let row: Option<(i64, String, String)> = sqlx::query_as(
        "SELECT id, name, email FROM users WHERE LOWER(email) = LOWER($1) LIMIT 1",
    )
    .bind(email)
    .fetch_optional(pool)
    .await?;
    Ok(row.map(|(id, name, email)| UserRow { id, name, email }))
}

pub async fn find_user_auth_by_email(
    pool: &PgPool,
    email: &str,
) -> Result<Option<UserAuthRow>, sqlx::Error> {
    let row: Option<(i64, String, String, Option<String>)> = sqlx::query_as(
        "SELECT id, name, email, password_hash FROM users WHERE LOWER(email) = LOWER($1) LIMIT 1",
    )
    .bind(email)
    .fetch_optional(pool)
    .await?;
    Ok(row.map(|(id, name, email, password_hash)| UserAuthRow {
        id,
        name,
        email,
        password_hash,
    }))
}

pub async fn find_user_by_id(pool: &PgPool, user_id: i64) -> Result<Option<UserRow>, sqlx::Error> {
    let row: Option<(i64, String, String)> =
        sqlx::query_as("SELECT id, name, email FROM users WHERE id = $1 LIMIT 1")
            .bind(user_id)
            .fetch_optional(pool)
            .await?;
    Ok(row.map(|(id, name, email)| UserRow { id, name, email }))
}

pub async fn insert_user(
    pool: &PgPool,
    name: &str,
    email: &str,
    request_id: Option<&str>,
) -> Result<InsertedUser, sqlx::Error> {
    insert_user_with_password(pool, name, email, None, request_id).await
}

pub async fn insert_user_with_password(
    pool: &PgPool,
    name: &str,
    email: &str,
    password_hash: Option<&str>,
    request_id: Option<&str>,
) -> Result<InsertedUser, sqlx::Error> {
    let mut conn = pool.acquire().await?;
    stamp_application_name(&mut conn, request_id).await?;
    let row: (i64, String, String, NaiveDateTime) = sqlx::query_as(
        "INSERT INTO users (name, email, password_hash, created_at) VALUES ($1, $2, $3, NOW()) RETURNING id, name, email, created_at",
    )
    .bind(name)
    .bind(email)
    .bind(password_hash)
    .fetch_one(&mut *conn)
    .await?;

    Ok(InsertedUser {
        id: row.0,
        name: row.1,
        email: row.2,
        created_at: format_created_at(row.3),
    })
}

pub async fn insert_item(
    pool: &PgPool,
    name: &str,
    request_id: Option<&str>,
) -> Result<InsertedItem, sqlx::Error> {
    let mut conn = pool.acquire().await?;
    stamp_application_name(&mut conn, request_id).await?;
    let row: (i64, String, NaiveDateTime) = sqlx::query_as(
        "INSERT INTO items (name, created_at) VALUES ($1, NOW()) RETURNING id, name, created_at",
    )
    .bind(name)
    .fetch_one(&mut *conn)
    .await?;

    Ok(InsertedItem {
        id: row.0,
        name: row.1,
        created_at: format_created_at(row.2),
    })
}
