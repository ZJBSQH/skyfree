"""P5 — 多Agent写作全链路入口

跑通: 主编Agent → 大纲Agent → 人物Agent → 世界观Agent → 写稿Agent
"""

from config import get_model
from state import NovelState
from graph.workflow import create_workflow


def get_input(prompt: str) -> str:
    val = input(prompt).strip()
    return val if val else "y"


def main():
    print("\n" + "=" * 60)
    print("  🚀 Freesky 多Agent创意写作助手 — P5 多Agent编排")
    print("=" * 60)

    # 1. 获取灵感
    inspiration = input("\n请输入故事灵感: ").strip()
    if not inspiration:
        inspiration = "一个普通少年意外获得穿越异世界的能力，获得仇恨值面板，被所有人憎恨就变强。他必须在陌生的修真世界中生存并寻找回家的路。"

    # 2. 初始化状态
    initial_state: NovelState = {
        "inspiration": inspiration,
        "outline": "",
        "characters": "",
        "world_settings": "",
        "chapters": [],
        "current_chapter": 1,
        "confirm_next": True,
        "error_count": 0,
        "messages": [],
    }

    # 3. 创建工作流
    print("\n初始化 LLM...")
    model = get_model()
    workflow = create_workflow(model)

    # 4. 逐步交互: 每个Agent完成后暂停确认
    steps = ["orchestrator", "outline", "character", "worldbuilding", "writer"]

    print("\n开始创作流程:")
    print("  → 主编分析 → 大纲设计 → 人物设计 → 世界观构建 → 第1章写作\n")

    try:
        result = workflow.invoke(initial_state)
    except Exception as e:
        print(f"\n❌ 工作流执行失败: {e}")
        return

    # 5. 输出摘要
    print("\n" + "=" * 60)
    print("  ✅ 创作完成！各阶段产出摘要：")
    print("=" * 60)
    print(f"\n📋 大纲长度: {len(result.get('outline', ''))} 字符")
    print(f"👤 人物档案长度: {len(result.get('characters', ''))} 字符")
    print(f"🌍 世界观设定长度: {len(result.get('world_settings', ''))} 字符")
    print(f"📖 已完成章节数: {len(result.get('chapters', []))}")

    if result.get("chapters"):
        ch1 = result["chapters"][0]
        print(f"\n第1章正文(前200字预览): {ch1[:200]}...")


if __name__ == "__main__":
    main()
