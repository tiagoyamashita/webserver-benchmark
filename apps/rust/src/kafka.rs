//! Kafka admin (create `create-user` topic on startup) and consumer (Java → Rust user insert).

use rdkafka::admin::{AdminClient, AdminOptions, NewTopic, TopicReplication};
use rdkafka::client::DefaultClientContext;
use rdkafka::config::ClientConfig;
use rdkafka::consumer::{Consumer, StreamConsumer};
use rdkafka::error::RDKafkaErrorCode;
use rdkafka::message::{Headers, Message};
use rdkafka::types::RDKafkaRespErr;
use rdkafka::metadata::MetadataTopic;
use serde::Deserialize;
use sqlx::PgPool;
use std::time::Duration;

const SOURCE: &str = "src/kafka.rs";
pub const CREATE_USER_TOPIC: &str = "create-user";
pub const CREATE_USER_GROUP: &str = "exercises-rust-create-user";

/// Mirrors Java `app.kafka.*` / `spring.kafka.admin.fail-fast`.
#[derive(Clone, Debug)]
pub struct KafkaAdminConfig {
    pub bootstrap_servers: String,
    pub create_user_topic: String,
    pub create_user_partitions: i32,
    pub create_user_replicas: i32,
    pub fail_fast: bool,
}

impl KafkaAdminConfig {
    pub fn from_env() -> Self {
        Self {
            bootstrap_servers: std::env::var("KAFKA_BOOTSTRAP_SERVERS")
                .unwrap_or_else(|_| "127.0.0.1:9092".into()),
            create_user_topic: std::env::var("KAFKA_CREATE_USER_TOPIC")
                .unwrap_or_else(|_| CREATE_USER_TOPIC.into()),
            create_user_partitions: env_i32("KAFKA_CREATE_USER_PARTITIONS", 1),
            create_user_replicas: env_i32("KAFKA_CREATE_USER_REPLICAS", 1),
            fail_fast: env_bool("KAFKA_ADMIN_FAIL_FAST", true),
        }
    }
}

fn env_i32(key: &str, default: i32) -> i32 {
    std::env::var(key)
        .ok()
        .and_then(|s| s.parse().ok())
        .unwrap_or(default)
}

fn env_bool(key: &str, default: bool) -> bool {
    std::env::var(key)
        .ok()
        .map(|s| matches!(s.to_ascii_lowercase().as_str(), "1" | "true" | "yes"))
        .unwrap_or(default)
}

/// Ensures the `create-user` topic exists with the configured layout. Fails when `fail_fast` is true
/// and Kafka is unreachable or the existing topic does not match config (same semantics as Java).
pub async fn ensure_kafka_admin(config: &KafkaAdminConfig) -> Result<(), String> {
    let admin: AdminClient<DefaultClientContext> = ClientConfig::new()
        .set("bootstrap.servers", &config.bootstrap_servers)
        .create()
        .map_err(|e| {
            format!(
                "Kafka admin client (bootstrap={}): {e}",
                config.bootstrap_servers
            )
        })?;

    match topic_state(&admin, &config.create_user_topic, config)? {
        TopicState::Valid => {
            tracing::info!(
                source = SOURCE,
                controller = "ensure_kafka_admin",
                topic = %config.create_user_topic,
                partitions = config.create_user_partitions,
                replicas = config.create_user_replicas,
                bootstrap = %config.bootstrap_servers,
                "Kafka topic already exists"
            );
            return Ok(());
        }
        TopicState::Missing => {}
    }

    let new_topic = NewTopic::new(
        config.create_user_topic.as_str(),
        config.create_user_partitions,
        TopicReplication::Fixed(config.create_user_replicas),
    );

    let results = admin
        .create_topics(std::slice::from_ref(&new_topic), &AdminOptions::new())
        .await
        .map_err(|e| format!("Kafka create_topics: {e}"))?;

    for result in results {
        match result {
            Ok(created) => {
                tracing::info!(
                    source = SOURCE,
                    controller = "ensure_kafka_admin",
                    topic = %created,
                    partitions = config.create_user_partitions,
                    replicas = config.create_user_replicas,
                    "Kafka topic created"
                );
            }
            Err((name, RDKafkaErrorCode::TopicAlreadyExists)) => {
                match topic_state(&admin, &name, config)? {
                    TopicState::Valid => {
                        tracing::info!(
                            source = SOURCE,
                            controller = "ensure_kafka_admin",
                            topic = %name,
                            "Kafka topic already exists (concurrent create)"
                        );
                    }
                    TopicState::Missing => {
                        return Err(format!(
                            "Kafka topic {name} reported as existing but metadata is missing"
                        ));
                    }
                }
            }
            Err((name, code)) => {
                return Err(format!("Kafka topic {name}: {code}"));
            }
        }
    }

    Ok(())
}

enum TopicState {
    Missing,
    Valid,
}

