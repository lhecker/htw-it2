package io.hecker.rtp;

import com.google.common.collect.ImmutableList;

import javax.annotation.concurrent.Immutable;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Collection;
import java.util.Optional;

// FEC header field composition can be found in RFC 5109, section 7.
// https://tools.ietf.org/html/rfc5109#section-7
@Immutable
class RtpFecPacket extends RtpPacket {
    public static final int FEC_MIN_SIZE = 2;
    public static final int FEC_MAX_SIZE = 16;

    private static final int FEC_STATIC_HEADER_SIZE = 10;
    private static final int FEC_LEVEL_0_HEADER_SIZE = 4; // Assuming the FEC long bit is not set

    private final int m_sequenceNumberBase;
    private final int m_paddingRecovery;
    private final int m_extensionRecovery;
    private final int m_csrcCountRecovery;
    private final int m_markerRecovery;
    private final int m_payloadTypeRecovery;
    private final long m_timestampRecovery;
    private final int m_payloadLengthRecovery;
    private final ImmutableList<FecProtection> m_protections;

    RtpFecPacket(ByteBuffer data) {
        super(data);

        data = data.duplicate();
        data.order(ByteOrder.BIG_ENDIAN);
        data.position(getHeaderSize());
        data = data.slice();

        if (data.remaining() < FEC_STATIC_HEADER_SIZE + FEC_LEVEL_0_HEADER_SIZE) {
            throw new IllegalArgumentException("packet too small");
        }

        m_paddingRecovery = (data.get(0) >>> 5) & 0b00000001;
        m_extensionRecovery = (data.get(0) >>> 4) & 0b00000001;
        m_csrcCountRecovery = data.get(0) & 0b00001111;
        m_markerRecovery = (data.get(1) >>> 7) & 0b00000001;
        m_payloadTypeRecovery = data.get(1) & 0b01111111;
        m_sequenceNumberBase = data.getShort(2) & 0xffff;
        m_timestampRecovery = data.getInt(4) & 0xffffffffL;
        m_payloadLengthRecovery = data.getShort(8) & 0xffff;

        {
            ImmutableList.Builder<FecProtection> builder = ImmutableList.builder();

            data.position(10);
            while (data.hasRemaining()) {
                int protectionLength = data.getShort() & 0xffff;
                int mask = data.getShort() & 0xffff;

                data.limit(data.position() + protectionLength);

                builder.add(new FecProtection(mask, data.slice()));

                data.position(data.limit());
                data.limit(data.capacity());
            }

            m_protections = builder.build();
        }
    }

    private RtpFecPacket(Builder builder) {
        super(builder);

        m_paddingRecovery = builder.m_paddingRecovery;
        m_extensionRecovery = builder.m_extensionRecovery;
        m_csrcCountRecovery = builder.m_csrcCountRecovery;
        m_markerRecovery = builder.m_markerRecovery;
        m_payloadTypeRecovery = builder.m_payloadTypeRecovery;
        m_sequenceNumberBase = builder.m_sequenceNumberBase;
        m_timestampRecovery = builder.m_timestampRecovery;
        m_payloadLengthRecovery = builder.m_payloadLengthRecovery;
        m_protections = builder.m_protections;
    }

    static Builder builder() {
        return new Builder();
    }

    @Override
    public ByteBuffer serialize() {
        FecProtection protection = m_protections.get(0);
        int protectionLength = protection.getPayload().remaining();

        ByteBuffer bb = ByteBuffer.allocate(getHeaderSize() + FEC_STATIC_HEADER_SIZE + FEC_LEVEL_0_HEADER_SIZE + protectionLength);
        bb.order(ByteOrder.BIG_ENDIAN);

        // RTP header
        serializeHeaderInto(bb);

        // General FEC header
        bb.put((byte) (getPaddingRecovery() << 5 | getExtensionRecovery() << 4 | getCsrcCountRecovery()));
        bb.put((byte) (getMarkerRecovery() << 7 | getPayloadTypeRecovery()));
        bb.putShort((short) getSequenceNumberBase());
        bb.putInt((int) getTimestampRecovery());
        bb.putShort((short) getPayloadLengthRecovery());

        // Level 0 FEC header
        bb.putShort((short) protectionLength);
        bb.putShort((short) protection.getMask());
        bb.put(protection.getPayload().duplicate());

        bb.flip();
        return bb;
    }

