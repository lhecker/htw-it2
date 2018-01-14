package io.hecker.it2;

import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableMap;
import com.google.common.net.InetAddresses;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.common.util.concurrent.Service;
import io.hecker.rtp.RtpPayloadType;
import io.hecker.rtp.RtpSender;
import io.hecker.rtsp.*;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.security.SecureRandom;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

class Server extends RtspServer {
    private static final Logger LOGGER = LogManager.getLogger();
    private static final String SAMPLE_PATH = "/sample.mjpeg";
    private static final String SAMPLE_NAME = SAMPLE_PATH.substring(1);

    private final SecureRandom m_sessionIdGenerator = new SecureRandom();
    private final Map<Long, RtpSender> m_sessions = new ConcurrentHashMap<>();

    private final Map<RtspMethod, RtspServerHandler> m_handlers = ImmutableMap.<RtspMethod, RtspServerHandler>builder()
        .put(RtspMethod.OPTIONS, this::handleOptions)
        .put(RtspMethod.DESCRIBE, this::handleDescribe)
        .put(RtspMethod.SETUP, this::handleSetup)
        .put(RtspMethod.TEARDOWN, this::handleTeardown)
        .put(RtspMethod.PLAY, this::handlePlay)
        .put(RtspMethod.PAUSE, this::handlePause)
        .build();

    Server(InetSocketAddress address) throws IOException {
        super(address);
        addHandler(this::handle);
    }

    private void handle(RtspIncomingRequest req, RtspOutgoingResponse res) throws Exception {
        RtspServerHandler handler = m_handlers.get(req.getMethod());
        if (handler == null) {
            throw new RtspServerException(RtspStatus.NOT_IMPLEMENTED, "method not implemented");
        }

        handler.accept(req, res);
    }

    private void handleOptions(RtspIncomingRequest req, RtspOutgoingResponse res) {
        String options = m_handlers.keySet()
            .stream()
            .map(RtspMethod::name)
            .collect(Collectors.joining(", "));
        res.headers().set(RtspHeader.PUBLIC, options);
    }

    private void handleDescribe(RtspIncomingRequest req, RtspOutgoingResponse res) {
        if (!req.getPath().equals(SAMPLE_PATH)) {
            res.setStatus(RtspStatus.NOT_IMPLEMENTED);
            return;
        }

        InetSocketAddress socketAddress = req.getRemoteAddress();
        InetAddress address = socketAddress.getAddress();
        long version = System.nanoTime();
        String addressType = address instanceof Inet6Address ? "IP6" : "IP4";
        String addressString = InetAddresses.toAddrString(address);

        res.setBody(""
            + "v=0\r\n"
            + "o=- 0 " + version + " IN " + addressType + " " + addressString + "\r\n"
            + "s=" + SAMPLE_NAME + "\r\n"
            + "t=0 0\r\n"
            + "m=video 1024/2 RTP/AVP " + RtpPayloadType.JPEG.code() + "\r\n"
        );
    }

