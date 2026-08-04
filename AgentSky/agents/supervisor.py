"""主编 Agent — 解析需求，路由任务，控制整体流程"""

import json
from state import AgentSkyState
from agents.base import BaseAgent

SYSTEM_PROMPT = """你是一位资深小说项目主编，负责统筹整个创作流程。

## 你的能力
1. 需求解析：从用户输入中提取创作需求（题材、风格、篇幅等）
2. 任务拆解：判断当前缺少什么，按优先级排定任务队列
3. 路由决策：将任务分发给对应的专业Agent
4. 成果评估：接收各Agent的产出，判断是否满足要求
5. 审核调度：收到审核Agent的修改意见后，决定由哪个Agent返工

## 路由规则（next_action取值）
- "setting"   — 缺少世界观/势力/规则设定，派给设定师
- "character" — 缺少人物卡/人物关系，派给人物设计师
- "plot"      — 缺少大纲/主线支线/伏笔，派给剧情策划
- "writer"    — 蓝图（设定+人物+大纲）齐备，派给写手
- "finish"    — 全部完成，结束流程

### 首次启动
当 phase="init" 时，分析用户需求，判断需要哪些蓝图：
- 如果用户需求涉及修炼/异能/魔法等特殊能力 → 必须先有设定
- 如果用户提到了具体人物 → 需要人物设计
- 如果用户需求是"写一章"但没有大纲 → 必须先有大纲
- 一般情况下按顺序：setting → character → plot，但如果用户已经提供了某部分可跳过

### 蓝图阶段
当各专业Agent汇报完成后，检查蓝图完整性：
- world_settings 是否够用？
- characters 是否覆盖了所有必要角色？
- plot_outline 是否有足够的章节规划？
- 全部齐备 → 路由到 writer

### 审核修改循环
当 phase="review" 且有 review_issues 时：
- critical 问题 → 立即路由到对应 target_agent 重做
- major 问题 → 累积2个以上再路由修改
- 仅 minor 问题 → 如果 review_round < max_review_rounds，让 writer 微调；否则 finish
- review_round >= max_review_rounds → 强制 finish

## 输出格式（严格JSON，不要markdown代码块）
{
  "analysis": "对当前状态的简要分析",
  "next_action": "setting|character|plot|writer|finish",
  "task_context": "传递给目标Agent的具体创作指令，包含用户需求要点",
  "reason": "做出此路由决策的原因"
}
"""


