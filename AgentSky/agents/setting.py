"""设定师 Agent — 世界观、势力、能力规则设计"""

from state import AgentSkyState
from agents.base import BaseAgent

SYSTEM_PROMPT = """你是一位专业的奇幻/科幻世界观设定师，负责构建小说的底层设定体系。

## 职责
1. 世界观框架：时代背景、社会结构、文明形态
2. 势力体系：各势力名称、宗旨、实力对比、历史恩怨
3. 能力规则：修炼体系/魔法系统/科技树的具体规则、等级划分
4. 地理环境：主要场景的地理位置、气候特征
5. 历史事件：影响剧情走向的关键历史事件

## 核心原则
- 统一性：所有设定必须内部自洽
- 可查性：每条设定有唯一 category + key
- 服务剧情：只设定用得上的，不过度设计
- 防冲突：如果已有设定库中有冲突项，先指出再给出修改建议

## 输出格式（严格JSON，不要markdown代码块）
{
  "new_settings": [
    {
      "category": "世界观|势力|能力规则|地理|历史",
      "key": "设定名称（唯一标识）",
      "content": "详细设定内容（200-500字）"
    }
  ],
  "conflict_report": "如果发现与已有设定冲突，说明冲突点和处理方式；否则写'无冲突'",
  "summary": "本轮设定简要说明（一句话）"
}

## 设定质量要求
- 每个category至少提供1条设定
- 能力规则必须分层级（如：炼气→筑基→金丹...）
- 势力之间要有明确的利益冲突或合作关系
- 设定要有"可见性"——能在剧情中直观展现，而非仅仅背景说明
"""


class SettingAgent(BaseAgent):
    """设定师 — 构建和维护统一设定库"""

    def invoke(self, state: AgentSkyState) -> dict:
        existing = state.get("world_settings", [])

        task_context = state.get("task_context", "") or state.get("user_request", "")

        rag_materials = self._retrieve_rag(state)
        prompt = self._build_user_prompt(task_context, existing, rag_materials)
        self._log(f"设定库现有{len(existing)}条，准备生成新设定...")

        result = self._call_llm_json(prompt)

        new_settings = result.get("new_settings", [])
        for s in new_settings:
            s["version"] = 1

        conflict = result.get("conflict_report", "")
        summary = result.get("summary", "")
        print(f"  [SettingAgent] +{len(new_settings)}条设定 | {summary}")
        if conflict and conflict != "无冲突":
            print(f"  [SettingAgent] 冲突报告: {conflict}")

        all_settings = existing + new_settings
        return {
            "world_settings": all_settings,
            "messages": [f"[SettingAgent] 新增{len(new_settings)}条设定: {summary}"],
        }

    def _build_user_prompt(self, task_context: str, existing: list, rag_materials: list) -> str:
        parts = [f"## 创作需求\n{task_context}"]

        if rag_materials:
            parts.append("\n## 参考资料（向量检索）")
            for i, m in enumerate(rag_materials, 1):
                parts.append(f"{i}. {m['content'][:500]}")

        if existing:
            parts.append("\n## 已有设定库（请避免冲突）")
            for i, s in enumerate(existing, 1):
                parts.append(
                    f"{i}. [{s.get('category', '')}] {s.get('key', '')}: "
                    f"{s.get('content', '')[:200]}"
                )
        else:
            parts.append("\n## 已有设定库\n（空，需要从零构建设定体系）")

        parts.append("\n## 指令\n请根据创作需求，为这部小说设计完整的底层设定体系。至少覆盖世界观、势力、能力规则三个维度。")
        return "\n".join(parts)
