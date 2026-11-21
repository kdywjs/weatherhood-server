package com.weatherhood.auth.service;

import com.weatherhood.auth.repository.LoginChallengeRepository;
import com.weatherhood.auth.repository.UserSessionRepository;
import java.time.Clock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 만료 판정과 독립적으로 이미 만료된 세션·challenge 행을 매시간 정리한다.
 */
@Component
public class ExpiredCredentialCleaner {
    private final LoginChallengeRepository challenges;
    private final UserSessionRepository sessions;
    private final Clock clock;

    /**
     * 의존 객체와 외부 설정을 받아 ExpiredCredentialCleaner를 구성한다.
     *
     * @param challenges 일회용 로그인 challenge 저장소
     * @param sessions   세션 저장 또는 인증 서비스
     * @param clock      만료 및 발표 시각 판정에 사용할 시계
     */
    public ExpiredCredentialCleaner(LoginChallengeRepository challenges, UserSessionRepository sessions, Clock clock) {
        this.challenges = challenges;
        this.sessions = sessions;
        this.clock = clock;
    }

    /**
     * 만료된 challenge와 세션을 삭제하며 계정과 저장 지역은 유지한다.
     */
    @Scheduled(fixedDelay = 3_600_000, initialDelay = 3_600_000)
    @Transactional
    public void deleteExpiredCredentials() {
        challenges.deleteExpiredChallenges(clock.instant());
        sessions.deleteExpiredSessions(clock.instant());
    }
}
