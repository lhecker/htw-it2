package io.hecker.rtp;

import com.google.common.primitives.Ints;

import javax.annotation.concurrent.Immutable;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

// RTP header field composition can be found in RFC 3550, section 5.1.
// https://tools.ietf.org/html/rfc3550#section-5.1
@Immutable
public abstract class RtpPacket implements Comparable<RtpPacket> {
    private static final int RTP_STATIC_HEADER_SIZE = 12;

    private final int m_version;
    private final int m_padding;
    private final int m_extension;
    private final int m_csrcCount;
    private final int m_marker;
    private final RtpPayloadType m_payloadType;
    private final int m_sequenceNumber;
    private final long m_timestamp;
    private final long m_synchronizationSource;

    RtpPacket(ByteBuffer data) {
        data = data
            .duplicate()
            .order(ByteOrder.BIG_ENDIAN);

        if (data.remaining() < RTP_STATIC_HEADER_SIZE) {
            throw new IllegalArgumentException("packet too small");
        }

        m_version = (data.get(0) >>> 6) & 0b00000011;
        m_padding = (data.get(0) >>> 5) & 0b00000001;
        m_extension = (data.get(0) >>> 4) & 0b00000001;
        m_csrcCount = data.get(0) & 0b00001111;
        m_marker = (data.get(1) >>> 7) & 0b00000001;
        m_payloadType = RtpPayloadType.valueOf(data.get(1) & 0b01111111);
        m_sequenceNumber = data.getShort(2) & 0xffff;
        m_timestamp = data.getInt(4) & 0xffffffffL;
        m_synchronizationSource = data.getInt(8) & 0xffffffffL;
    }

    RtpPacket(Builder builder) {
        m_version = builder.m_version;
        m_padding = builder.m_padding;
        m_extension = builder.m_extension;
        m_csrcCount = builder.m_csrcCount;
        m_marker = builder.m_marker;
        m_payloadType = builder.m_payloadType;
        m_sequenceNumber = builder.m_sequenceNumber;
        m_timestamp = builder.m_timestamp;
        m_synchronizationSource = builder.m_synchronizationSource;
    }

    static RtpPayloadType guessType(ByteBuffer data) {
        return RtpPayloadType.valueOf(data.get(1) & 0b01111111);
    }

    public abstract ByteBuffer serialize();

    int getCsrcCount() {
        return m_csrcCount;
    }

    private int getVersion() {
        return m_version;
    }

    int getPadding() {
        return m_padding;
    }

    int getExtension() {
        return m_extension;
    }

    int getMarker() {
        return m_marker;
    }

    public RtpPayloadType getPayloadType() {
        return m_payloadType;
    }

    public int getSequenceNumber() {
        return m_sequenceNumber;
    }

    public long getTimestamp() {
        return m_timestamp;
    }

    private long getSynchronizationSource() {
        return m_synchronizationSource;
    }

    @Override
    public int compareTo(RtpPacket o) {
        if (o == null) {
            throw new NullPointerException();
        }
        return getSequenceNumber() == o.getSequenceNumber() ? 0 : Ints.saturatedCast(getTimestamp() - o.getTimestamp());
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof RtpPacket && getSequenceNumber() == ((RtpPacket) o).getSequenceNumber();
    }

    void serializeHeaderInto(ByteBuffer bb) {
        bb.put((byte) (getVersion() << 6 | getPadding() << 5 | getExtension() << 4 | getCsrcCount()));
        bb.put((byte) (getMarker() << 7 | getPayloadType().code()));
        bb.putShort((short) getSequenceNumber());
        bb.putInt((int) getTimestamp());
        bb.putInt((int) getSynchronizationSource());
    }

    int getHeaderSize() {
        return RTP_STATIC_HEADER_SIZE + getCsrcCount() * 4;
    }

    static class Builder {
        int m_version = 2;
        int m_padding = 0;
        int m_extension = 0;
        int m_csrcCount = 0;
        int m_marker = 0;
        RtpPayloadType m_payloadType = RtpPayloadType.UNKNOWN;
        int m_sequenceNumber = 0;
        long m_timestamp = 0;
        long m_synchronizationSource = 0;
    }
}
