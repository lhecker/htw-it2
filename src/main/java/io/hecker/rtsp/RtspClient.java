package io.hecker.rtsp;

import com.google.common.net.InetAddresses;

import java.io.Closeable;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URI;

public class RtspClient implements Closeable {
    private final Socket m_socket = new Socket();
    private final DataInputStream m_input;
    private final DataOutputStream m_output;

    private final Object m_lock = new Object();
    private long m_cseq;

    public RtspClient(InetSocketAddress address) throws IOException {
        m_socket.connect(address);
        m_input = new DataInputStream(m_socket.getInputStream());
        m_output = new DataOutputStream(m_socket.getOutputStream());
    }

    @Override
    public void close() throws IOException {
        synchronized (m_lock) {
            m_socket.close();
        }
    }

    public RtspIncomingResponse fetch(RtspOutgoingRequest req) throws Exception {
        if (!req.getPath().startsWith("rtsp://")) {
            URI url = new URI(
                "rtsp",
                null,
                InetAddresses.toUriString(m_socket.getInetAddress()),
                m_socket.getPort(),
                req.getPath(),
                null,
                null
            );
            req.setPath(url.toString());
        }

        synchronized (m_lock) {
            String cseq = unsafeAcquireCseq();

            req.headers().set(RtspHeader.CSEQ, cseq);
            req.serializeInto(m_output);
            m_output.flush();

            RtspIncomingResponse res = new RtspIncomingResponse(m_input);
            String cseqRes = res.headers().get(RtspHeader.CSEQ).orElse(null);

            if (!cseq.equals(cseqRes)) {
                throw new RtspClientException(res, "cseq header does not match");
            }

            int statusCode = res.getStatus().code();
            if (statusCode < 200 || statusCode > 299) {
                throw new RtspClientException(res, "non 2xx status code");
            }

            return res;
        }
    }

    private String unsafeAcquireCseq() {
        String cseq = Long.toUnsignedString(m_cseq);
        m_cseq++;
        return cseq;
    }
}
