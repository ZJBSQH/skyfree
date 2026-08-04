"""剧情策划 Agent — 大纲、主线支线、伏笔管理"""

from state import AgentSkyState
from agents.base import BaseAgent

SYSTEM_PROMPT = """你是一位资深剧情策划，擅长设计节奏紧凑、逻辑严密的故事结构。

## 职责
1. 大纲设计：卷/章结构树，每章标题和概要
2. 主线规划：核心冲突的起承转合，关键转折点
3. 支线设计：辅助主线的次要情节线
4. 伏笔管理：埋设点和回收点，纳入伏笔回收追踪表
5. 节奏控制：高潮与铺垫交替

## 核心原则
- 剧情不得与世界观设定冲突，角色行为符合其性格和动机
- 事件因果关系清晰，无逻辑漏洞
- 每个伏笔必须有明确的回收计划
- 每3-5章至少有一个小爽点或反转

## 输出格式（严格JSON，不要markdown代码块）
{
  "plot_nodes": [
    {
      "id": "ch_01",
      "title": "章节标题",
      "summary": "本章概要（50-100字）",
      "foreshadowing": ["本章新埋的伏笔描述"],
      "characters_involved": ["出场人物名称"],
      "settings_revealed": ["本章揭示的设定key"],
      "type": "主线",
      "tension_level": "铺垫|发展|小高潮|大高潮"
    }
  ],
  "foreshadowing_plan": [
    {
      "id": "fs_01",
      "description": "伏笔内容",
      "planted_in": "埋在哪章",
      "resolved_in": "计划在哪章回收",
      "payoff": "回收方式"
    }
  ],
  "structure_analysis": "整体结构说明（100字内）",
  "summary": "本轮剧情设计简要说明"
}
"""


class PlotAgent(BaseAgent):
    """剧情策划 — 设计与维护大纲和伏笔"""

    def invoke(self, state: AgentSkyState) -> dict:
        existing_plot = state.get("plot_outline", [])
        existing_fs = state.get("foreshadowing_bank", [])
        characters = state.get("characters", [])
        world_settings = state.get("world_settings", [])

        task_context = state.get("task_context", "") or state.get("user_request", "")

        rag_materials = self._retrieve_rag(state)
        prompt = self._build_user_prompt(task_context, existing_plot, characters, world_settings, rag_materials)
        self._log(f"大纲现有{len(existing_plot)}节点，准备生成剧情...")

        result = self._call_llm_json(prompt)

        new_plot = result.get("plot_nodes", [])
        new_fs = result.get("foreshadowing_plan", [])
        analysis = result.get("structure_analysis", "")
        summary = result.get("summary", "")
        print(f"  [PlotAgent] +{len(new_plot)}章大纲, +{len(new_fs)}个伏笔 | {summary}")

        all_plot = existing_plot + new_plot
        all_fs = existing_fs + new_fs

        return {
            "plot_outline": all_plot,
            "foreshadowing_bank": all_fs,
            "messages": [f"[PlotAgent] 新增{len(new_plot)}章大纲: {summary}"],
        }

    def _build_user_prompt(self, task_context: str, existing_plot: list,
                           characters: list, settings: list, rag_materials: list) -> str:
        parts = [f"## 创作需求\n{task_context}"]

        if rag_materials:
            parts.append("\n## 参考资料（向量检索）")
            for i, m in enumerate(rag_materials, 1):
                parts.append(f"{i}. {m['content'][:500]}")

        if settings:
            parts.append("\n## 世界观设定摘要")
            for s in settings[:5]:
                parts.append(f"- [{s.get('category', '')}] {s.get('key', '')}: {s.get('content', '')[:150]}")

        if characters:
            parts.append("\n## 人物列表（剧情需覆盖）")
            for c in characters:
                parts.append(f"- {c.get('name', '')} ({c.get('role_type', '')}): {c.get('motivation', '')[:80]}")

        if existing_plot:
            parts.append(f"\n## 已有大纲（{len(existing_plot)}章，请在已有基础上续写）")
            for p in existing_plot[-3:]:
                parts.append(f"- {p.get('id', '')} {p.get('title', '')}")

        parts.append("\n## 指令\n请设计10章大纲（如已有大纲则续写新章节），确保每章有冲突或悬念，整体遵循三幕式结构。伏笔要明确回收计划。")
        return "\n".join(parts)
