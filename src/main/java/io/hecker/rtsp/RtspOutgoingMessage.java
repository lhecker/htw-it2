package io.hecker.rtsp;

import javax.annotation.Nullable;
import java.io.DataOutput;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

class RtspOutgoingMessage {
    private final RtspHeaderMap m_headers = new RtspHeaderMap();
    private @Nullable ByteBuffer m_body;

    void serializeHeaderBodyInto(DataOutput output) throws IOException {
        headers().serializeInto(output);
        output.writeBytes("\r\n");

        Optional<ByteBuffer> bodyOpt = getBody();
        if (bodyOpt.isPresent()) {
            ByteBuffer body = bodyOpt.get();
            output.write(body.array(), body.arrayOffset() + body.position(), body.remaining());
        }
    }

    public RtspHeaderMap headers() {
        return m_headers;
    }

    private Optional<ByteBuffer> getBody() {
        return Optional.ofNullable(m_body);
    }

    public void setBody(@Nullable String body) {
        setBody(body != null ? ByteBuffer.wrap(body.getBytes(StandardCharsets.UTF_8)) : null);
    }

    private void setBody(@Nullable ByteBuffer body) {
        m_body = body;

        if (m_body != null) {
            m_headers.set(RtspHeader.CONTENT_LENGTH, String.valueOf(m_body.remaining()));
        } else {
            m_headers.remove(RtspHeader.CONTENT_LENGTH);
        }
    }
}
