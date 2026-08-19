from types import SimpleNamespace

import pytest

from agents.base import BaseAgent
from agents.reviewer import ReviewerAgent, SYSTEM_PROMPT as REVIEWER_PROMPT
from agents.supervisor import SupervisorAgent, SYSTEM_PROMPT as SUPERVISOR_PROMPT
from agents.writer import WriterAgent, SYSTEM_PROMPT as WRITER_PROMPT
from state import make_initial_state


class FakeModel:
    def __init__(self, payload: str):
        self.payload = payload

    def invoke(self, messages):
        return SimpleNamespace(content=self.payload, response_metadata={})


class ConcreteAgent(BaseAgent):
    def invoke(self, state):
        return {}


def _ready_state():
    state = make_initial_state("test novel")
    state["world_settings"] = [{"key": "world", "content": "rules"}]
    state["characters"] = [{"name": "hero"}]
    state["plot_outline"] = [{"id": "ch_01"}, {"id": "ch_02"}]
    return state


def test_writer_keeps_unreviewed_chapter_out_of_completed_chapters():
    model = FakeModel(
        '{"chapter_id":"ch_01","chapter_title":"One","content":"draft",'
        '"foreshadowing_resolved":[],"foreshadowing_planted":[],"self_check":{}}'
    )
    result = WriterAgent(model, WRITER_PROMPT, enable_rag=False).invoke(_ready_state())

    assert result["current_draft"] == "draft"
    assert result.get("completed_chapters", []) == []


def test_supervisor_commits_only_the_reviewed_draft():
    state = _ready_state()
    state["current_draft"] = "reviewed revision"
    state["review_passed"] = True

    result = SupervisorAgent(FakeModel("{}"), SUPERVISOR_PROMPT).invoke(state)

    assert result["completed_chapters"] == ["reviewed revision"]
    assert result["current_draft"] == ""
    assert result["next_action"] == "writer"


def test_json_parse_failure_raises_instead_of_returning_empty_result():
    agent = ConcreteAgent(FakeModel("not-json"), "test")

    with pytest.raises(ValueError, match="JSON"):
        agent._call_llm_json("test", max_retries=0)


def test_reviewer_rejects_json_without_required_fields():
    state = make_initial_state("test novel")
    state["current_draft"] = "draft"
    agent = ReviewerAgent(FakeModel("{}"), REVIEWER_PROMPT)

    with pytest.raises(ValueError, match="passed"):
        agent.invoke(state)


def test_reviewer_rejects_passed_result_with_issues():
    state = make_initial_state("test novel")
    state["current_draft"] = "draft"
    payload = (
        '{"passed":true,"summary":"looks fine","issues":['
        '{"severity":"critical","category":"logic_flaw",'
        '"description":"broken cause","target_agent":"writer",'
        '"suggestion":"repair it"}]}'
    )
    agent = ReviewerAgent(FakeModel(payload), REVIEWER_PROMPT)

    with pytest.raises(ValueError, match="passed result must not include issues"):
        agent.invoke(state)


@pytest.mark.parametrize(
    ("field", "value"),
    [("severity", "blocker"), ("category", "continuity"), ("target_agent", "reviewer")],
)
def test_reviewer_rejects_invalid_issue_enums(field, value):
    state = make_initial_state("test novel")
    state["current_draft"] = "draft"
    issue = {
        "severity": "major",
        "category": "logic_flaw",
        "description": "broken cause",
        "target_agent": "writer",
        "suggestion": "repair it",
    }
    issue[field] = value
    payload = '{"passed":false,"summary":"needs work","issues":[' + __import__("json").dumps(issue) + "]}"
    agent = ReviewerAgent(FakeModel(payload), REVIEWER_PROMPT)

    with pytest.raises(ValueError, match=field):
        agent.invoke(state)


def test_supervisor_marks_review_exhaustion_as_failed():
    state = _ready_state()
    state.update({
        "phase": "review",
        "current_draft": "unapproved draft",
        "review_passed": False,
        "review_round": state["max_review_rounds"],
        "review_issues": [{
            "severity": "critical",
            "category": "logic_flaw",
            "description": "broken cause",
            "target_agent": "writer",
            "suggestion": "repair it",
        }],
    })

    result = SupervisorAgent(FakeModel("{}"), SUPERVISOR_PROMPT).invoke(state)

    assert result["phase"] == "failed"
    assert result["next_action"] == "finish"
