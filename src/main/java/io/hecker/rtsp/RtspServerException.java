package io.hecker.rtsp;

public class RtspServerException extends RuntimeException {
    private final RtspStatus m_status;

    public RtspServerException(RtspStatus status) {
        m_status = status;
    }

    public RtspServerException(RtspStatus status, String message) {
        super(message);
        m_status = status;
    }

    public RtspServerException(RtspStatus status, String message, Throwable cause) {
        super(message, cause);
        m_status = status;
    }

    public RtspServerException(RtspStatus status, Throwable cause) {
        super(cause);
        m_status = status;
    }

    public RtspStatus getStatus() {
        return m_status;
    }
}
