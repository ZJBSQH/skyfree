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


class ReviewerContractModel:
    """Return a valid issue only when the reviewer exposes its full enum contract."""

    def invoke(self, messages):
        system_prompt = messages[0].content
        allowed_values = {
            "critical", "major", "minor",
            "setting_conflict", "logic_flaw", "character_ooc", "style", "deviation",
            "setting", "character", "plot", "writer",
        }
        severity = "major" if all(value in system_prompt for value in allowed_values) else "blocker"
        payload = {
            "passed": False,
            "summary": "needs work",
            "issues": [{
                "severity": severity,
                "category": "logic_flaw",
                "description": "broken cause",
                "target_agent": "writer",
                "suggestion": "repair it",
            }],
        }
        return SimpleNamespace(content=__import__("json").dumps(payload), response_metadata={})


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


def test_reviewer_treats_passed_result_with_issues_as_failed_review():
    state = make_initial_state("test novel")
    state["current_draft"] = "draft"
    payload = (
        '{"passed":true,"summary":"looks fine","issues":['
        '{"severity":"critical","category":"logic_flaw",'
        '"description":"broken cause","target_agent":"writer",'
        '"suggestion":"repair it"}]}'
    )
    agent = ReviewerAgent(FakeModel(payload), REVIEWER_PROMPT)

    result = agent.invoke(state)

    assert result["review_passed"] is False
    assert result["review_issues"] == [{
        "severity": "critical",
        "category": "logic_flaw",
        "description": "broken cause",
        "target_agent": "writer",
        "suggestion": "repair it",
    }]


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


def test_reviewer_prompt_exposes_the_complete_issue_enum_contract():
    state = make_initial_state("test novel")
    state["current_draft"] = "draft"
    agent = ReviewerAgent(ReviewerContractModel(), REVIEWER_PROMPT)

    result = agent.invoke(state)

    assert result["review_issues"][0]["severity"] == "major"


def test_reviewer_normalizes_common_issue_aliases_before_validation():
    state = make_initial_state("test novel")
    state["current_draft"] = "draft"
    payload = {
        "passed": False,
        "summary": "needs work",
        "issues": [{
            "severity": "HIGH",
            "category": "剧情逻辑",
            "description": "broken cause",
            "target_agent": "写手",
            "suggestion": "repair it",
        }],
    }
    agent = ReviewerAgent(FakeModel(__import__("json").dumps(payload)), REVIEWER_PROMPT)

    result = agent.invoke(state)

    assert result["review_issues"] == [{
        "severity": "critical",
        "category": "logic_flaw",
        "description": "broken cause",
        "target_agent": "writer",
        "suggestion": "repair it",
    }]


def test_reviewer_allows_minor_issues_as_warnings():
    state = make_initial_state("test novel")
    state["current_draft"] = "draft"
    payload = {
        "passed": False,
        "summary": "minor polish only",
        "issues": [{
            "severity": "minor",
            "category": "style",
            "description": "one sentence can be sharper",
            "target_agent": "writer",
            "suggestion": "tighten the wording",
        }],
    }
    agent = ReviewerAgent(FakeModel(__import__("json").dumps(payload)), REVIEWER_PROMPT)

    result = agent.invoke(state)

    assert result["review_passed"] is True
    assert result["review_issues"][0]["severity"] == "minor"


def test_supervisor_commits_draft_with_warnings_when_review_rounds_are_exhausted():
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

    assert result["phase"] == "done"
    assert result["next_action"] == "finish"
    assert result["completed_chapters"] == ["unapproved draft"]
    assert result["current_draft"] == "unapproved draft"
    assert result["review_issues"][0]["description"] == "broken cause"
