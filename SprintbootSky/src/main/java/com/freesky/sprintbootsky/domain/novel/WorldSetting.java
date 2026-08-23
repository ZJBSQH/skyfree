package com.freesky.sprintbootsky.domain.novel;

/**
 * 世界观设定条目，对应 AgentSky 的 SettingEntry（category / key / content / version）。
 */
public record WorldSetting(String category, String key, String content, Integer version) {

    public WorldSetting {
        category = category == null ? "" : category;
        key = key == null ? "" : key;
        content = content == null ? "" : content;
    }
}
