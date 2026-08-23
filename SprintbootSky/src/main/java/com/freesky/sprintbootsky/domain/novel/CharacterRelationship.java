package com.freesky.sprintbootsky.domain.novel;

/**
 * 人物关系，字段与 Vue 前端契约保持一致（name / relation / dynamic）。
 */
public record CharacterRelationship(String name, String relation, String dynamic) {

    public CharacterRelationship {
        name = name == null ? "" : name;
        relation = relation == null ? "" : relation;
        dynamic = dynamic == null ? "" : dynamic;
    }
}
