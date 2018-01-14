package io.hecker.it2;

import com.google.common.util.concurrent.AbstractExecutionThreadService;
import com.google.common.util.concurrent.Monitor;
import com.google.common.util.concurrent.MoreExecutors;
import io.hecker.rtp.RtpReceiver;
import io.hecker.rtp.RtpPayloadType;
import io.hecker.rtp.RtpRegularPacket;
import io.hecker.rtsp.*;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.util.function.Consumer;

class Client extends AbstractExecutionThreadService {
    private static final Logger LOGGER = LogManager.getLogger();

    private final InetSocketAddress m_address;
    private final String m_path;
    private final Consumer<ByteBuffer> m_callback;

    private final Monitor m_playMonitor = new Monitor();
    private boolean m_play = false;
    private final Monitor.Guard m_playGuard = new Monitor.Guard(m_playMonitor) {
        @Override
        public boolean isSatisfied() {
            return m_play;
        }
    };
    private Thread m_thread;
    private RtspClient m_client;
    private RtpReceiver m_receiver;
    private String m_sessionId;

    Client(InetSocketAddress address, String path, Consumer<ByteBuffer> callback) {
        m_address = address;
        m_path = path;
        m_callback = callback;
    }

    RtpReceiver getReceiver() {
        return m_receiver;
    }

    @Override
    protected void startUp() throws Exception {
        try {
            m_thread = Thread.currentThread();
            m_client = new RtspClient(m_address);
            m_receiver = new RtpReceiver(new InetSocketAddress(0));

            RtspOutgoingRequest req = new RtspOutgoingRequest(RtspMethod.SETUP, m_path);
            req.headers().set(RtspHeader.TRANSPORT, "RTP/AVP/UDP;client_port=" + m_receiver.getLocalPort());

            RtspIncomingResponse res = m_client.fetch(req);

            m_sessionId = res.headers()
                .get(RtspHeader.SESSION)
                .orElseThrow(() -> new RtspClientException(res, "missing session header"));

            Client self = this;

            m_receiver.addListener(new Listener() {
                @Override
                public void terminated(State from) {
                    self.stopAsync();
                }

                @Override
                public void failed(State from, Throwable failure) {
                    // TODO: propagate the failure up to this service
                    LOGGER.error("receiver failed", failure);
                    self.stopAsync();
                }
            }, MoreExecutors.directExecutor());

            m_receiver.startAsync();
        } catch (Throwable e) {
            LOGGER.error("startup failed", e);

            if (m_receiver != null) {
                m_receiver.stopAsync();
            }
            if (m_client != null) {
                m_client.close();
            }

            throw e;
        }
    }

    @Override
    protected void run() throws Exception {
        try {
            while (isRunning()) {
                RtpRegularPacket packet = m_receiver.next();

                if (packet.getPayloadType() != RtpPayloadType.JPEG) {
                    continue;
                }

                m_playMonitor.enter();
                try {
                    m_playMonitor.waitFor(m_playGuard);
                } finally {
                    m_playMonitor.leave();
                }

                m_callback.accept(packet.getPayload());
            }
        } catch (InterruptedException ignored) {
        }
    }

    @Override
    protected void shutDown() throws Exception {
        try {
            RtspOutgoingRequest req = new RtspOutgoingRequest(RtspMethod.TEARDOWN, m_path);
            req.headers().set(RtspHeader.SESSION, m_sessionId);
            m_client.fetch(req);
        } catch (RtspClientException e) {
            if (e.getStatus() != RtspStatus.SESSION_NOT_FOUND) {
                throw e;
            }
        } finally {
            m_receiver.stopAsync();
            m_client.close();
        }
    }

    @Override
    protected void triggerShutdown() {
        if (m_thread != null) {
            m_thread.interrupt();
        }
    }

    void setPlay(boolean play) throws Exception {
        m_playMonitor.enter();
        m_play = play;
        m_playMonitor.leave();

        RtspOutgoingRequest req = new RtspOutgoingRequest(play ? RtspMethod.PLAY : RtspMethod.PAUSE, m_path);
        req.headers().set(RtspHeader.SESSION, m_sessionId);
        m_client.fetch(req);
    }
}
