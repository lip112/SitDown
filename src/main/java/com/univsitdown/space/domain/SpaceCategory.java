package com.univsitdown.space.domain;

// 공간 카테고리 enum. DB에는 문자열 그대로 저장 (EnumType.STRING).
// 클라이언트에는 .name() 값(예: "READING_ROOM")을 그대로 반환.
public enum SpaceCategory {
    READING_ROOM,   // 열람실
    STUDY_ROOM,     // 스터디룸
    PC_ROOM,        // PC실
    LECTURE_ROOM    // 강의실
}
