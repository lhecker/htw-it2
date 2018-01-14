package io.hecker.rtsp;

import com.google.common.util.concurrent.AbstractExecutionThreadService;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.common.util.concurrent.Service;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Collection;

public class RtspServer extends AbstractExecutionThreadService {
    private static final Logger LOGGER = LogManager.getLogger();

    private final Collection<RtspServerHandler> m_handlers = new ArrayList<>();
    private final ServerSocket m_serverSocket;

    protected RtspServer(InetSocketAddress address) throws IOException {
        m_serverSocket = new ServerSocket(address.getPort(), 0, address.getAddress());
    }

    protected void addHandler(RtspServerHandler handler) {
        synchronized (m_handlers) {
            m_handlers.add(handler);
        }
    }

    protected void run() {
        while (true) {
            Socket socket;

            try {
                socket = m_serverSocket.accept();
            } catch (IOException e) {
                break;
            }

            LOGGER.info("accepted connection from {}", socket.getRemoteSocketAddress());

            Service connection;
            try {
                connection = new Connection(socket);
            } catch (IOException e) {
                e.printStackTrace();
                continue;
            }

            connection.addListener(new Listener() {
                @Override
                public void failed(State from, Throwable failure) {
                    LOGGER.error("connection service failed", failure);
                }
            }, MoreExecutors.directExecutor());
            connection.startAsync();
        }
    }

    private void handle(RtspIncomingRequest req, RtspOutgoingResponse res) throws Exception {
        LOGGER.info("handling method={} path={}", req.getMethod(), req.getPath());

        synchronized (m_handlers) {
            for (RtspServerHandler handler : m_handlers) {
                handler.accept(req, res);
            }
        }

        LOGGER.info("handled method={} path={} status={}", req.getMethod(), req.getPath(), res.getStatus().code());
    }

    private class Connection extends AbstractExecutionThreadService {
        private final Socket m_socket;
        private final DataInputStream m_input;
        private final DataOutputStream m_output;

        Connection(Socket socket) throws IOException {
            m_socket = socket;
            m_input = new DataInputStream(socket.getInputStream());
            m_output = new DataOutputStream(socket.getOutputStream());
        }

        protected void run() throws Exception {
            while (isRunning()) {
                RtspIncomingRequest req = null;
                RtspOutgoingResponse res = new RtspOutgoingResponse();

                try {
                    req = new RtspIncomingRequest(m_input, (InetSocketAddress) m_socket.getRemoteSocketAddress());
                    handle(req, res);
                } catch (EOFException e) {
                    break;
                } catch (Throwable e) {
                    RtspStatus status = RtspStatus.INTERNAL_SERVER_ERROR;
                    if (e instanceof RtspServerException) {
                        status = ((RtspServerException) e).getStatus();
                    }

                    res = new RtspOutgoingResponse();
                    res.setStatus(status);
                    res.setBody(e.getMessage());
                }

                if (req != null) {
                    res.headers().copySelectionFrom(req.headers(), RtspHeader.CSEQ);
                }

                res.headers().setIfAbsent(RtspHeader.CONTENT_TYPE, "text/plain");
                res.headers().setDateToNow();

                res.serializeInto(m_output);

                m_output.flush();

                // On invalid requests we terminate the connection
                if (req == null) {
                    break;
                }
            }
        }

        @Override
        protected void triggerShutdown() {
            try {
                m_socket.close();
            } catch (Throwable ignored) {
            }
        }

        @Override
        protected void shutDown() throws Exception {
            m_socket.close();
        }
    }

    @Override
    protected void triggerShutdown() {
        try {
            m_serverSocket.close();
        } catch (Throwable ignored) {
        }
    }


    @Override
    protected void shutDown() throws Exception {
        m_serverSocket.close();
    }


}
