"""Kafka configuration for Python-only consumers."""

from __future__ import annotations

import os
from dataclasses import dataclass


@dataclass(frozen=True)
class KafkaCreateItemConfig:
    bootstrap_servers: str
    create_item_topic: str
    create_item_consumer_group: str
    enabled: bool

    @classmethod
    def from_env(cls) -> KafkaCreateItemConfig:
        bootstrap = os.environ.get("KAFKA_BOOTSTRAP_SERVERS", "").strip()
        topic = os.environ.get("KAFKA_CREATE_ITEM_TOPIC", "create-item").strip() or "create-item"
        group = (
            os.environ.get("KAFKA_CREATE_ITEM_CONSUMER_GROUP", "exercises-create-item-python").strip()
            or "exercises-create-item-python"
        )
        enabled = bool(bootstrap)
        return cls(
            bootstrap_servers=bootstrap or "127.0.0.1:9092",
            create_item_topic=topic,
            create_item_consumer_group=group,
            enabled=enabled,
        )
