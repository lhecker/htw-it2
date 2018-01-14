package io.hecker.rtsp;

import javax.annotation.Nullable;
import java.nio.charset.StandardCharsets;

public class RtspClientException extends Exception {
    private final RtspIncomingResponse m_res;

    public RtspClientException(RtspIncomingResponse res) {
        super(buildMessage(res, null));
        m_res = res;
    }

    public RtspClientException(RtspIncomingResponse res, String message) {
        super(buildMessage(res, message));
        m_res = res;
    }

    public RtspClientException(RtspIncomingResponse res, String message, Throwable cause) {
        super(buildMessage(res, message), cause);
        m_res = res;
    }

    public RtspClientException(RtspIncomingResponse res, Throwable cause) {
        super(cause);
        m_res = res;
    }

    private static String buildMessage(RtspIncomingResponse res, @Nullable String message) {
        StringBuilder builder = new StringBuilder();

        if (message != null) {
            builder.append(message);
            builder.append(" (");
        }

        builder.append(res.getStatus().code());
        builder.append(' ');
        builder.append(res.getStatus().reasonPhrase());

        res.getBody().ifPresent(body -> {
            try {
                String str = StandardCharsets.UTF_8.newDecoder().decode(body).toString();
                builder.append(" - ");
                builder.append(str);
            } catch (Throwable ignored) {
            }
        });

        if (message != null) {
            builder.append(')');
        }

        return builder.toString();
    }

    public RtspIncomingResponse getResponse() {
        return m_res;
    }

    public RtspStatus getStatus() {
        return m_res.getStatus();
    }
}
