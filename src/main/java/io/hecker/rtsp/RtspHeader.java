package io.hecker.rtsp;

import com.google.common.base.CaseFormat;
import com.google.common.collect.ImmutableMap;

import java.util.Map;

public enum RtspHeader {
    ACCEPT(),
    ACCEPT_ENCODING(),
    ACCEPT_LANGUAGE(),
    ALLOW(),
    AUTHORIZATION(),
    BANDWIDTH(),
    BLOCKSIZE(),
    CACHE_CONTROL(),
    CONFERENCE(),
    CONNECTION(),
    CONTENT_BASE(),
    CONTENT_ENCODING(),
    CONTENT_LANGUAGE(),
    CONTENT_LENGTH(),
    CONTENT_LOCATION(),
    CONTENT_TYPE(),
    CSEQ(),
    DATE(),
    EXPIRES(),
    FROM(),
    HOST(),
    IF_MATCH(),
    IF_MODIFIED_SINCE(),
    KEYMGMT(),
    LAST_MODIFIED(),
    PROXY_AUTHENTICATE(),
    PROXY_REQUIRE(),
    PUBLIC(),
    RANGE(),
    REFERER(),
    REQUIRE(),
    RETRT_AFTER(),
    RTP_INFO(),
    SCALE(),
    SESSION(),
    SERVER(),
    SPEED(),
    TIMESTAMP(),
    TRANSPORT(),
    UNSUPPORTED(),
    USER_AGENT(),
    VARY(),
    VIA(),
    WWW_AUTHENTICATE();

    private static final Map<String, RtspHeader> HYPHENATED_INVERSE;

    static {
        RtspHeader[] values = values();
        ImmutableMap.Builder<String, RtspHeader> builder = ImmutableMap.builderWithExpectedSize(values.length);

        for (RtspHeader sc : values) {
            builder.put(sc.toString(), sc);
        }

        HYPHENATED_INVERSE = builder.build();
    }

    private final String m_lowerCaseValue;

    RtspHeader() {
        m_lowerCaseValue = CaseFormat.UPPER_UNDERSCORE.to(CaseFormat.LOWER_HYPHEN, name());
    }

    public static RtspHeader valueOfLowerHypenated(String str) {
        return HYPHENATED_INVERSE.get(str);
    }

    @Override
    public String toString() {
        return m_lowerCaseValue;
    }
}
