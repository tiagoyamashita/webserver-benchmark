"""Kafka consumer for ``create-item`` events (Python-only consumer group)."""

from __future__ import annotations

import json
import logging
import threading

from exercises.web.controller_logging import (
    log_error,
    log_kafka_received,
    log_succeeded,
    log_warn,
)
from exercises.web.correlation import kafka_request_id_scope
from exercises.web.db import DatabaseNotConfiguredError, insert_item
from exercises.web.kafka_config import KafkaCreateItemConfig
from exercises.web.request_id import resolve_kafka_request_id

SOURCE = "src/exercises/web/kafka_create_item.py"
_LOG = logging.getLogger(__name__)
CREATE_ITEM_EVENT = "create-item"
_consumer_started = False
_consumer_lock = threading.Lock()


def _header_request_id(headers: list[tuple[str, bytes]] | None) -> str | None:
    if not headers:
        return None
    for key, value in headers:
        if key == "X-Request-ID" and value:
            return value.decode("utf-8")
    return None


def handle_create_item_event(payload: str, header_request_id: str | None) -> None:
    if not payload or not payload.strip():
        request_id = resolve_kafka_request_id(None, header_request_id)
        log_warn(
            _LOG,
            "create_item_consumer",
            SOURCE,
            "create-item event skipped",
            request_id=request_id,
            reason="empty-payload",
        )
        return

    try:
        event = json.loads(payload)
    except json.JSONDecodeError as exc:
        request_id = resolve_kafka_request_id(None, header_request_id)
        log_error(
            _LOG,
            "create_item_consumer",
            SOURCE,
            "create-item event parse failed",
            exc=exc,
            request_id=request_id,
            error=str(exc),
        )
        return

    message_request_id = event.get("requestId")
    if not isinstance(message_request_id, str):
        message_request_id = None
    request_id = resolve_kafka_request_id(message_request_id, header_request_id)

    with kafka_request_id_scope(request_id):
        event_type = event.get("event")
        if event_type != CREATE_ITEM_EVENT:
            log_warn(
                _LOG,
                "create_item_consumer",
                SOURCE,
                "create-item event skipped",
                request_id=request_id,
                kafka_event=event_type,
                reason="unexpected-event-type",
            )
            return

        raw_name = event.get("name")
        name = raw_name.strip() if isinstance(raw_name, str) else ""
        if not name:
            log_warn(
                _LOG,
                "create_item_consumer",
                SOURCE,
                "create-item event validation failed",
                request_id=request_id,
                item_name=name,
                reason="blank-name",
            )
            return

        log_kafka_received(
            _LOG,
            "create_item_consumer",
            SOURCE,
            CREATE_ITEM_EVENT,
            request_id=request_id,
            kafka_event=CREATE_ITEM_EVENT,
            item_name=name,
        )
        try:
            item_id, saved_name, _created_at = insert_item(name, request_id=request_id)
            log_succeeded(
                _LOG,
                "create_item_consumer",
                SOURCE,
                request_id=request_id,
                item_id=item_id,
                item_name=saved_name,
                kafka_event=CREATE_ITEM_EVENT,
            )
        except DatabaseNotConfiguredError as exc:
            log_warn(
                _LOG,
                "create_item_consumer",
                SOURCE,
                "create-item event database not configured",
                request_id=request_id,
                item_name=name,
                target_service="postgres",
                error=str(exc),
            )
        except Exception as exc:
            log_error(
                _LOG,
                "create_item_consumer",
                SOURCE,
                "create-item event failed",
                exc=exc,
                request_id=request_id,
                item_name=name,
                target_service="postgres",
                error=str(exc),
            )


def _consumer_loop(config: KafkaCreateItemConfig) -> None:
    try:
        from kafka import KafkaConsumer
    except ModuleNotFoundError as exc:
        log_warn(
            _LOG,
            "create_item_consumer",
            SOURCE,
            "Kafka consumer disabled: kafka-python not installed",
            error=str(exc),
        )
        return

    consumer = KafkaConsumer(
        config.create_item_topic,
        bootstrap_servers=config.bootstrap_servers,
        group_id=config.create_item_consumer_group,
        auto_offset_reset="earliest",
        enable_auto_commit=True,
    )
    log_succeeded(
        _LOG,
        "create_item_consumer",
        SOURCE,
        topic=config.create_item_topic,
        group=config.create_item_consumer_group,
        bootstrap=config.bootstrap_servers,
        phase="consumer-started",
    )
    for message in consumer:
        try:
            payload = message.value.decode("utf-8") if isinstance(message.value, bytes) else str(message.value or "")
            handle_create_item_event(payload, _header_request_id(message.headers))
        except Exception as exc:
            log_error(
                _LOG,
                "create_item_consumer",
                SOURCE,
                "create-item consumer message failed",
                exc=exc,
                error=str(exc),
            )


def _run_consumer(config: KafkaCreateItemConfig) -> None:
    try:
        _consumer_loop(config)
    except Exception as exc:
        log_error(
            _LOG,
            "create_item_consumer",
            SOURCE,
            "create-item Kafka consumer stopped",
            exc=exc,
            error=str(exc),
        )


def start_create_item_consumer() -> None:
    global _consumer_started
    config = KafkaCreateItemConfig.from_env()
    if not config.enabled:
        log_warn(
            _LOG,
            "create_item_consumer",
            SOURCE,
            "create-item Kafka consumer skipped",
            reason="missing-kafka-bootstrap",
        )
        return
    with _consumer_lock:
        if _consumer_started:
            return
        _consumer_started = True
    thread = threading.Thread(
        target=_run_consumer,
        args=(config,),
        name="kafka-create-item-consumer",
        daemon=True,
    )
    thread.start()
