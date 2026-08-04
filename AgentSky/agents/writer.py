"""正文写手 Agent — 基于蓝图逐章写作，严格遵守人设与世界观"""

import json
from state import AgentSkyState
from agents.base import BaseAgent

SYSTEM_PROMPT = """你是一位专业小说作家，擅长将剧情蓝图转化为生动流畅的正文。

## 写作要求
- 人设不出戏：每个角色的言行必须与其人物卡一致，审核重点检查OOC
- 设定不出错：涉及能力/世界规则时，必须严格遵循设定库
- 伏笔要回收：按伏笔回收追踪表，该回收的伏笔不能遗漏
- 节奏紧凑：每章800-2000字，冲突驱动，结尾留钩子
- 对话生动：每个角色的对话风格要与其性格匹配
- 画面感强：描写有细节，让读者有画面感

## 输出格式（严格JSON，不要markdown代码块）
{
  "chapter_id": "ch_01",
  "chapter_title": "章节标题",
  "content": "正文全文（800-2000字）",
  "foreshadowing_resolved": ["本章回收的伏笔ID"],
  "foreshadowing_planted": ["本章新埋的伏笔描述"],
  "self_check": {
    "ooc_check": "人物是否OOC的自检",
    "setting_check": "是否违反设定的自检",
    "foreshadowing_check": "伏笔回收情况"
  }
}
"""


class WriterAgent(BaseAgent):
    """正文写手 — 严格遵循蓝图创作正文"""

    def invoke(self, state: AgentSkyState) -> dict:
        plot_outline = state.get("plot_outline", [])
        characters = state.get("characters", [])
        world_settings = state.get("world_settings", [])
        foreshadowing_bank = state.get("foreshadowing_bank", [])
        completed = state.get("completed_chapters", [])
        review_issues = state.get("review_issues", [])
        current_draft = state.get("current_draft", "")

        prompt = self._build_user_prompt(
            state["user_request"], plot_outline, characters,
            world_settings, foreshadowing_bank, completed,
            review_issues, current_draft
        )

        is_revision = len(review_issues) > 0
        chapter_num = len(completed) + 1
        self._log(f"撰写第{chapter_num}章" if not is_revision else f"根据审核意见修改第{chapter_num}章...")

        result = self._call_llm_json(prompt)

        chapter_content = result.get("content", "")
        chapter_title = result.get("chapter_title", "")
        chapter_id = result.get("chapter_id", "")
        self_check = result.get("self_check", {})
        print(f"  [WriterAgent] {chapter_id} {chapter_title} ({len(chapter_content)}字)")

        if is_revision:
            # 修改模式：只更新当前草稿，不追加 completed
            return {
                "current_draft": chapter_content,
                "messages": [f"[WriterAgent] 修改 {chapter_id} {chapter_title}: {len(chapter_content)}字"],
            }
        else:
            # 新章模式：追加到 completed_chapters
            new_completed = completed + [chapter_content]
            return {
                "current_draft": chapter_content,
                "completed_chapters": new_completed,
                "messages": [f"[WriterAgent] {chapter_id} {chapter_title}: {len(chapter_content)}字"],
            }

    def _build_user_prompt(self, user_request: str, plot_outline: list,
                           characters: list, world_settings: list,
                           foreshadowing_bank: list, completed: list,
                           review_issues: list, current_draft: str) -> str:
        parts = [f"## 故事需求\n{user_request}"]

        # 世界观摘要
        if world_settings:
            parts.append("\n## 世界观设定（严格遵守）")
            for s in world_settings:
                parts.append(f"- [{s.get('category', '')}] {s.get('key', '')}: {s.get('content', '')[:300]}")

        # 人物卡
        if characters:
            parts.append("\n## 人物卡（严格遵守人设）")
            for c in characters:
                parts.append(
                    f"\n### {c.get('name', '')} ({c.get('role_type', '')})\n"
                    f"- 外貌: {c.get('appearance', '')}\n"
                    f"- 性格: {c.get('personality', '')}\n"
                    f"- 背景: {c.get('background', '')}\n"
                    f"- 能力: {c.get('ability', '')}\n"
                    f"- 动机: {c.get('motivation', '')}"
                )
                if c.get("relationships"):
                    rels = [f"{r.get('name', '')}({r.get('relation', '')})" for r in c["relationships"]]
                    parts.append(f"- 关系: {', '.join(rels)}")

        # 大纲
        if plot_outline:
            chapter_num = len(completed) + 1 if not review_issues else len(completed)
            parts.append(f"\n## 大纲（本次撰写第{chapter_num}章）")
            for p in plot_outline:
                parts.append(f"- {p.get('id', '')} {p.get('title', '')}: {p.get('summary', '')}")

        # 伏笔
        if foreshadowing_bank:
            unresolved = [f for f in foreshadowing_bank if not f.get("resolved_in")]
            if unresolved:
                parts.append("\n## 待回收伏笔")
                for f in unresolved:
                    parts.append(f"- {f.get('id', '')}: {f.get('description', '')}")

        # 前文
        if completed:
            parts.append(f"\n## 前文章节摘要（共{len(completed)}章）")
            for i, ch in enumerate(completed, 1):
                summary = ch[:300] + "..." if len(ch) > 300 else ch
                parts.append(f"第{i}章: {summary}")

        # 审核意见（修改模式）
        if review_issues and current_draft:
            parts.append(f"\n## 上次审核修改意见（请针对性修改）")
            for issue in review_issues:
                parts.append(f"- [{issue.get('severity', '')}] {issue.get('category', '')}: {issue.get('description', '')}")
                parts.append(f"  建议: {issue.get('suggestion', '')}")
            parts.append(f"\n## 当前草稿（需要修改）\n{current_draft}")
            parts.append("\n## 指令\n请根据审核意见修改上述草稿。只修改指出的问题，保留已通过的部分。")
        else:
            chapter_num = len(completed) + 1
            parts.append(f"\n## 指令\n请根据以上完整蓝图撰写第{chapter_num}章的正文。严格遵守人设和世界观规则。")

        return "\n".join(parts)
