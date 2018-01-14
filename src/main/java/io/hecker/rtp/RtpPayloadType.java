package io.hecker.rtp;

import com.google.common.collect.ImmutableMap;

public enum RtpPayloadType {
    UNKNOWN(-1),
    JPEG(26),
    FEC(127);

    private static final ImmutableMap<Integer, RtpPayloadType> INVERSE;

    static {
        RtpPayloadType[] values = RtpPayloadType.values();
        ImmutableMap.Builder<Integer, RtpPayloadType> builder = ImmutableMap.builderWithExpectedSize(values.length);

        for (RtpPayloadType type : values) {
            builder.put(type.code(), type);
        }

        INVERSE = builder.build();
    }

    private final int m_value;

    RtpPayloadType(int value) {
        m_value = value;
    }

    public static RtpPayloadType valueOf(int code) {
        return INVERSE.getOrDefault(code, UNKNOWN);
    }

    public int code() {
        return m_value;
    }
}
