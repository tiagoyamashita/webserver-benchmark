"""Registry of outbound relay targets (extend by adding a RelaySpec entry).

To add a relay:

1. Add a ``RelaySpec`` to ``_relay_specs()`` below (``id`` becomes ``/api/relay/<id>``).
2. Ensure ``StackLinks.from_env()`` exposes the ``stack_links_field`` base URL.
3. No route changes needed — ``register_relay_routes`` serves all registered specs.
"""

from __future__ import annotations

from dataclasses import dataclass

from exercises.web.stack_ping import StackLinks


@dataclass(frozen=True)
class RelaySpec:
    """Maps `/api/relay/<id>` to a downstream HTTP endpoint on another stack service."""

    id: str
    relay_target: str
    stack_links_field: str
    downstream_path: str
    methods: frozenset[str] = frozenset({"GET", "POST"})
    description: str = ""


def _relay_specs() -> dict[str, RelaySpec]:
    return {
        "react": RelaySpec(
            id="react",
            relay_target="webserver-benchmark-react-node",
            stack_links_field="react_node_base_url",
            downstream_path="/api/items",
            methods=frozenset({"GET", "POST"}),
            description=(
                "Relay to React Node direct Postgres items API (`GET`/`POST /api/items`)."
            ),
        ),
    }


RELAY_SPECS: dict[str, RelaySpec] = _relay_specs()


def get_relay_spec(target_id: str) -> RelaySpec | None:
    key = target_id.strip().lower()
    if not key:
        return None
    return RELAY_SPECS.get(key)


def resolve_base_url(spec: RelaySpec, links: StackLinks | None = None) -> str:
    stack = links or StackLinks.from_env()
    base = getattr(stack, spec.stack_links_field, "").strip().rstrip("/")
    if not base:
        raise ValueError(f"{spec.stack_links_field} is not configured")
    return base


def list_relay_specs() -> tuple[RelaySpec, ...]:
    return tuple(RELAY_SPECS.values())
