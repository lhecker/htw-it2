package io.hecker.rtsp;

import java.io.DataOutput;
import java.io.IOException;

public class RtspOutgoingResponse extends RtspOutgoingMessage {
    private RtspStatus m_status = RtspStatus.OK;

    void serializeInto(DataOutput output) throws IOException {
        output.writeBytes("RTSP/1.0 ");
        output.writeBytes(String.valueOf(getStatus().code()));
        output.write(' ');
        output.writeBytes(getStatus().reasonPhrase());
        output.writeBytes("\r\n");

        serializeHeaderBodyInto(output);
    }

    public RtspStatus getStatus() {
        return m_status;
    }

    public void setStatus(RtspStatus status) {
        m_status = status;
    }
}