    Optional<RtpRegularPacket> recover(Collection<RtpRegularPacket> packets) {
        FecProtection protection = m_protections.get(0);
        ByteBuffer payload = protection.getPayload();
        int sequenceNumberBase = getSequenceNumberBase();
        int missingSequenceNumber = -1;
        int missingPadding = getPaddingRecovery();
        int missingExtension = getExtensionRecovery();
        int missingCsrcCount = getCsrcCountRecovery();
        int missingMarker = getMarkerRecovery();
        int missingPayloadType = getPayloadTypeRecovery();
        long missingTimestamp = getTimestampRecovery();
        int missingPayloadLength = getPayloadLengthRecovery();

        for (int mask = protection.getMask(), off = 0, m = 1 << 15; m != 0; off++, m >>= 1) {
            if ((mask & m) == 0) {
                continue;
            }

            int seq = sequenceNumberBase + off;
            RtpRegularPacket packet = packets
                .stream()
                .filter(p -> p.getSequenceNumber() == seq)
                .findFirst()
                .orElse(null);

            if (packet != null) {
                ByteBuffer data = packet.getPayload();

                missingPadding ^= packet.getPadding();
                missingExtension ^= packet.getExtension();
                missingCsrcCount ^= packet.getCsrcCount();
                missingMarker ^= packet.getMarker();
                missingPayloadType ^= packet.getPayloadType().code();
                missingTimestamp ^= packet.getTimestamp();
                missingPayloadLength ^= data.remaining();

                for (int i = 0, r = data.remaining(); i < r; ++i) {
                    payload.put(i, (byte) (payload.get(i) ^ data.get(i)));
                }
            } else if (missingSequenceNumber == -1) {
                missingSequenceNumber = seq;
            } else {
                // If more than one packet is missing we can't recover it using the level 0 recovery anyways.
                return Optional.empty();
            }
        }

        // If no packet is missing we can just return
        if (missingSequenceNumber == -1) {
            return Optional.empty();
        }

        payload.limit(missingPayloadLength);

        RtpRegularPacket packet = RtpRegularPacket.builder()
            .withSequenceNumber(missingSequenceNumber)
            .withPadding(missingPadding)
            .withExtension(missingExtension)
            .withCsrcCount(missingCsrcCount)
            .withMarker(missingMarker)
            .withPayloadType(RtpPayloadType.valueOf(missingPayloadType))
            .withTimestamp(missingTimestamp)
            .withPayload(payload)
            .build();
        return Optional.of(packet);
    }

    private int getSequenceNumberBase() {
        return m_sequenceNumberBase;
    }

    private int getPaddingRecovery() {
        return m_paddingRecovery;
    }

    private int getExtensionRecovery() {
        return m_extensionRecovery;
    }

    private int getCsrcCountRecovery() {
        return m_csrcCountRecovery;
    }

    private int getMarkerRecovery() {
        return m_markerRecovery;
    }

    private int getPayloadTypeRecovery() {
        return m_payloadTypeRecovery;
    }

    private long getTimestampRecovery() {
        return m_timestampRecovery;
    }

    private int getPayloadLengthRecovery() {
        return m_payloadLengthRecovery;
    }

    static class Builder extends RtpPacket.Builder {
        private Collection<RtpRegularPacket> m_packets = ImmutableList.of();
        private int m_paddingRecovery = 0;
        private int m_extensionRecovery = 0;
        private int m_csrcCountRecovery = 0;
        private int m_markerRecovery = 0;
        private int m_payloadTypeRecovery = 0;
        private int m_sequenceNumberBase = 0;
        private long m_timestampRecovery = 0;
        private int m_payloadLengthRecovery = 0;
        private ImmutableList<FecProtection> m_protections = ImmutableList.of();

        Builder() {
            m_payloadType = RtpPayloadType.FEC;
        }

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

        Builder withMarker(int value) {
            m_marker = value;
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

        Builder withSequenceNumberBase(int fecSequenceNumberBase) {
            m_sequenceNumberBase = fecSequenceNumberBase;
            return this;
        }

        Builder withPackets(Collection<RtpRegularPacket> packets) {
            m_packets = packets;
            return this;
        }

        RtpFecPacket build() {
            int protectionLength = 0;

            for (RtpRegularPacket packet : m_packets) {
                // TODO: The FEC protection length actually includes the csrc fields
                int payloadLength = packet.getPayload().remaining();

                m_paddingRecovery ^= packet.getPadding();
                m_extensionRecovery ^= packet.getExtension();
                m_csrcCountRecovery ^= packet.getCsrcCount();
                m_markerRecovery ^= packet.getMarker();
                m_payloadTypeRecovery ^= packet.getPayloadType().code();
                m_timestampRecovery ^= packet.getTimestamp();
                m_payloadLengthRecovery ^= payloadLength;

                protectionLength = Integer.max(protectionLength, payloadLength);
            }

            // The "mask" field: Every bit corresponds to a specific packet being protected.
            // A queue size of 5 corresponds to 0b_1111_1000_0000_0000.
            int protectionMask = -1 << (16 - m_packets.size());
            ByteBuffer protectionPayload = ByteBuffer.allocate(protectionLength);

            for (RtpRegularPacket packet : m_packets) {
                ByteBuffer data = packet.getPayload();

                for (int i = 0, r = data.remaining(); i < r; ++i) {
                    protectionPayload.put(i, (byte) (protectionPayload.get(i) ^ data.get(i)));
                }
            }

            m_protections = ImmutableList.of(new FecProtection(protectionMask, protectionPayload));

            return new RtpFecPacket(this);
        }
    }

    @Immutable
    private static class FecProtection {
        private final int m_mask;
        private final ByteBuffer m_payload;

        FecProtection(int mask, ByteBuffer payload) {
            if (mask == 0) {
                throw new IllegalArgumentException("mask must not be 0");
            }
            if (payload.remaining() == 0) {
                throw new IllegalArgumentException("payload must not be empty");
            }

            m_mask = mask;
            m_payload = payload;
        }

        int getMask() {
            return m_mask;
        }

        ByteBuffer getPayload() {
            return m_payload;
        }
    }
}
