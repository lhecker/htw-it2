package io.hecker.rtsp;

import javax.annotation.concurrent.Immutable;
import java.io.DataInput;
import java.io.EOFException;
import java.io.IOException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Immutable
public class RtspIncomingResponse extends RtspIncomingMessage {
    private static final Pattern RESPONSE_LINE_PATTERN = Pattern.compile("^RTSP/1\\.[01] (\\d{3}+)");

    private final RtspStatus m_status;

    RtspIncomingResponse(DataInput input) throws IOException {
        String requestLine = input.readLine();
        if (requestLine == null) {
            throw new EOFException();
        }

        Matcher requestLineMatcher = RESPONSE_LINE_PATTERN.matcher(requestLine);
        if (!requestLineMatcher.lookingAt()) {
            throw new IOException("invalid response line: " + requestLine);
        }

        String statusString = requestLineMatcher.group(1);

        RtspStatus status;
        try {
            status = RtspStatus.valueOfCode(Integer.parseUnsignedInt(statusString));
        } catch (Throwable e) {
            throw new IOException("invalid status: " + statusString);
        }

        m_status = status;

        deserializeFrom(input);
    }

    public RtspStatus getStatus() {
        return m_status;
    }
}
