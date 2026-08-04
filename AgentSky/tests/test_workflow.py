"""图重构测试 — Command 路由 + 图结构，不触发真实 LLM"""
import pytest
from types import SimpleNamespace
from langgraph.types import Command
from langgraph.graph import END

import graph.workflow as wf
from state import make_initial_state


@pytest.fixture(autouse=True)
def _restore_agents():
    snapshot = dict(wf._AGENTS)
    yield
    wf._AGENTS.clear()
    wf._AGENTS.update(snapshot)


class FakeAgent:
    def __init__(self, result: dict):
        self.result = result

    def invoke(self, state):
        return self.result


class FakeStore:
    def search(self, query, top_k=3):
        return []


def test_supervisor_node_returns_command_with_node_name(monkeypatch):
    wf._AGENTS["supervisor"] = FakeAgent(
        {"next_action": "writer", "phase": "writing", "task_context": "写第1章", "supervisor_log": ["x"]}
    )
    result = wf.supervisor_node({})
    assert isinstance(result, Command)
    assert result.goto == "writer"
    assert result.update["phase"] == "writing"
    assert "next_action" not in result.update


def test_supervisor_node_finish_maps_to_end(monkeypatch):
    wf._AGENTS["supervisor"] = FakeAgent({"next_action": "finish", "phase": "done"})
    assert wf.supervisor_node({}).goto == END


def test_supervisor_node_unknown_next_action_maps_to_end(monkeypatch):
    wf._AGENTS["supervisor"] = FakeAgent({"next_action": "garbage", "phase": "done"})
    assert wf.supervisor_node({}).goto == END


def test_reviewer_node_passed_goes_end(monkeypatch):
    wf._AGENTS["reviewer"] = FakeAgent({"review_passed": True, "review_round": 1, "messages": []})
    assert wf.reviewer_node({"max_review_rounds": 3}).goto == END


def test_reviewer_node_failed_goes_supervisor(monkeypatch):
    wf._AGENTS["reviewer"] = FakeAgent({"review_passed": False, "review_round": 1, "messages": []})
    assert wf.reviewer_node({"max_review_rounds": 3}).goto == "supervisor"


def test_reviewer_node_max_rounds_goes_end(monkeypatch):
    wf._AGENTS["reviewer"] = FakeAgent({"review_passed": False, "review_round": 3, "messages": []})
    assert wf.reviewer_node({"max_review_rounds": 3}).goto == END


def test_create_workflow_structure(monkeypatch):
    monkeypatch.setattr(wf, "get_store", lambda: FakeStore())
    workflow = wf.create_workflow(model=object())
    nodes = set(workflow.get_graph().nodes.keys())
    assert {"supervisor", "setting", "character", "plot", "writer", "reviewer"} <= nodes
