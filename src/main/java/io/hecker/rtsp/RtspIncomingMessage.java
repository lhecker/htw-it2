package io.hecker.rtsp;

import javax.annotation.Nullable;
import java.io.DataInput;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Optional;

class RtspIncomingMessage {
    private final RtspHeaderMap m_headers = new RtspHeaderMap();
    private @Nullable ByteBuffer m_body;

    void deserializeFrom(DataInput input) throws IOException {
        m_headers.deserializeFrom(input);

        Optional<String> contentLengthStrOpt = m_headers.get(RtspHeader.CONTENT_LENGTH);
        ByteBuffer body = null;

        if (contentLengthStrOpt.isPresent()) {
            String contentLengthStr = contentLengthStrOpt.get();
            int contentLength;

            try {
                contentLength = Integer.parseUnsignedInt(contentLengthStr);
            } catch (NumberFormatException e) {
                throw new IOException("invalid Content-Length header: " + contentLengthStr);
            }

            body = ByteBuffer.allocate(contentLength);
            input.readFully(body.array());
        }

        m_body = body;
    }

    public RtspHeaderMap headers() {
        return m_headers;
    }

    public Optional<ByteBuffer> getBody() {
        return Optional.ofNullable(m_body);
    }
}