    private void handleSetup(RtspIncomingRequest req, RtspOutgoingResponse res) throws Exception {
        if (req.headers().contains(RtspHeader.SESSION)) {
            throw new RtspServerException(RtspStatus.METHOD_NOT_VALID_IN_THIS_STATE, "unable to reconfigure stream");
        }

        //
        // 1. Parse the Transport: header and acquire the client_port
        //

        String transportHeader = req.headers()
            .get(RtspHeader.TRANSPORT)
            .orElseThrow(() -> new RtspServerException(RtspStatus.BAD_REQUEST, "missing transport header"));
        List<String> transportParts = Splitter.on(';').trimResults().splitToList(transportHeader);
        String transportKind = transportParts.get(0);
        List<String> transportParameters = transportParts.subList(1, transportParts.size());

        if (!transportKind.equals("RTP/AVP") && !transportKind.equals("RTP/AVP/UDP")) {
            throw new RtspServerException(RtspStatus.UNSUPPORTED_TRANSPORT, "only RTP/AVP/UDP is currently supported");
        }

        int clientPort = -1;

        for (String p : transportParameters) {
            if (p.startsWith("client_port=")) {
                try {
                    clientPort = Integer.parseUnsignedInt(p.substring(12));
                } catch (NumberFormatException e) {
                    clientPort = Integer.MAX_VALUE;
                }

                break;
            }
        }

        if (clientPort == -1) {
            throw new RtspServerException(RtspStatus.UNSUPPORTED_TRANSPORT, "client_port required");
        }
        if (clientPort > 65535) {
            throw new RtspServerException(RtspStatus.UNSUPPORTED_TRANSPORT, "invalid client_port");
        }

        //
        // 2. Open the specified file
        //

        if (!req.getPath().endsWith(".mjpeg")) {
            throw new RtspServerException(RtspStatus.UNSUPPORTED_MEDIA_TYPE, "only .mjpeg is currently supported");
        }

        InputStream in;

        // sample.mjpeg is directly embedded inside the .jar
        if (req.getPath().equals(SAMPLE_PATH)) {
            in = Server.class.getClassLoader().getResourceAsStream(SAMPLE_NAME);
        } else {
            try {
                String relativePath = req.getPath().substring(1);
                in = new FileInputStream(relativePath);
            } catch (FileNotFoundException e) {
                throw new RtspServerException(RtspStatus.NOT_FOUND, "resource not found");
            }
        }

        //
        // 3. Set up and run a new RtpSender instance
        //

        InetSocketAddress target = new InetSocketAddress(req.getRemoteAddress().getAddress(), clientPort);
        MjpegParser stream = new MjpegParser(in);
        RtpSender sender;

        try {
            sender = new RtpSender(target, stream);
        } catch (Throwable e) {
            stream.close();
            throw e;
        }

        long sessionId = registerRtpSender(sender);

        sender.addListener(new Service.Listener() {
            @Override
            public void terminated(State from) {
                deregisterRtpSender(sessionId);

                try {
                    stream.close();
                } catch (IOException e) {
                    LOGGER.error("failed to close stream", e);
                }
            }

            @Override
            public void failed(State from, Throwable failure) {
                LOGGER.error("sender failed", failure);
                terminated(from);
            }
        }, MoreExecutors.directExecutor());

        sender.startAsync();

        //
        // 4. Done!
        //

        res.headers().set(RtspHeader.SESSION, Long.toUnsignedString(sessionId));
    }

    private void handleTeardown(RtspIncomingRequest req, RtspOutgoingResponse res) {
        getRTSPSenderForRequest(req).stopAsync();
    }

    private void handlePlay(RtspIncomingRequest req, RtspOutgoingResponse res) {
        getRTSPSenderForRequest(req).setContinue(true);
    }

    private void handlePause(RtspIncomingRequest req, RtspOutgoingResponse res) {
        getRTSPSenderForRequest(req).setContinue(false);
    }

    private long registerRtpSender(RtpSender sender) {
        long sessionId;

        do {
            sessionId = m_sessionIdGenerator.nextLong();
        } while (m_sessions.putIfAbsent(sessionId, sender) != null);

        return sessionId;
    }

    private void deregisterRtpSender(long sessionId) {
        m_sessions.remove(sessionId);
    }

    private RtpSender getRTSPSenderForRequest(RtspIncomingRequest req) throws RtspServerException {
        String sessionHeader = req.headers()
            .get(RtspHeader.SESSION)
            .orElseThrow(() -> new RtspServerException(RtspStatus.BAD_REQUEST, "missing Session header"));

        long sessionId;
        try {
            sessionId = Long.parseUnsignedLong(sessionHeader);
        } catch (NumberFormatException e) {
            throw new RtspServerException(RtspStatus.BAD_REQUEST, "invalid session id");
        }

        RtpSender sender = m_sessions.get(sessionId);

        if (sender == null) {
            throw new RtspServerException(RtspStatus.SESSION_NOT_FOUND, "session not found");
        }

        return sender;
    }
}
