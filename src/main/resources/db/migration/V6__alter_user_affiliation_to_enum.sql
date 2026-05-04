-- affiliation 컬럼을 자유 문자열에서 Enum 값으로 변경
-- 기존 데이터가 있는 경우 null 처리 후 컬럼 크기 축소
ALTER TABLE users ALTER COLUMN affiliation TYPE VARCHAR(20);
UPDATE users SET affiliation = NULL WHERE affiliation NOT IN ('UNDERGRADUATE', 'GRADUATE', 'FACULTY', 'ASSISTANT', 'EXTERNAL');
