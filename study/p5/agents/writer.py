"""写稿Agent — 基于蓝图逐章写作，包含上下文拼接"""

from state import NovelState
from agents.base import BaseAgent
from utils.context import build_writer_context

SYSTEM_PROMPT = """你是一位资深网络小说作家，擅长创作引人入胜、节奏紧凑的小说正文。

根据提供的完整蓝图（灵感、大纲、人物、世界观、前文），撰写指定章节的正文。

写作要求：
1. 每章正文约800-1500字
2. 保持与前文设定一致（人物性格、世界观规则）
3. 每章结尾留悬念或钩子
4. 对话生动，描写有画面感
5. 节奏紧凑，不拖沓"""


class WriterAgent(BaseAgent):
    def invoke(self, state: NovelState) -> dict:
        chapter_num = state.get("current_chapter", 1)
        prev_chapters = state.get("chapters", [])

        context = build_writer_context(
            inspiration=state["inspiration"],
            outline=state.get("outline", ""),
            characters=state.get("characters", ""),
            world_settings=state.get("world_settings", ""),
            chapter_num=chapter_num,
            prev_chapters=prev_chapters,
        )
        self._log(f"撰写第{chapter_num}章 (含前文{len(prev_chapters)}章上下文)...")

        chapter_text = self._call_llm(context)
        print(chapter_text)

        new_chapters = prev_chapters + [chapter_text]
        return {
            "chapters": new_chapters,
            "current_chapter": chapter_num + 1,
            "messages": [chapter_text],
        }
