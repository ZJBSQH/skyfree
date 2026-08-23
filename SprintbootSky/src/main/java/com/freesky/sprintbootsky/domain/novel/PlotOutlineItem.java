package com.freesky.sprintbootsky.domain.novel;

import java.util.List;

/**
 * 剧情大纲节点，对应 AgentSky 的 PlotNode。
 */
public record PlotOutlineItem(
        String id,
        String title,
        String summary,
        List<String> foreshadowing,
        List<String> charactersInvolved,
        List<String> settingsRevealed,
        String type,
        String tensionLevel) {

    public PlotOutlineItem {
        id = id == null ? "" : id;
        title = title == null ? "" : title;
        summary = summary == null ? "" : summary;
        foreshadowing = foreshadowing == null ? List.of() : List.copyOf(foreshadowing);
        charactersInvolved = charactersInvolved == null ? List.of() : List.copyOf(charactersInvolved);
        settingsRevealed = settingsRevealed == null ? List.of() : List.copyOf(settingsRevealed);
        type = type == null ? "" : type;
        tensionLevel = tensionLevel == null ? "" : tensionLevel;
    }
}
