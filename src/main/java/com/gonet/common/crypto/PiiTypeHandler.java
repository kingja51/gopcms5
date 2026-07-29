package com.gonet.common.crypto;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.MappedTypes;

/**
 * PII 컬럼 암복호화 TypeHandler — 저장 시 암호화, 조회 시 복호화.
 *
 * <p>서비스는 평문만 다룬다. 매퍼 XML 에서 컬럼마다 지정한다:
 * <pre>
 *   #{memberName, typeHandler=com.gonet.common.crypto.PiiTypeHandler}
 *   &lt;result property="memberName" column="member_name"
 *           typeHandler="com.gonet.common.crypto.PiiTypeHandler"/&gt;
 * </pre>
 *
 * <p><b>이행기 정책</b>: 읽을 때 {@code {AG}} 가 없으면 평문으로 간주해 그대로 돌려준다.
 * 기존 dev 시드처럼 평문으로 들어간 값이 남아 있어도 화면이 깨지지 않게 하려는 것이다
 * (conventions §6 — 이행 완료 후 강제 모드로 전환).
 *
 * <p>스프링 빈이 아니라 MyBatis 가 직접 만드는 객체라 생성자 주입을 쓸 수 없다.
 * {@link Aes256Gcm} 을 정적 홀더({@link PiiCrypto})에서 받아 쓴다.
 */
@MappedTypes(String.class)
public class PiiTypeHandler extends BaseTypeHandler<String> {

    @Override
    public void setNonNullParameter(PreparedStatement ps, int i, String parameter,
            JdbcType jdbcType) throws SQLException {
        ps.setString(i, PiiCrypto.encrypt(parameter));
    }

    @Override
    public String getNullableResult(ResultSet rs, String columnName) throws SQLException {
        return PiiCrypto.decrypt(rs.getString(columnName));
    }

    @Override
    public String getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
        return PiiCrypto.decrypt(rs.getString(columnIndex));
    }

    @Override
    public String getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
        return PiiCrypto.decrypt(cs.getString(columnIndex));
    }
}
