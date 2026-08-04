"""AgentSky 入口 -- 多Agent创意写作助手 (DeepSeek)

用法:
    python main.py               交互模式
    python main.py --idea "灵感"  命令行模式
"""

import sys
from state import make_initial_state
from graph.workflow import create_workflow


def print_banner():
    print("\n" + "=" * 60)
    print("  AgentSky -- 多Agent创意写作助手")
    print("  Powered by DeepSeek")
    print("=" * 60)


def print_result(result: dict):
    """打印最终结果摘要"""
    print("\n" + "=" * 60)
    print("  [DONE] 创作流程完成!")
    print("=" * 60)

    settings_count = len(result.get("world_settings", []))
    chars_count = len(result.get("characters", []))
    plot_count = len(result.get("plot_outline", []))
    chapters_count = len(result.get("completed_chapters", []))
    draft = result.get("current_draft", "")
    review_round = result.get("review_round", 0)
    review_passed = result.get("review_passed", False)

    print(f"\n  蓝图: {settings_count}条设定 | {chars_count}个人物 | {plot_count}章大纲")
    if draft:
        print(f"  正文: {len(draft)}字")
        print(f"  审核: {'PASS' if review_passed else 'N/A'} (第{review_round}轮)")
        print(f"\n  --- 正文预览 ---")
        print(draft[:500])
        if len(draft) > 500:
            print("  ...(省略)...")


def run_with_idea(idea: str):
    """命令行模式 -- 输入一句话灵感，跑完整流程"""
    print_banner()

    print("\n[*] 初始化 DeepSeek 模型...")
    from llm.config import get_model
    model = get_model()
    print("    model ready (deepseek-chat)")

    print("\n[*] 构建 LangGraph 工作流...")
    workflow = create_workflow(model)
    print("    workflow compiled")

    state = make_initial_state(idea)

    print("\n[*] 开始创作流程...")
    print("    supervisor -> setting -> character -> plot -> writer -> reviewer\n")

    try:
        result = workflow.invoke(state)
        print_result(result)
    except Exception as e:
        print(f"\n[FAIL] 创作流程异常: {e}")
        import traceback
        traceback.print_exc()
        return 1

    return 0


def main():
    if len(sys.argv) > 1:
        idea = " ".join(sys.argv[1:])
        return run_with_idea(idea)
    else:
        # 默认使用示例灵感
        example = "一个被宗门视为废物的少年，意外觉醒了'仇恨值系统'——别人越恨他，他越强。他在修真界中韬光养晦，等待复仇的那一天。"
        print_banner()
        print(f"\n使用示例灵感: {example[:60]}...")
        return run_with_idea(example)


if __name__ == "__main__":
    sys.exit(main())
