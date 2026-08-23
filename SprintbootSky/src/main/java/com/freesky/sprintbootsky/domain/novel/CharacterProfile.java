package com.freesky.sprintbootsky.domain.novel;

import java.util.List;

/**
 * 人物卡（NovelProject 聚合内部实体）。字段名与 AgentSky / Vue 契约一致。
 */
public record CharacterProfile(
        String name,
        String roleType,
        String appearance,
        String personality,
        String background,
        String ability,
        String motivation,
        List<CharacterRelationship> relationships) {

    public CharacterProfile {
        name = name == null ? "" : name;
        roleType = roleType == null ? "" : roleType;
        appearance = appearance == null ? "" : appearance;
        personality = personality == null ? "" : personality;
        background = background == null ? "" : background;
        ability = ability == null ? "" : ability;
        motivation = motivation == null ? "" : motivation;
        relationships = relationships == null ? List.of() : List.copyOf(relationships);
    }
}
