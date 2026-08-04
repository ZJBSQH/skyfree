"""LangGraph 多Agent编排工作流 — Supervisor 调度 + 审核闭环

图结构:
    START → supervisor ──(条件边)──→ setting / character / plot / writer / END
    setting / character / plot → supervisor (固定边，汇报)
    writer → reviewer (固定边，写完必审)
    reviewer ──(条件边)──→ supervisor (未通过) / END (通过)
"""

from langgraph.graph import StateGraph, START, END
from state import AgentSkyState, make_initial_state
from llm.config import get_model

# ── Agent 实例（懒加载，由 create_workflow 创建）──

_AGENTS = {}


def _ensure_agents(model):
    """确保所有 Agent 已实例化并缓存"""
    if _AGENTS:
        return _AGENTS

    from agents.supervisor import SupervisorAgent, SYSTEM_PROMPT as SP
    from agents.setting import SettingAgent, SYSTEM_PROMPT as STP
    from agents.character import CharacterAgent, SYSTEM_PROMPT as CP
    from agents.plot import PlotAgent, SYSTEM_PROMPT as PP
    from agents.writer import WriterAgent, SYSTEM_PROMPT as WP
    from agents.reviewer import ReviewerAgent, SYSTEM_PROMPT as RP

    _AGENTS["supervisor"] = SupervisorAgent(model, SP)
    _AGENTS["setting"] = SettingAgent(model, STP)
    _AGENTS["character"] = CharacterAgent(model, CP)
    _AGENTS["plot"] = PlotAgent(model, PP)
    _AGENTS["writer"] = WriterAgent(model, WP)
    _AGENTS["reviewer"] = ReviewerAgent(model, RP)

    return _AGENTS


# ═══════════════════════════════════════════════════════════════
# 节点函数 — 每个 Agent 对应一个图节点
# ═══════════════════════════════════════════════════════════════

def supervisor_node(state: AgentSkyState) -> dict:
    """主编节点 — 解析需求，决策路由"""
    agent = _AGENTS["supervisor"]
    return agent.invoke(state)


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


def reviewer_node(state: AgentSkyState) -> dict:
    """审核节点 — 五维审核正文"""
    agent = _AGENTS["reviewer"]
    return agent.invoke(state)


# ═══════════════════════════════════════════════════════════════
# 条件路由函数
# ═══════════════════════════════════════════════════════════════

def route_supervisor(state: AgentSkyState) -> str:
    """Supervisor 的条件边：根据 next_action 决定跳转目标

    返回值必须是已注册的节点名或 END
    """
    next_action = state.get("next_action", "finish")

    routing_map = {
        "setting_agent": "setting",
        "character_agent": "character",
        "plot_agent": "plot",
        "writer_agent": "writer",
        "finish": END,
    }

    target = routing_map.get(next_action, END)
    print(f"  [Router] supervisor → {target} (next_action={next_action})")
    return target


def route_reviewer(state: AgentSkyState) -> str:
    """Reviewer 的条件边：通过→END，未通过→回 Supervisor 修正

    防死循环：超过 max_review_rounds 强制结束
    """
    review_passed = state.get("review_passed", False)
    review_round = state.get("review_round", 0)
    max_rounds = state.get("max_review_rounds", 3)

    if review_passed:
        print(f"  [Router] reviewer → END (审核通过, 第{review_round}轮)")
        return END

    if review_round >= max_rounds:
        print(f"  [Router] reviewer → END (已达最大轮次{max_rounds}, 强制结束)")
        return END

    issues = state.get("review_issues", [])
    print(f"  [Router] reviewer → supervisor (未通过, 问题数={len(issues)}, 轮次={review_round})")
    return "supervisor"


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

    边:
        固定边: START → supervisor
                setting/character/plot → supervisor
                writer → reviewer
        条件边: supervisor → setting|character|plot|writer|END
                reviewer → supervisor(未通过)|END(通过)

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

    # 条件边
    builder.add_conditional_edges(
        "supervisor",
        route_supervisor,
        {
            "setting": "setting",
            "character": "character",
            "plot": "plot",
            "writer": "writer",
            END: END,
        }
    )

    builder.add_conditional_edges(
        "reviewer",
        route_reviewer,
        {
            "supervisor": "supervisor",
            END: END,
        }
    )

    return builder.compile()


# ═══════════════════════════════════════════════════════════════
# 测试入口
# ═══════════════════════════════════════════════════════════════

if __name__ == "__main__":
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
