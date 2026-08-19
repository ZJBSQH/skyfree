"""上下文拼接工具 — 为写稿Agent拼装完整prompt上下文"""


def build_writer_context(inspiration: str, outline: str, characters: str,
                         world_settings: str, chapter_num: int,
                         prev_chapters: list[str]) -> str:
    """构建写第N章时的完整上下文 prompt。

    拼入：灵感 + 大纲 + 人物档案 + 世界观 + 前N-1章摘要。
    """
    parts = []

    parts.append(f"## 故事灵感\n{inspiration}")

    if outline:
        parts.append(f"\n## 完整大纲\n{outline}")

    if characters:
        parts.append(f"\n## 人物档案\n{characters}")

    if world_settings:
        parts.append(f"\n## 世界观设定\n{world_settings}")

    if prev_chapters:
        summaries = []
        for i, ch in enumerate(prev_chapters, 1):
            summary = ch[:300] + "..." if len(ch) > 300 else ch
            summaries.append(f"第{i}章: {summary}")
        parts.append("\n## 前文章节摘要\n" + "\n".join(summaries))

    parts.append(
        f"\n## 任务\n请根据以上设定，撰写第{chapter_num}章的完整正文。"
        f"保持与前文设定一致，注意人物性格和世界观规则。"
    )

    return "\n".join(parts)
