"""审核 Agent — 五维检查：设定冲突、逻辑漏洞、人物OOC、文笔、需求偏离"""

import json
from state import AgentSkyState
from agents.base import BaseAgent

SYSTEM_PROMPT = """你是一位严苛的小说质量审核专家。你的工作是用五维标准逐项检查正文质量。

## 五维审核标准

### 1. 设定冲突 (setting_conflict)
- 正文中的能力/规则描述是否与设定库一致？
- 世界观逻辑是否前后矛盾？
- 地理/历史描述是否有冲突？

### 2. 剧情逻辑 (logic_flaw)
- 事件因果链是否成立？
- 人物行为是否有合理动机？
- 时间线是否矛盾？
- 伏笔回收是否有遗漏？

### 3. 人物OOC (character_ooc)
- 角色言行是否与人物卡一致？
- 角色能力水平是否超出设定？
- 人物关系是否与关系表一致？

### 4. 文笔风格 (style)
- 对话是否自然生动？
- 描写是否有画面感？
- 节奏是否紧凑？有无注水段落？
- 有无语病或重复用词？

### 5. 需求偏离 (deviation)
- 内容是否符合用户原始需求（题材/风格/篇幅）？
- 是否有用户明确不需要的内容？

## 输出格式（严格JSON，不要markdown代码块）
{
  "passed": true,
  "issues": [
    {
      "severity": "critical",
      "category": "setting_conflict",
      "description": "精确到句/段的问题描述",
      "target_agent": "setting",
      "suggestion": "具体可操作的修改建议"
    }
  ],
  "summary": "总体评价（50字内）",
  "approved_content": "已通过无需修改的部分描述"
}

## 审核原则
- 宁可错杀不可放过：不确定的问题也标记为 minor
- 精准定位：每个问题精确到具体段落/句子
- 给出方案：不只指出问题，必须给可操作的修改建议
- 保护成果：明确标注已通过的部分
- 如果没有问题，passed=true, issues=[]
"""


class ReviewerAgent(BaseAgent):
    """审核专家 — 闭环质量把控"""

    def invoke(self, state: AgentSkyState) -> dict:
        draft = state.get("current_draft", "")
        review_round = state.get("review_round", 0)

        if not draft:
            self._log("无正文，跳过审核")
            return {"review_passed": True, "review_round": review_round}

        prompt = self._build_user_prompt(state)
        self._log(f"第{review_round + 1}轮审核, 正文字数={len(draft)}")

        result = self._call_llm_json(prompt)

        passed = result.get("passed", False)
        issues = result.get("issues", [])
        summary = result.get("summary", "")

        status = "PASS" if passed else f"FAIL ({len(issues)}个问题)"
        print(f"  [ReviewerAgent] {status} | {summary}")

        if not passed:
            for i, issue in enumerate(issues):
                print(f"  [{i+1}] [{issue.get('severity', '?')}] {issue.get('category', '?')}: {issue.get('description', '')[:80]}")

        return {
            "review_issues": issues,
            "review_passed": passed,
            "review_round": review_round + 1,
            "messages": [f"[ReviewerAgent] {status}: {summary}"],
        }

    def _build_user_prompt(self, state: AgentSkyState) -> str:
        parts = []

        parts.append(f"## 用户原始需求\n{state.get('user_request', '')}")

        draft = state.get("current_draft", "")
        parts.append(f"\n## 待审核正文\n{draft}")

        # 参考材料
        world_settings = state.get("world_settings", [])
        if world_settings:
            parts.append("\n## 设定库（对照检查）")
            for s in world_settings:
                parts.append(f"- [{s.get('category', '')}] {s.get('key', '')}: {s.get('content', '')[:200]}")

        characters = state.get("characters", [])
        if characters:
            parts.append("\n## 人物卡（对照检查OOC）")
            for c in characters:
                parts.append(f"- {c.get('name', '')} | 性格: {c.get('personality', '')[:80]} | 能力: {c.get('ability', '')[:80]}")

        plot_outline = state.get("plot_outline", [])
        if plot_outline:
            parts.append("\n## 大纲（对照检查偏离）")
            for p in plot_outline[:3]:
                parts.append(f"- {p.get('id', '')} {p.get('title', '')}: {p.get('summary', '')[:100]}")

        foreshadowing_bank = state.get("foreshadowing_bank", [])
        if foreshadowing_bank:
            parts.append("\n## 伏笔表（对照检查遗漏）")
            for f in foreshadowing_bank:
                parts.append(f"- {f.get('id', '')}: {f.get('description', '')[:100]}")

        parts.append("\n## 指令\n请对正文进行五维严格审核，输出审核结果。")

        return "\n".join(parts)
