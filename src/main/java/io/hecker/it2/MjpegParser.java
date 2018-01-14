package io.hecker.it2;

import com.google.common.collect.AbstractIterator;
import com.google.common.io.ByteStreams;
import io.hecker.rtp.RtpPayloadType;
import io.hecker.rtp.VideoFrame;

import javax.annotation.Nullable;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

class MjpegParser extends AbstractIterator<VideoFrame> implements Closeable {
    private static final int TIMESTAMP_INCREMENT = 40;

    private @Nullable InputStream m_inputStream;
    private long m_timestamp;

    MjpegParser(InputStream in) {
        m_inputStream = in;
    }

    @Override
    public void close() throws IOException {
        if (m_inputStream == null) {
            return;
        }

        try {
            m_inputStream.close();
        } finally {
            m_inputStream = null;
        }
    }

    @Override
    protected VideoFrame computeNext() {
        if (m_inputStream == null) {
            return endOfData();
        }

        try {
            byte[] lengthBytes = new byte[5];
            ByteStreams.readFully(m_inputStream, lengthBytes);

            String lengthStr = new String(lengthBytes, StandardCharsets.US_ASCII);
            int length = Integer.parseUnsignedInt(lengthStr);

            ByteBuffer payload = ByteBuffer.allocate(length);
            ByteStreams.readFully(m_inputStream, payload.array());

            VideoFrame frame = new VideoFrame(RtpPayloadType.JPEG, payload, m_timestamp);

            m_timestamp += TIMESTAMP_INCREMENT;

            return frame;
        } catch (Throwable ignored) {
            return endOfData();
        }
    }
}
