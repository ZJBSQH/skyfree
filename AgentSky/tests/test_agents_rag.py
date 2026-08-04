"""Agent RAG 注入测试 — 用 FakeStore/FakeModel 验证 prompt 注入，不触发真实 LLM"""
from types import SimpleNamespace

from agents.writer import WriterAgent, SYSTEM_PROMPT as WP
from agents.setting import SettingAgent, SYSTEM_PROMPT as STP
from agents.character import CharacterAgent, SYSTEM_PROMPT as CP
from agents.plot import PlotAgent, SYSTEM_PROMPT as PP
from state import make_initial_state


class FakeStore:
    def search(self, query, top_k=3):
        return [{"content": "天玄门剑修以御剑术闻名，弟子入门先习养剑三年。", "score": 0.91}]


class FakeModel:
    def __init__(self, payload: str):
        self.payload = payload
        self.last_messages = []

    def invoke(self, messages):
        self.last_messages = messages
        return SimpleNamespace(content=self.payload, response_metadata={})


WRITER_PAYLOAD = '{"chapter_id": "ch_01", "chapter_title": "测试", "content": "正文", "foreshadowing_resolved": [], "foreshadowing_planted": [], "self_check": {}}'
SETTING_PAYLOAD = '{"new_settings": [], "conflict_report": "无冲突", "summary": "测试"}'
CHARACTER_PAYLOAD = '{"new_characters": [], "consistency_check": "无冲突", "summary": "测试"}'
PLOT_PAYLOAD = '{"plot_nodes": [], "foreshadowing_plan": [], "structure_analysis": "测试", "summary": "测试"}'


def _user_prompt(model: FakeModel) -> str:
    return model.last_messages[1].content


def test_writer_prompt_includes_rag_section():
    model = FakeModel(WRITER_PAYLOAD)
    agent = WriterAgent(model, WP, store=FakeStore())
    state = make_initial_state("写一部修仙小说")
    state["task_context"] = "撰写第1章"
    agent.invoke(state)
    prompt = _user_prompt(model)
    assert "## 参考资料（向量检索）" in prompt
    assert "天玄门剑修" in prompt


def test_writer_prompt_without_store_skips_rag():
    model = FakeModel(WRITER_PAYLOAD)
    agent = WriterAgent(model, WP, store=None)
    agent.invoke(make_initial_state("写一部修仙小说"))
    assert "## 参考资料（向量检索）" not in _user_prompt(model)


def test_setting_prompt_includes_rag_section():
    model = FakeModel(SETTING_PAYLOAD)
    agent = SettingAgent(model, STP, store=FakeStore())
    agent.invoke(make_initial_state("设计修真世界观"))
    assert "## 参考资料（向量检索）" in _user_prompt(model)


def test_character_prompt_includes_rag_section():
    model = FakeModel(CHARACTER_PAYLOAD)
    agent = CharacterAgent(model, CP, store=FakeStore())
    agent.invoke(make_initial_state("设计人物"))
    assert "## 参考资料（向量检索）" in _user_prompt(model)


def test_plot_prompt_includes_rag_section():
    model = FakeModel(PLOT_PAYLOAD)
    agent = PlotAgent(model, PP, store=FakeStore())
    agent.invoke(make_initial_state("设计大纲"))
    assert "## 参考资料（向量检索）" in _user_prompt(model)