class SupervisorAgent(BaseAgent):
    """主编 — 流程调度中枢"""

    def invoke(self, state: AgentSkyState) -> dict:
        review_passed = state.get("review_passed", False)
        has_draft = bool(state.get("current_draft", ""))
        review_issues = state.get("review_issues", [])

        # ── 状态一致性修复：reviewer 未通过但无任何 issue → 视为通过 ──
        if not review_passed and has_draft and not review_issues:
            print("  [Supervisor] WARNING: review_passed=False 但无 issue，自动修正为通过")
            review_passed = True

        # ── Priority 1: 审核未通过且有正文 → 进入审核修复循环 ──
        if not review_passed and has_draft and review_issues:
            return self._handle_review(state)

        # ── 蓝图状态检查 ──
        has_settings = len(state.get("world_settings", [])) > 0
        has_characters = len(state.get("characters", [])) > 0
        has_plot = len(state.get("plot_outline", [])) > 0
        blueprints_ready = has_settings and has_characters and has_plot

        # ── Priority 2: 蓝图齐备 → 规则路由，不调 LLM ──
        if blueprints_ready:
            return self._route_blueprint_ready(state)

        # ── Priority 3: 蓝图未齐备 → LLM 路由决策 ──
        return self._route_by_llm(state)

    def _route_blueprint_ready(self, state: AgentSkyState) -> dict:
        """蓝图齐备时的规则路由：直接决定写新章还是结束"""
        completed_count = len(state.get("completed_chapters", []))
        plot_count = len(state.get("plot_outline", []))
        review_passed = state.get("review_passed", False)
        has_draft = bool(state.get("current_draft", ""))

        # 全部章节已写完
        if completed_count >= plot_count and has_draft and review_passed:
            self._log(f"全部完成: {completed_count}/{plot_count}章, review_passed={review_passed}")
            return {
                "phase": "done",
                "next_action": "finish",
                "task_context": "",
                "supervisor_log": [f"[Supervisor] 全部{completed_count}章完成，结束"],
            }

        # 写下一章（或第一版草稿）
        chapter_num = completed_count + 1
        task_context = f"撰写第{chapter_num}章" if not has_draft else f"继续撰写(已完成{completed_count}章)"

        self._log(f"蓝图齐备 → 路由到 writer (第{chapter_num}章)")
        return {
            "phase": "writing",
            "next_action": "writer",
            "task_context": task_context,
            "supervisor_log": [f"[Supervisor] 蓝图齐备，路由到 writer (第{chapter_num}/{plot_count}章)"],
        }

    def _route_by_llm(self, state: AgentSkyState) -> dict:
        """蓝图阶段：通过 LLM 决定下一步路由"""
        phase = state.get("phase", "init")
        user_request = state.get("user_request", "")
        has_settings = len(state.get("world_settings", [])) > 0
        has_characters = len(state.get("characters", [])) > 0
        has_plot = len(state.get("plot_outline", [])) > 0

        context = self._build_context(state)
        self._log(f"phase={phase}, has_setting={has_settings}, has_char={has_characters}, has_plot={has_plot}")

        result = self._call_llm_json(context)
        next_action = result.get("next_action", "finish")
        task_context = result.get("task_context", user_request)
        analysis = result.get("analysis", "")

        new_phase = phase
        if next_action == "writer":
            new_phase = "writing"
        elif next_action == "finish":
            new_phase = "done"

        print(f"  [Supervisor] {analysis}")
        print(f"  [Supervisor] decision: {next_action} | reason: {result.get('reason', '')}")

        return {
            "phase": new_phase,
            "next_action": next_action,
            "task_context": task_context,
            "supervisor_log": [f"[Supervisor] {analysis} -> {next_action}"],
        }

    def _handle_review(self, state: AgentSkyState) -> dict:
        """处理审核意见，决定修改路由（规则判断，不调LLM）"""
        issues = state["review_issues"]
        review_round = state["review_round"]
        max_rounds = state["max_review_rounds"]

        criticals = [i for i in issues if i.get("severity") == "critical"]
        majors = [i for i in issues if i.get("severity") == "major"]
        minors = [i for i in issues if i.get("severity") == "minor"]
        actionable = criticals + majors  # critical 和 major 都需要处理

        # 强制结束：已达最大轮次
        if review_round >= max_rounds:
            print(f"  [Supervisor] review_round={review_round} >= max={max_rounds}, 强制结束")
            return {
                "phase": "done", "next_action": "finish",
                "task_context": "",
                "supervisor_log": ["[Supervisor] 已达最大审核轮次，强制结束"],
            }

        # critical 或 major → 路由到对应 agent 修复
        if actionable:
            next_action = actionable[0].get("target_agent", "writer")
            sev = actionable[0].get("severity", "?")
            desc = actionable[0].get("description", "")
            suggestion = actionable[0].get("suggestion", "")
            task_context = f"修改意见: {desc}\n建议: {suggestion}"
            print(f"  [Supervisor] {sev}问题 → 路由到 {next_action}: {desc}")
            return {
                "phase": "review",
                "next_action": next_action,
                "task_context": task_context,
                "supervisor_log": [f"[Supervisor] {len(actionable)}个问题(含{len(criticals)}critical/{len(majors)}major)，路由到 {next_action}: {desc}"],
            }

        # 仅 minor → 让 writer 微调
        if minors:
            print(f"  [Supervisor] 仅有{len(minors)}个minor问题 → writer微调")
            return {
                "phase": "review",
                "next_action": "writer",
                "task_context": f"微调: {minors[0].get('description', '')}",
                "supervisor_log": [f"[Supervisor] 仅有minor问题，writer微调"],
            }

        # 无问题 → 完成
        return {
            "phase": "done", "next_action": "finish",
            "task_context": "",
            "supervisor_log": ["[Supervisor] 审核无问题，结束"],
        }

    def _build_context(self, state: AgentSkyState) -> str:
        """构建给 LLM 的完整上下文"""
        parts = []

        parts.append(f"## 用户原始需求\n{state['user_request']}")

        parts.append(f"\n## 当前进度")
        parts.append(f"- phase: {state.get('phase', 'init')}")
        parts.append(f"- 设定库条目数: {len(state.get('world_settings', []))}")
        parts.append(f"- 人物卡数量: {len(state.get('characters', []))}")
        parts.append(f"- 大纲节点数: {len(state.get('plot_outline', []))}")
        parts.append(f"- 已完成章节数: {len(state.get('completed_chapters', []))}")
        parts.append(f"- 审核轮次: {state.get('review_round', 0)}")

        had_output = False
        if state.get("world_settings"):
            parts.append(f"\n## 已有设定（摘要）")
            for s in state["world_settings"][:5]:
                parts.append(f"- [{s.get('category', '')}] {s.get('key', '')}: {s.get('content', '')[:80]}...")
            had_output = True

        if state.get("characters"):
            parts.append(f"\n## 已有人物（摘要）")
            for c in state["characters"][:5]:
                parts.append(f"- {c.get('name', '')} ({c.get('role_type', '')}): {c.get('personality', '')[:60]}...")
            had_output = True

        if state.get("plot_outline"):
            parts.append(f"\n## 已有大纲（前5章）")
            for p in state["plot_outline"][:5]:
                parts.append(f"- {p.get('id', '')} {p.get('title', '')}: {p.get('summary', '')[:80]}...")
            had_output = True

        parts.append(f"\n## 指令")
        if state.get("phase") == "init":
            parts.append("这是首次启动。请分析用户需求，判断创作蓝图需要哪些部分（设定/人物/大纲），按优先级顺序返回第一个需要执行的Agent。")
        else:
            parts.append("请根据当前蓝图完成情况决定下一步：缺什么补什么，齐备了就让writer开始写，全部完成就finish。")

        return "\n".join(parts)
