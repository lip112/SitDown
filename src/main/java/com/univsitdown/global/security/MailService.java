package com.univsitdown.global.security;

/**
 * 이메일 발송을 담당하는 서비스 인터페이스
 *
 * 구현체:
 * - LogMailService: 로컬/개발 단계 로깅용 더미 구현
 * - (향후) SmtpMailService: 실제 SMTP를 통한 이메일 발송
 */
public interface MailService {

    /**
     * 이메일 인증 코드 발송
     *
     * @param to 수신자 이메일
     * @param code 6자리 인증 코드
     */
    void sendVerificationCode(String to, String code);

    /**
     * 비밀번호 재설정 링크 발송
     *
     * @param to 수신자 이메일
     * @param resetLink 비밀번호 재설정 URL
     */
    void sendPasswordReset(String to, String resetLink);
}
