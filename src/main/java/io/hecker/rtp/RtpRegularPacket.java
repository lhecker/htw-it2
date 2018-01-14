package io.hecker.rtp;

import javax.annotation.concurrent.Immutable;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

// RTP header field composition can be found in RFC 3550, section 5.1.
// https://tools.ietf.org/html/rfc3550#section-5.1
@Immutable
public class RtpRegularPacket extends RtpPacket {
    private final ByteBuffer m_payload;

    RtpRegularPacket(ByteBuffer data) {
        super(data);

        data = data.duplicate();
        data.position(getHeaderSize());
        m_payload = data.slice();
    }

    private RtpRegularPacket(Builder builder) {
        super(builder);

        m_payload = builder.m_payload;
    }

    static Builder builder() {
        return new Builder();
    }

    public ByteBuffer getPayload() {
        return m_payload;
    }

    @Override
    public ByteBuffer serialize() {
        ByteBuffer bb = ByteBuffer.allocate(getHeaderSize() + m_payload.remaining());
        bb.order(ByteOrder.BIG_ENDIAN);

        // RTP header
        serializeHeaderInto(bb);

        // General Payload
        bb.put(m_payload.duplicate());

        bb.flip();
        return bb;
    }

    static class Builder extends RtpPacket.Builder {
        ByteBuffer m_payload = ByteBuffer.allocate(0);

        Builder withVersion(int value) {
            m_version = value;
            return this;
        }

        Builder withPadding(int value) {
            m_padding = value;
            return this;
        }

        Builder withExtension(int value) {
            m_extension = value;
            return this;
        }

        Builder withCsrcCount(int value) {
            m_csrcCount = value;
            return this;
        }

        Builder withMarker(int value) {
            m_marker = value;
            return this;
        }

        Builder withPayloadType(RtpPayloadType value) {
            m_payloadType = value;
            return this;
        }

        Builder withSequenceNumber(int value) {
            m_sequenceNumber = value;
            return this;
        }

        Builder withTimestamp(long value) {
            m_timestamp = value;
            return this;
        }

        Builder withSynchronizationSource(long value) {
            m_synchronizationSource = value;
            return this;
        }

        Builder withPayload(ByteBuffer value) {
            m_payload = value.slice();
            return this;
        }

        RtpRegularPacket build() {
            return new RtpRegularPacket(this);
        }
    }
}
