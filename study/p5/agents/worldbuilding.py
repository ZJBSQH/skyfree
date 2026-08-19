"""世界观Agent — 构建世界观设定"""

from state import NovelState
from agents.base import BaseAgent

SYSTEM_PROMPT = """你是一位资深世界观设计师，擅长构建宏大、自洽的虚构世界。

根据故事灵感、大纲和人物档案，设计完整的世界观设定。要求分类输出：

1. 修炼体系/力量体系（等级划分、晋升条件、特色能力）
2. 势力分布（主要宗门/组织/国家，各自特点与关系）
3. 地理环境（重要地点、区域特色）
4. 历史背景（关键历史事件，影响当下格局的）
5. 世界规则（特殊法则、限制条件）

每类3-5条，简洁有力，与故事情节直接相关。"""


class WorldbuildingAgent(BaseAgent):
    def invoke(self, state: NovelState) -> dict:
        inspiration = state["inspiration"]
        outline = state.get("outline", "")
        characters = state.get("characters", "")

        user_msg = (
            f"故事灵感：{inspiration}\n\n"
            f"故事大纲：\n{outline}\n\n"
            f"人物档案：\n{characters}"
        )
        self._log("基于大纲和人物构建世界观...")

        world_settings = self._call_llm(user_msg)
        print(world_settings)

        return {"world_settings": world_settings, "messages": [world_settings]}
