"""图重构测试 — Command 路由 + 图结构，不触发真实 LLM"""
import pytest
from types import SimpleNamespace
from langgraph.types import Command
from langgraph.graph import END

import graph.workflow as wf
from state import make_initial_state
from agents.supervisor import SupervisorAgent, SYSTEM_PROMPT as SUPERVISOR_PROMPT


@pytest.fixture(autouse=True)
def _restore_agents():
    snapshot = dict(wf._AGENTS)
    yield
    wf._AGENTS.clear()
    wf._AGENTS.update(snapshot)


class FakeAgent:
    def __init__(self, result: dict):
        self.result = result
        self.calls = 0

    def invoke(self, state):
        self.calls += 1
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


def test_reviewer_node_passed_goes_supervisor(monkeypatch):
    wf._AGENTS["reviewer"] = FakeAgent({"review_passed": True, "review_round": 1, "messages": []})
    assert wf.reviewer_node({"max_review_rounds": 3}).goto == "supervisor"


def test_reviewer_node_failed_goes_supervisor(monkeypatch):
    wf._AGENTS["reviewer"] = FakeAgent({"review_passed": False, "review_round": 1, "messages": []})
    assert wf.reviewer_node({"max_review_rounds": 3}).goto == "supervisor"


def test_reviewer_node_max_rounds_goes_supervisor(monkeypatch):
    wf._AGENTS["reviewer"] = FakeAgent({"review_passed": False, "review_round": 3, "messages": []})
    assert wf.reviewer_node({"max_review_rounds": 3}).goto == "supervisor"


def test_create_workflow_structure(monkeypatch):
    monkeypatch.setattr(wf, "get_store", lambda: FakeStore())
    workflow = wf.create_workflow(model=object())
    nodes = set(workflow.get_graph().nodes.keys())
    assert {"supervisor", "setting", "character", "plot", "writer", "reviewer"} <= nodes


def test_specialist_review_repair_returns_to_writer_and_finishes():
    state = make_initial_state("test novel")
    state.update({
        "phase": "review",
        "world_settings": [{"key": "world", "content": "rules"}],
        "characters": [{"name": "hero"}],
        "plot_outline": [{"id": "ch_01"}],
        "current_draft": "original draft",
        "review_issues": [{
            "severity": "critical",
            "category": "setting_conflict",
            "description": "rule conflict",
            "target_agent": "setting",
            "suggestion": "repair the rule",
        }],
        "review_round": 1,
    })

    setting = FakeAgent({"world_settings": state["world_settings"], "messages": []})
    writer = FakeAgent({"current_draft": "revised draft", "messages": []})
    reviewer = FakeAgent({
        "review_passed": True,
        "review_issues": [],
        "review_round": 2,
        "messages": [],
    })
    wf._AGENTS.update({
        "supervisor": SupervisorAgent(object(), SUPERVISOR_PROMPT),
        "setting": setting,
        "character": FakeAgent({}),
        "plot": FakeAgent({}),
        "writer": writer,
        "reviewer": reviewer,
    })

    result = wf.create_workflow(model=object()).invoke(
        state,
        config={"recursion_limit": 8},
    )

    assert setting.calls == 1
    assert writer.calls == 1
    assert result["completed_chapters"] == ["revised draft"]
    assert result["phase"] == "done"
