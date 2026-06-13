from exercises.web.request_id import resolve_kafka_request_id


def test_resolve_kafka_request_id_prefers_message_body() -> None:
    message_id = "11111111-2222-3333-4444-555555555555"
    header_id = "aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee"
    assert resolve_kafka_request_id(message_id, header_id) == message_id


def test_resolve_kafka_request_id_falls_back_to_header() -> None:
    header_id = "aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee"
    assert resolve_kafka_request_id(None, header_id) == header_id
