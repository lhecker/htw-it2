package io.hecker.rtp;

import javax.annotation.concurrent.Immutable;
import java.nio.ByteBuffer;

@Immutable
public class VideoFrame {
    private final RtpPayloadType m_payloadType;
    private final ByteBuffer m_payload;
    private final long m_timestamp;

    public VideoFrame(RtpPayloadType payloadType, ByteBuffer payload, long timestamp) {
        m_payloadType = payloadType;
        m_payload = payload;
        m_timestamp = timestamp;
    }

    RtpPayloadType getPayloadType() {
        return m_payloadType;
    }

    ByteBuffer getPayload() {
        return m_payload;
    }

    long getTimestamp() {
        return m_timestamp;
    }
}
