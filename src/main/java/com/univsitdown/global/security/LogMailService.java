package com.univsitdown.global.security;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 로컬/개발 단계에서 사용하는 MailService 더미 구현
 *
 * 실제 메일을 발송하지 않고, 대신 로그에 재설정 링크를 기록한다.
 */
@Slf4j
@Component
public class LogMailService implements MailService {

    @Override
    public void sendPasswordReset(String to, String resetLink) {
        log.info("[MAIL] 비밀번호 재설정 링크 발송 | to={} link={}", to, resetLink);
    }
}
