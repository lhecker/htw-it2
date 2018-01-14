package io.hecker.rtsp;

import java.io.DataOutput;
import java.io.IOException;

public class RtspOutgoingRequest extends RtspOutgoingMessage {
    private RtspMethod m_method;
    private String m_path;

    public RtspOutgoingRequest() {
        m_method = RtspMethod.GET_PARAMETER;
        m_path = "/";
    }

    public RtspOutgoingRequest(RtspMethod method, String path) {
        m_method = method;
        m_path = path;
    }

    void serializeInto(DataOutput output) throws IOException {
        output.writeBytes(getMethod().toString());
        output.write(' ');
        output.writeBytes(getPath());
        output.writeBytes(" RTSP/1.0\r\n");

        serializeHeaderBodyInto(output);
    }

    private RtspMethod getMethod() {
        return m_method;
    }

    public void setMethod(RtspMethod method) {
        m_method = method;
    }

    public String getPath() {
        return m_path;
    }

    public void setPath(String path) {
        m_path = path;
    }
}
