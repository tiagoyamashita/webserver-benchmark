from __future__ import annotations

import pytest

from exercises.web.app import create_app


@pytest.fixture
def client():
    """Flask test client for the exercises web app (dashboard; API routes not implemented yet)."""
    app = create_app()
    app.config["TESTING"] = True
    with app.test_client() as c:
        yield c
