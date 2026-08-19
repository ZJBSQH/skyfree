"""LangGraph 多Agent编排工作流"""

from langgraph.graph import StateGraph, START, END
from state import NovelState
from agents.orchestrator import OrchestratorAgent, SYSTEM_PROMPT as ORCH_PROMPT
from agents.outline import OutlineAgent, SYSTEM_PROMPT as OUTLINE_PROMPT
from agents.character import CharacterAgent, SYSTEM_PROMPT as CHAR_PROMPT
from agents.worldbuilding import WorldbuildingAgent, SYSTEM_PROMPT as WORLD_PROMPT
from agents.writer import WriterAgent, SYSTEM_PROMPT as WRITER_PROMPT


def create_workflow(model):
    """创建并返回编译后的多Agent写作工作流。

    Agent 节点顺序:
      orchestrator → outline → character → worldbuilding → writer → END
    """
    orch = OrchestratorAgent(model, ORCH_PROMPT)
    outline = OutlineAgent(model, OUTLINE_PROMPT)
    character = CharacterAgent(model, CHAR_PROMPT)
    worldbuilding = WorldbuildingAgent(model, WORLD_PROMPT)
    writer = WriterAgent(model, WRITER_PROMPT)

    def orch_node(state: NovelState) -> dict:
        return orch.invoke(state)

    def outline_node(state: NovelState) -> dict:
        return outline.invoke(state)

    def character_node(state: NovelState) -> dict:
        return character.invoke(state)

    def worldbuilding_node(state: NovelState) -> dict:
        return worldbuilding.invoke(state)

    def writer_node(state: NovelState) -> dict:
        return writer.invoke(state)

    builder = StateGraph(NovelState)

    builder.add_node("orchestrator", orch_node)
    builder.add_node("outline", outline_node)
    builder.add_node("character", character_node)
    builder.add_node("worldbuilding", worldbuilding_node)
    builder.add_node("writer", writer_node)

    builder.add_edge(START, "orchestrator")
    builder.add_edge("orchestrator", "outline")
    builder.add_edge("outline", "character")
    builder.add_edge("character", "worldbuilding")
    builder.add_edge("worldbuilding", "writer")
    builder.add_edge("writer", END)

    return builder.compile()
