"""AgentSky 全局状态定义 — 贯穿所有Agent的 TypedDict"""

from typing import TypedDict, Annotated, Literal
from langgraph.graph.message import add_messages


# ── 子类型定义 ──

class SettingEntry(TypedDict, total=False):
    """单条设定"""
    category: str          # 世界观 | 势力 | 能力规则 | 地理 | 历史
    key: str               # 唯一设定名
    content: str           # 设定内容
    version: int           # 版本号，用于回溯


class CharacterCard(TypedDict, total=False):
    """人物卡"""
    name: str
    role_type: str         # 主角 | 配角 | 反派 | 路人
    appearance: str
    personality: str
    background: str
    ability: str
    motivation: str
    relationships: list[dict]


class PlotNode(TypedDict, total=False):
    """剧情节点（自引用树）"""
    id: str
    title: str
    summary: str
    foreshadowing: list[str]
    characters_involved: list[str]
    settings_revealed: list[str]
    type: str              # 主线 | 支线
    tension_level: str     # 铺垫 | 发展 | 小高潮 | 大高潮


class ReviewIssue(TypedDict, total=False):
    """审核问题"""
    severity: Literal["critical", "major", "minor"]
    category: Literal["setting_conflict", "logic_flaw", "character_ooc", "style", "deviation"]
    description: str
    target_agent: str
    suggestion: str


# ── 全局状态 ──

class AgentSkyState(TypedDict):
    """贯穿所有 Agent 的全局共享状态"""

    # 用户输入
    user_request: str

    # Supervisor 控制
    phase: str
    next_action: str
    task_context: str
    task_queue: list[str]
    supervisor_log: Annotated[list[str], add_messages]

    # 设定库
    world_settings: list[SettingEntry]

    # 人物库
    characters: list[CharacterCard]

    # 剧情蓝图
    plot_outline: list[PlotNode]
    foreshadowing_bank: list[dict]

    # 正文
    current_draft: str
    completed_chapters: list[str]

    # 审核
    review_issues: list[ReviewIssue]
    review_passed: bool
    review_round: int

    # 迭代控制
    max_review_rounds: int
    error_count: int

    # 消息流 (LangGraph 自动累加)
    messages: Annotated[list, add_messages]


# ── 初始状态工厂 ──

def make_initial_state(user_request: str) -> AgentSkyState:
    return AgentSkyState(
        user_request=user_request,
        phase="init",
        next_action="",
        task_context="",
        task_queue=[],
        supervisor_log=[],
        world_settings=[],
        characters=[],
        plot_outline=[],
        foreshadowing_bank=[],
        current_draft="",
        completed_chapters=[],
        review_issues=[],
        review_passed=False,
        review_round=0,
        max_review_rounds=3,
        error_count=0,
        messages=[],
    )
