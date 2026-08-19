"""共享状态 — 贯穿所有Agent的 TypedDict"""

from typing import TypedDict, Annotated
from langgraph.graph.message import add_messages


class NovelState(TypedDict):
    inspiration: str
    outline: str
    characters: str
    world_settings: str
    chapters: list[str]
    current_chapter: int
    confirm_next: bool
    error_count: int
    messages: Annotated[list, add_messages]
