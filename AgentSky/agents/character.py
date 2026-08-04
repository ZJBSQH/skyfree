"""人物设计师 Agent — 人物卡设计与人物关系网"""

from state import AgentSkyState
from agents.base import BaseAgent

SYSTEM_PROMPT = """你是一位资深人物设计师，擅长塑造立体、有记忆点的小说角色。

## 职责
1. 人物卡设计：名称、外貌、性格、背景、能力、核心动机
2. 人物关系网：角色之间的情感纽带、利益关系、冲突对立
3. 人物弧光：角色的成长轨迹和关键转变节点
4. 能力匹配：角色能力必须符合世界观中的能力规则设定

## 核心原则
- 遵守设定库中的能力规则，不得超出上限
- 每个角色必须有清晰的核心动机驱动其行为
- 不同角色的性格、说话方式有明显区分
- 每个角色都要有戏剧功能（推动情节/制造冲突/揭示主题）
- 人物关系要有张力和变化空间

## 输出格式（严格JSON，不要markdown代码块）
{
  "new_characters": [
    {
      "name": "角色名",
      "role_type": "主角|配角|反派|路人",
      "appearance": "外貌描述（50-100字）",
      "personality": "性格特征（50-100字）",
      "background": "背景故事（100-200字）",
      "ability": "能力描述（需符合设定库中的能力规则）",
      "motivation": "核心动机（一句话）",
      "relationships": [
        {"name": "关联角色名", "relation": "师徒|朋友|恋人|仇敌|竞争对手|...", "dynamic": "关系动态描述"}
      ],
      "arc_summary": "角色成长弧光概述（一句话）"
    }
  ],
  "consistency_check": "检查是否与设定库冲突，如有冲突请说明",
  "summary": "本轮人物设计简要说明"
}
"""


class CharacterAgent(BaseAgent):
    """人物设计师 — 设计与维护角色卡"""

    def invoke(self, state: AgentSkyState) -> dict:
        existing = state.get("characters", [])
        world_settings = state.get("world_settings", [])

        task_context = state.get("task_context", "") or state.get("user_request", "")

        rag_materials = self._retrieve_rag(state)
        prompt = self._build_user_prompt(task_context, existing, world_settings, rag_materials)
        self._log(f"现有人物{len(existing)}个，设定{len(world_settings)}条，准备设计人物...")

        result = self._call_llm_json(prompt)

        new_chars = result.get("new_characters", [])
        consistency = result.get("consistency_check", "")
        summary = result.get("summary", "")
        print(f"  [CharacterAgent] +{len(new_chars)}个人物 | {summary}")

        # 补充人物间的关系
        all_chars = existing + new_chars
        return {
            "characters": all_chars,
            "messages": [f"[CharacterAgent] 新增{len(new_chars)}个人物: {summary}"],
        }

    def _build_user_prompt(self, task_context: str, existing: list, settings: list, rag_materials: list) -> str:
        parts = [f"## 创作需求\n{task_context}"]

        if rag_materials:
            parts.append("\n## 参考资料（向量检索）")
            for i, m in enumerate(rag_materials, 1):
                parts.append(f"{i}. {m['content'][:500]}")

        if settings:
            parts.append("\n## 世界观设定（人物能力需遵守）")
            for s in settings:
                if s.get("category") in ("能力规则", "势力", "世界观"):
                    parts.append(f"- [{s.get('category', '')}] {s.get('key', '')}: {s.get('content', '')[:200]}")

        if existing:
            parts.append("\n## 已有角色")
            for c in existing:
                parts.append(f"- {c.get('name', '')} ({c.get('role_type', '')}): {c.get('personality', '')[:80]}")

        parts.append("\n## 指令\n请根据创作需求和世界观设定，设计主要角色。至少包含主角和1-2个关键配角/反派。确保角色能力不超出设定库中的能力上限。")
        return "\n".join(parts)
