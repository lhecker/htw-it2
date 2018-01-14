package io.hecker.rtp;

import com.google.common.util.concurrent.AbstractExecutionThreadService;
import com.google.common.util.concurrent.AtomicDouble;
import com.google.common.util.concurrent.Monitor;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Iterator;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;

import static com.google.common.base.Preconditions.checkArgument;

public class RtpSender extends AbstractExecutionThreadService {
    private static final Logger LOGGER = LogManager.getLogger();
    private static final AtomicDouble SIMULATED_LOSS_RATE = new AtomicDouble();
    private static final AtomicInteger FEC_SIZE = new AtomicInteger();

    private final SocketAddress m_target;
    private final Iterator<VideoFrame> m_stream;

    private final DatagramSocket m_socket = new DatagramSocket();
    private final RateLimiter m_rateLimiter = new RateLimiter();

    private final Deque<RtpRegularPacket> m_fecQueue = new ArrayDeque<>(RtpFecPacket.FEC_MAX_SIZE);
    private final long m_synchronizationSource = ThreadLocalRandom.current().nextInt() & 0xffffffffL;
    private int m_sequenceNumber = ThreadLocalRandom.current().nextInt() & 0xffff;

    private final Monitor m_continueMonitor = new Monitor();
    private boolean m_continue;
    private final Monitor.Guard m_continueGuard = new Monitor.Guard(m_continueMonitor) {
        @Override
        public boolean isSatisfied() {
            return m_continue;
        }
    };

    public RtpSender(SocketAddress target, Iterator<VideoFrame> stream) throws IOException {
        m_target = target;
        m_stream = stream;

        m_socket.connect(target);
    }

    public static void setSimulatedLossRate(double rate) {
        checkArgument(rate >= 0 && rate <= 1, "Expected rate to be in [0,1] but was %f", rate);
        SIMULATED_LOSS_RATE.set(rate);
    }

    public static void setFecSize(int size) {
        checkArgument(
            size == 0 || size >= RtpFecPacket.FEC_MIN_SIZE && size <= RtpFecPacket.FEC_MAX_SIZE,
            "Expected rate to be either 0 or in [%d,%d] but was %d",
            RtpFecPacket.FEC_MIN_SIZE,
            RtpFecPacket.FEC_MAX_SIZE,
            size
        );
        FEC_SIZE.set(size);
    }

    public void setContinue(boolean resume) {
        m_continueMonitor.enter();
        m_continue = resume;
        m_continueMonitor.leave();
    }

    @Override
    protected void run() throws Exception {
        while (isRunning() && m_stream.hasNext()) {
            sendFrame(m_stream.next());
        }
    }

    @Override
    protected void shutDown() {
        m_socket.close();
    }

    @Override
    protected void triggerShutdown() {
        setContinue(true);
    }

    private void sendFrame(VideoFrame frame) throws Exception {
        m_continueMonitor.enter();
        try {
            m_continueMonitor.waitForUninterruptibly(m_continueGuard);
        } finally {
            m_continueMonitor.leave();
        }

        if (!isRunning()) {
            return;
        }

        Thread.sleep(m_rateLimiter.getSleepTime(frame.getTimestamp()));
        m_rateLimiter.present(frame.getTimestamp());

        RtpRegularPacket packet = encode(frame);
        send(packet);
        createFecPacketMaybe(packet);
    }

    private RtpRegularPacket encode(VideoFrame frame) {
        return RtpRegularPacket.builder()
            .withSequenceNumber(nextSequenceNumber())
            .withSynchronizationSource(m_synchronizationSource)
            .withTimestamp(frame.getTimestamp())
            .withPayloadType(frame.getPayloadType())
            .withPayload(frame.getPayload())
            .build();
    }

    private int nextSequenceNumber() {
        m_sequenceNumber = (m_sequenceNumber + 1) & 0xffff;
        return m_sequenceNumber;
    }

    private void createFecPacketMaybe(RtpRegularPacket packet) throws Exception {
        int size = FEC_SIZE.get();
        if (size == 0) {
            return;
        }

        m_fecQueue.add(packet);
        if (m_fecQueue.size() < size) {
            return;
        }

        RtpRegularPacket firstPacket = m_fecQueue.getFirst();
        RtpRegularPacket lastPacket = m_fecQueue.getLast();

        RtpFecPacket fecPacket = RtpFecPacket.builder()
            .withSequenceNumber(nextSequenceNumber())
            .withSynchronizationSource(m_synchronizationSource)
            .withTimestamp(lastPacket.getTimestamp())
            .withSequenceNumberBase(firstPacket.getSequenceNumber())
            .withPackets(m_fecQueue)
            .build();

        m_fecQueue.clear();

        send(fecPacket);
    }

    private void send(RtpPacket packet) throws IOException {
        double simulatedLossRate = SIMULATED_LOSS_RATE.get();
        if (simulatedLossRate > 0 && ThreadLocalRandom.current().nextDouble(1.0) < simulatedLossRate) {
            return;
        }

        ByteBuffer packetData = packet.serialize();
        LOGGER.debug("sending packet seq={} len={}", packet.getSequenceNumber(), packetData.remaining());
        m_socket.send(new DatagramPacket(packetData.array(), packetData.arrayOffset(), packetData.limit(), m_target));
    }
}
