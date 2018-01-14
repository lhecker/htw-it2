package io.hecker.rtsp;

import com.google.common.collect.ImmutableMap;

public enum RtspStatus {
    CONTINUE(100, "Continue"),

    OK(200, "OK"),
    CREATED(201, "Created"),
    LOW_ON_STORAGE_SPACE(250, "Low on Storage Space"),

    MULTIPLE_CHOICES(300, "Multiple Choices"),
    MOVED_PERMANENTLY(301, "Moved Permanently"),
    MOVED_TEMPORARILY(302, "Moved Temporarily"),
    SEE_OTHER(303, "See Other"),
    NOT_MODIFIED(304, "Not Modified"),
    USE_PROXY(305, "Use Proxy"),

    BAD_REQUEST(400, "Bad Request"),
    UNAUTHORIZED(401, "Unauthorized"),
    PAYMENT_REQUIRED(402, "Payment Required"),
    FORBIDDEN(403, "Forbidden"),
    NOT_FOUND(404, "Not Found"),
    METHOD_NOT_ALLOWED(405, "Method Not Allowed"),
    NOT_ACCEPTABLE(406, "Not Acceptable"),
    PROXY_AUTHENTICATION_REQUIRED(407, "Proxy Authentication Required"),
    REQUEST_TIMEOUT(408, "Request Time-out"),
    GONE(410, "Gone"),
    LENGTH_REQUIRED(411, "Length Required"),
    PRECONDITION_FAILED(412, "Precondition Failed"),
    REQUEST_ENTITY_TOO_LARGE(413, "Request Entity Too Large"),
    REQUEST_URI_TOO_LARGE(414, "Request-URI Too Large"),
    UNSUPPORTED_MEDIA_TYPE(415, "Unsupported Media Type"),
    PARAMETER_NOT_UNDERSTOOD(451, "Parameter Not Understood"),
    CONFERENCE_NOT_FOUND(452, "Conference Not Found"),
    NOT_ENOUGH_BANDWIDTH(453, "Not Enough Bandwidth"),
    SESSION_NOT_FOUND(454, "Session Not Found"),
    METHOD_NOT_VALID_IN_THIS_STATE(455, "Method Not Valid in This State"),
    HEADER_FIELD_NOT_VALID_FOR_RESOURCE(456, "Header Field Not Valid for Resource"),
    INVALID_RANGE(457, "Invalid Range"),
    PARAMETER_IS_READONLY(458, "Parameter Is Read-Only"),
    AGGREGATE_OPERATION_NOT_ALLOWED(459, "Aggregate operation not allowed"),
    ONLY_AGGREGATE_OPERATION_ALLOWED(460, "Only aggregate operation allowed"),
    UNSUPPORTED_TRANSPORT(461, "Unsupported transport"),
    DESTINATION_UNREACHABLE(462, "Destination unreachable"),

    INTERNAL_SERVER_ERROR(500, "Internal Server Error"),
    NOT_IMPLEMENTED(501, "Not Implemented"),
    BAD_GATEWAY(502, "Bad Gateway"),
    SERVICE_UNAVAILABLE(503, "Service Unavailable"),
    GATEWAY_TIMEOUT(504, "Gateway Time-out"),
    RTSP_VERSION_NOT_SUPPORTED(505, "RTSP Version not supported"),
    OPTION_NOT_SUPPORTED(551, "Option not supported");

    private static final ImmutableMap<Integer, RtspStatus> CODE_INVERSE;

    static {
        RtspStatus[] values = values();
        ImmutableMap.Builder<Integer, RtspStatus> builder = ImmutableMap.builderWithExpectedSize(values.length);

        for (RtspStatus sc : values) {
            builder.put(sc.code(), sc);
        }

        CODE_INVERSE = builder.build();
    }

    private final int m_code;
    private final String m_reasonPhrase;

    RtspStatus(int code, String reasonPhrase) {
        m_code = code;
        m_reasonPhrase = reasonPhrase;
    }

    public static RtspStatus valueOfCode(int code) {
        RtspStatus status = CODE_INVERSE.get(code);

        if (status == null) {
            throw new IllegalArgumentException("unknown code " + code);
        }

        return status;
    }

    public int code() {
        return m_code;
    }

    public String reasonPhrase() {
        return m_reasonPhrase;
    }
}