fn topic_state(
    admin: &AdminClient<DefaultClientContext>,
    topic: &str,
    config: &KafkaAdminConfig,
) -> Result<TopicState, String> {
    let metadata = admin
        .inner()
        .fetch_metadata(Some(topic), Duration::from_secs(15))
        .map_err(|e| format!("Kafka metadata for {topic}: {e}"))?;

    let Some(found) = metadata.topics().iter().find(|t| t.name() == topic) else {
        return Ok(TopicState::Missing);
    };

    if found.error() == Some(RDKafkaRespErr::RD_KAFKA_RESP_ERR_UNKNOWN_TOPIC_OR_PART) {
        return Ok(TopicState::Missing);
    }
    if let Some(err) = found.error() {
        return Err(format!("Kafka topic {topic} metadata error: {err:?}"));
    }

    validate_topic_layout(found, config)?;
    Ok(TopicState::Valid)
}

fn validate_topic_layout(meta: &MetadataTopic, config: &KafkaAdminConfig) -> Result<(), String> {
    let partitions = meta.partitions().len() as i32;
    if partitions != config.create_user_partitions {
        return Err(format!(
            "Kafka topic {} has {partitions} partitions, expected {}",
            meta.name(),
            config.create_user_partitions
        ));
    }

    let replication = meta
        .partitions()
        .first()
        .map(|p| p.replicas().len() as i32)
        .unwrap_or(0);
    if replication != config.create_user_replicas {
        return Err(format!(
            "Kafka topic {} replication factor is {replication}, expected {}",
            meta.name(),
            config.create_user_replicas
        ));
    }

    Ok(())
}

/// Starts a background task that consumes `create-user` events and inserts users into Postgres.
pub fn spawn_create_user_consumer(pool: PgPool, config: KafkaAdminConfig) {
    tokio::spawn(async move {
        if let Err(e) = run_create_user_consumer(config, pool).await {
            tracing::error!(
                source = SOURCE,
                controller = "create_user_consumer",
                error = %e,
                "create-user Kafka consumer stopped"
            );
        }
    });
}

#[derive(Debug, Deserialize)]
struct CreateUserEvent {
    event: String,
    name: String,
    email: String,
    #[serde(rename = "requestId")]
    request_id: Option<String>,
}

async fn run_create_user_consumer(
    config: KafkaAdminConfig,
    pool: PgPool,
) -> Result<(), Box<dyn std::error::Error + Send + Sync>> {
    let topic = config.create_user_topic.as_str();
    let consumer: StreamConsumer = ClientConfig::new()
        .set("bootstrap.servers", &config.bootstrap_servers)
        .set("group.id", CREATE_USER_GROUP)
        .set("enable.auto.commit", "true")
        .set("auto.offset.reset", "earliest")
        .create()?;

    consumer.subscribe(&[topic])?;
    tracing::info!(
        source = SOURCE,
        controller = "create_user_consumer",
        topic = topic,
        group = CREATE_USER_GROUP,
        bootstrap = %config.bootstrap_servers,
        "create-user Kafka consumer started"
    );

    loop {
        match consumer.recv().await {
            Ok(message) => {
                let request_id = message
                    .headers()
                    .and_then(|headers| {
                        headers
                            .iter()
                            .find(|h| h.key == "X-Request-ID")
                            .and_then(|h| h.value)
                            .and_then(|v| std::str::from_utf8(v).ok())
                    })
                    .map(str::to_string);
                let id_for_log = request_id.as_deref().unwrap_or("");

                let Some(payload) = message.payload() else {
                    tracing::warn!(
                        source = SOURCE,
                        controller = "create_user_consumer",
                        request_id = id_for_log,
                        reason = "empty-payload",
                        "create-user event skipped"
                    );
                    continue;
                };

                match serde_json::from_slice::<CreateUserEvent>(payload) {
                    Ok(event) => {
                        if event.event != "create-user" {
                            tracing::warn!(
                                source = SOURCE,
                                controller = "create_user_consumer",
                                request_id = id_for_log,
                                event = %event.event,
                                reason = "unexpected-event-type",
                                "create-user event skipped"
                            );
                            continue;
                        }
                        tracing::info!(
                            source = SOURCE,
                            controller = "create_user_consumer",
                            request_id = id_for_log,
                            event = %event.event,
                            name = %event.name,
                            email = %event.email,
                            "create-user event received"
                        );
                        match crate::users::create_user_from_event(
                            &pool,
                            &event.name,
                            &event.email,
                            event.request_id.as_deref().or(request_id.as_deref()),
                        )
                        .await
                        {
                            Ok(user) => {
                                tracing::info!(
                                    source = SOURCE,
                                    controller = "create_user_consumer",
                                    request_id = id_for_log,
                                    id = user.id,
                                    name = %user.name,
                                    email = %user.email,
                                    "create-user event handled"
                                );
                            }
                            Err(e) => {
                                tracing::error!(
                                    source = SOURCE,
                                    controller = "create_user_consumer",
                                    request_id = id_for_log,
                                    name = %event.name,
                                    email = %event.email,
                                    error = %e,
                                    "create-user event failed"
                                );
                            }
                        }
                    }
                    Err(e) => {
                        tracing::error!(
                            source = SOURCE,
                            controller = "create_user_consumer",
                            request_id = id_for_log,
                            error = %e,
                            "create-user event parse failed"
                        );
                    }
                }
            }
            Err(e) => {
                tracing::error!(
                    source = SOURCE,
                    controller = "create_user_consumer",
                    error = %e,
                    "create-user consumer recv error"
                );
            }
        }
    }
}
