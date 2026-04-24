package com.univsitdown.global.converter;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

// PostgreSQL text[] 컬럼을 JPA에서 List<String>으로 다루기 위한 변환기.
// @ElementCollection 대신 이 방식을 선택한 이유: 별도 조인 테이블 없이 N+1 문제 회피.
// autoApply = false: 명시적으로 @Convert를 붙인 필드에만 적용 (의도치 않은 변환 방지).
@Converter(autoApply = false)
public class StringListConverter implements AttributeConverter<List<String>, String[]> {

    // List<String> → String[]: DB 저장 시 호출
    @Override
    public String[] convertToDatabaseColumn(List<String> attribute) {
        if (attribute == null || attribute.isEmpty()) return new String[0];
        return attribute.toArray(new String[0]);
    }

    // String[] → List<String>: DB 조회 시 호출
    // Arrays.asList()가 반환하는 고정 크기 리스트를 그대로 쓰면 add/remove 시 UnsupportedOperationException 발생.
    // new ArrayList<>()로 감싸서 가변 리스트로 반환.
    @Override
    public List<String> convertToEntityAttribute(String[] dbData) {
        if (dbData == null || dbData.length == 0) return new ArrayList<>();
        return new ArrayList<>(Arrays.asList(dbData));
    }
}
