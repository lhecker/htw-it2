package io.hecker.rtsp;

import javax.annotation.concurrent.Immutable;
import java.io.DataInput;
import java.io.EOFException;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Immutable
public class RtspIncomingRequest extends RtspIncomingMessage {
    private static final Pattern REQUEST_LINE_PATTERN = Pattern.compile("^([^ ]+) ([^ ]+) RTSP/1\\.[01]$");

    private final RtspMethod m_method;
    private final String m_path;
    private final InetSocketAddress m_remoteAddress;

    RtspIncomingRequest(DataInput input, InetSocketAddress remoteAddress) throws IOException {
        String requestLine = input.readLine();
        if (requestLine == null) {
            throw new EOFException();
        }

        Matcher requestLineMatcher = REQUEST_LINE_PATTERN.matcher(requestLine);
        if (!requestLineMatcher.matches()) {
            throw new RtspServerException(RtspStatus.BAD_REQUEST, "invalid request line: " + requestLine);
        }

        String methodString = requestLineMatcher.group(1);
        String path = requestLineMatcher.group(2);

        try {
            path = new URI(path).getPath();
        } catch (URISyntaxException ignored) {
            if (!path.startsWith("/")) {
                path = '/' + path;
            }
        }

        RtspMethod method;
        try {
            method = RtspMethod.valueOf(methodString);
        } catch (Throwable e) {
            throw new RtspServerException(RtspStatus.BAD_REQUEST, "invalid method: " + methodString);
        }

        m_method = method;
        m_path = path;
        m_remoteAddress = remoteAddress;

        deserializeFrom(input);
    }

    public RtspMethod getMethod() {
        return m_method;
    }

    public String getPath() {
        return m_path;
    }

    public InetSocketAddress getRemoteAddress() {
        return m_remoteAddress;
    }
}
