"""LangGraph 多Agent编排工作流 — Supervisor 调度 + 审核闭环

图结构 (全部固定边，路由由节点返回的 Command 决定):
    START → supervisor → setting / character / plot / writer / END
    setting / character / plot → supervisor (固定边，汇报)
    writer → reviewer (固定边，写完必审)
    reviewer → supervisor (未通过) / END (通过)
"""

from langgraph.graph import StateGraph, START, END
from langgraph.types import Command
from state import AgentSkyState, make_initial_state
from llm.config import get_model
from agents.rag import get_store

# ── Agent 实例（懒加载，由 create_workflow 创建）──

_AGENTS = {}

# supervisor 可路由到的合法下游节点；未知/意外值一律降级到 END
VALID_NODES = {"setting", "character", "plot", "writer"}


def _ensure_agents(model):
    """确保所有 Agent 已实例化并缓存"""
    if _AGENTS:
        return _AGENTS

    store = get_store()

    from agents.supervisor import SupervisorAgent, SYSTEM_PROMPT as SP
    from agents.setting import SettingAgent, SYSTEM_PROMPT as STP
    from agents.character import CharacterAgent, SYSTEM_PROMPT as CP
    from agents.plot import PlotAgent, SYSTEM_PROMPT as PP
    from agents.writer import WriterAgent, SYSTEM_PROMPT as WP
    from agents.reviewer import ReviewerAgent, SYSTEM_PROMPT as RP

    _AGENTS["supervisor"] = SupervisorAgent(model, SP)
    _AGENTS["setting"] = SettingAgent(model, STP, store=store)
    _AGENTS["character"] = CharacterAgent(model, CP, store=store)
    _AGENTS["plot"] = PlotAgent(model, PP, store=store)
    _AGENTS["writer"] = WriterAgent(model, WP, store=store)
    _AGENTS["reviewer"] = ReviewerAgent(model, RP)

    return _AGENTS


# ═══════════════════════════════════════════════════════════════
# 节点函数 — 每个 Agent 对应一个图节点
# ═══════════════════════════════════════════════════════════════

def supervisor_node(state: AgentSkyState) -> Command:
    """主编节点 — 解析需求，返回 Command 决定下一跳"""
    result = _AGENTS["supervisor"].invoke(state)
    target = result.pop("next_action", "finish")
    goto = target if target in VALID_NODES else END
    print(f"  [Command] supervisor → {goto}")
    return Command(goto=goto, update=result)


def setting_node(state: AgentSkyState) -> dict:
    """设定师节点 — 构建/修改世界观设定"""
    agent = _AGENTS["setting"]
    return agent.invoke(state)


def character_node(state: AgentSkyState) -> dict:
    """人物节点 — 设计/修改人物卡"""
    agent = _AGENTS["character"]
    return agent.invoke(state)


def plot_node(state: AgentSkyState) -> dict:
    """剧情节点 — 设计/修改大纲和伏笔"""
    agent = _AGENTS["plot"]
    return agent.invoke(state)


def writer_node(state: AgentSkyState) -> dict:
    """写手节点 — 撰写正文"""
    agent = _AGENTS["writer"]
    return agent.invoke(state)


def reviewer_node(state: AgentSkyState) -> Command:
    """审核节点 — 通过/超轮次→END，否则回 supervisor"""
    result = _AGENTS["reviewer"].invoke(state)
    passed = result.get("review_passed", False)
    review_round = result.get("review_round", 0)
    max_rounds = state.get("max_review_rounds", 3)
    goto = END if (passed or review_round >= max_rounds) else "supervisor"
    print(f"  [Command] reviewer → {goto} (passed={passed}, round={review_round})")
    return Command(goto=goto, update=result)


# ═══════════════════════════════════════════════════════════════
# 图构建
# ═══════════════════════════════════════════════════════════════

def create_workflow(model=None):
    """构建并编译 AgentSky 多Agent写作工作流

    节点 (6个):
        supervisor — 主编调度
        setting    — 设定师
        character  — 人物设计师
        plot       — 剧情策划
        writer     — 正文写手
        reviewer   — 审核专家

    边 (全部固定边，路由由节点返回的 Command 决定):
        START → supervisor
        setting/character/plot → supervisor
        writer → reviewer

    Returns:
        编译后的 LangGraph StateGraph
    """
    if model is None:
        model = get_model()

    _ensure_agents(model)

    builder = StateGraph(AgentSkyState)

    # 注册节点
    builder.add_node("supervisor", supervisor_node)
    builder.add_node("setting", setting_node)
    builder.add_node("character", character_node)
    builder.add_node("plot", plot_node)
    builder.add_node("writer", writer_node)
    builder.add_node("reviewer", reviewer_node)

    # 固定边
    builder.add_edge(START, "supervisor")
    builder.add_edge("setting", "supervisor")
    builder.add_edge("character", "supervisor")
    builder.add_edge("plot", "supervisor")
    builder.add_edge("writer", "reviewer")

    return builder.compile()


# ═══════════════════════════════════════════════════════════════
# 测试入口
# ═══════════════════════════════════════════════════════════════

if __name__ == "__main__":
    import sys
    sys.stdout.reconfigure(encoding="utf-8", errors="replace")

    from llm.config import reset_tracker, get_tracker
    reset_tracker()

    print("=" * 60)
    print("  AgentSky — 多Agent小说助手 框架测试")
    print("=" * 60)

    workflow = create_workflow()

    print(f"\n图节点: {list(workflow.get_graph().nodes.keys())}")
    print(f"图边: {list(workflow.get_graph().edges)}")

    state = make_initial_state("测试：一个废柴少年获得系统在异世界崛起")

    try:
        result = workflow.invoke(state, config={"recursion_limit": 60})
        print(f"\n最终阶段: {result.get('phase')}")
        print(f"消息数: {len(result.get('messages', []))}")
        print(f"\n{get_tracker().summary()}")
    except Exception as e:
        print(f"\n执行异常: {e}")
        print(f"退出前 {get_tracker().summary()}")
        import traceback
        traceback.print_exc()
