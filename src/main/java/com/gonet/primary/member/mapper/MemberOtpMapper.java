package com.gonet.primary.member.mapper;

import com.gonet.primary.member.dto.MemberOtp;
import java.time.LocalDateTime;
import org.apache.ibatis.annotations.Param;
import org.egovframe.rte.psl.dataaccess.mapper.EgovMapper;

/** tb_member_otp — 인증번호 발급·검증. 평문은 어디에도 남지 않는다. */
@EgovMapper
public interface MemberOtpMapper {

    int insert(MemberOtp otp);

    /** 가장 최근 미사용 코드 — 검증은 이것 하나만 본다(옛 코드로 통과하면 안 된다). */
    MemberOtp findLatest(@Param("memberId") String memberId,
                         @Param("purpose") String purpose);

    /** 시도 횟수 +1 — 세션이 아니라 행에 둔다(세션을 새로 잡아도 초기화되지 않는다). */
    int increaseAttempt(@Param("otpId") String otpId);

    int markVerified(@Param("otpId") String otpId);

    /** 재발급 시 이전 코드를 즉시 만료 — 살아 있는 코드가 둘이면 창이 두 개가 된다. */
    int expireAll(@Param("memberId") String memberId, @Param("purpose") String purpose,
                  @Param("now") LocalDateTime now);
}
