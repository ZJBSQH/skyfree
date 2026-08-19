import threading
from concurrent.futures import ThreadPoolExecutor

from fastapi.testclient import TestClient

import server


class FakeWorkflow:
    def invoke(self, state, config):
        result = dict(state)
        result["completed_chapters"] = ["reviewed chapter"]
        return result


class BlockingWorkflow:
    def __init__(self):
        self.calls = 0
        self.entered = threading.Event()
        self.release = threading.Event()

    def invoke(self, state, config):
        self.calls += 1
        if self.calls == 1:
            self.entered.set()
            self.release.wait(timeout=5)
        return state


def test_create_requires_service_token(monkeypatch):
    monkeypatch.setenv("AGENTSKY_API_TOKEN", "test-secret")
    monkeypatch.setattr(
        "llm.config.get_model",
        lambda: (_ for _ in ()).throw(AssertionError("unauthorized request reached model")),
    )
    client = TestClient(server.app)

    response = client.post("/api/create", json={"idea": "test"})

    assert response.status_code == 401


def test_create_accepts_valid_service_token(monkeypatch):
    monkeypatch.setenv("AGENTSKY_API_TOKEN", "test-secret")
    monkeypatch.setattr("llm.config.get_model", lambda: object())
    monkeypatch.setattr(server, "create_workflow", lambda model: FakeWorkflow())
    client = TestClient(server.app)

    response = client.post(
        "/api/create",
        json={"idea": "test"},
        headers={"X-AgentSky-Token": "test-secret"},
    )

    assert response.status_code == 200
    assert response.json()["success"] is True
    assert response.json()["result"]["completed_chapters"] == ["reviewed chapter"]


def test_health_is_local_and_does_not_call_model(monkeypatch):
    model_calls = 0

    def model_call():
        nonlocal model_calls
        model_calls += 1
        raise AssertionError("health must not call model")

    monkeypatch.setattr(
        "llm.config.get_model",
        model_call,
    )
    client = TestClient(server.app)

    response = client.get("/api/health")

    assert response.status_code == 200
    assert response.json() == {"status": "ok", "service": "agentsky"}
    assert model_calls == 0


def test_create_rejects_request_when_concurrency_limit_is_reached(monkeypatch):
    monkeypatch.setenv("AGENTSKY_API_TOKEN", "test-secret")
    monkeypatch.setattr("llm.config.get_model", lambda: object())
    workflow = BlockingWorkflow()
    monkeypatch.setattr(server, "create_workflow", lambda model: workflow)
    monkeypatch.setattr(server, "_CREATE_SLOTS", threading.BoundedSemaphore(1))
    client = TestClient(server.app)
    headers = {"X-AgentSky-Token": "test-secret"}

    with ThreadPoolExecutor(max_workers=2) as pool:
        first = pool.submit(client.post, "/api/create", json={"idea": "first"}, headers=headers)
        assert workflow.entered.wait(timeout=2)
        second = client.post("/api/create", json={"idea": "second"}, headers=headers)
        workflow.release.set()
        first_response = first.result(timeout=5)

    assert first_response.status_code == 200
    assert second.status_code == 429
