use sqlx::postgres::PgPoolOptions;
use sqlx::PgPool;

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

pub async fn list_items(pool: &PgPool) -> Result<Vec<ItemRow>, sqlx::Error> {
    let rows: Vec<(i64, String, chrono::DateTime<chrono::Utc>)> = sqlx::query_as(
        "SELECT id, name, created_at FROM items ORDER BY id",
    )
    .fetch_all(pool)
    .await?;

    Ok(rows
        .into_iter()
        .map(|(id, name, created_at)| ItemRow {
            id,
            name,
            created_at: created_at.to_rfc3339(),
        })
        .collect())
}

pub async fn insert_item(pool: &PgPool, name: &str) -> Result<InsertedItem, sqlx::Error> {
    let row: (i64, String, chrono::DateTime<chrono::Utc>) = sqlx::query_as(
        "INSERT INTO items (name, created_at) VALUES ($1, NOW()) RETURNING id, name, created_at",
    )
    .bind(name)
    .fetch_one(pool)
    .await?;

    Ok(InsertedItem {
        id: row.0,
        name: row.1,
        created_at: row.2.to_rfc3339(),
    })
}
