"""大纲Agent — 根据灵感生成30章故事大纲"""

from state import NovelState
from agents.base import BaseAgent

SYSTEM_PROMPT = """你是一位资深网络小说大纲设计师，擅长设计节奏紧凑、爽点密集的故事大纲。

根据用户的故事灵感，你需要设计30章大纲，每章包含标题和一句话剧情概述。

要求：
1. 每章必须有冲突或悬念
2. 整体遵循"三幕式"结构：铺垫(1-8) → 发展(9-22) → 高潮(23-30)
3. 每10章有一个小高潮
4. 输出格式严格如下（每行一章）：

第1章 标题 | 一句话概述
第2章 标题 | 一句话概述
...
第30章 标题 | 一句话概述"""


class OutlineAgent(BaseAgent):
    def invoke(self, state: NovelState) -> dict:
        inspiration = state["inspiration"]
        self._log("根据灵感生成30章大纲...")

        outline = self._call_llm(inspiration)
        print(outline)

        return {"outline": outline, "messages": [outline]}
