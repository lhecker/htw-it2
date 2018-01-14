package io.hecker.rtp;

import com.google.common.util.concurrent.AbstractExecutionThreadService;
import com.google.common.util.concurrent.Monitor;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.annotation.Nullable;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.util.PriorityQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

public class RtpReceiver extends AbstractExecutionThreadService {
    private static final Logger LOGGER = LogManager.getLogger();
    private static final int MIN_QUEUE_SIZE = RtpFecPacket.FEC_MAX_SIZE;
    private static final int BUFFER_QUEUE_SIZE = 2 * MIN_QUEUE_SIZE;
    private static final int MAX_QUEUE_SIZE = 3 * MIN_QUEUE_SIZE;
    private static final int MAX_BUFFER_SECONDS = 3;

    private final DatagramSocket m_socket;

    private final AtomicLong m_expectedPacketCount = new AtomicLong();
    private final AtomicLong m_packetsLostCount = new AtomicLong();
    private final AtomicLong m_packetsRecoveredCount = new AtomicLong();

    private final PriorityQueue<RtpRegularPacket> m_queue = new PriorityQueue<>(MAX_QUEUE_SIZE);
    private final Monitor m_queueMonitor = new Monitor();
    private final Monitor.Guard m_queueNotFullGuard = new Monitor.Guard(m_queueMonitor) {
        @Override
        public boolean isSatisfied() {
            return m_queue.size() < MAX_QUEUE_SIZE;
        }
    };
    private final Monitor.Guard m_queueBufferedGuard = new Monitor.Guard(m_queueMonitor) {
        @Override
        public boolean isSatisfied() {
            return m_queue.size() > BUFFER_QUEUE_SIZE;
        }
    };

    private final RateLimiter m_rateLimiter = new RateLimiter();

    // NOTE: Only to be accessed by the service thread
    private int previousSequenceNumber = 0;
    private boolean gotPreviousSequenceNumber = false;

    public RtpReceiver(InetSocketAddress address) throws IOException {
        m_socket = new DatagramSocket(address);
    }

    private static double safeFraction(long dividend, long divisor) {
        return divisor != 0 ? (double) dividend / (double) divisor : 0.0;
    }

    public int getLocalPort() {
        return m_socket.getLocalPort();
    }

    public long getExpectedPacketCount() {
        return m_expectedPacketCount.get();
    }

    public long getPacketsLostCount() {
        return m_packetsLostCount.get();
    }

    public double getRelativePacketsLost() {
        return safeFraction(getPacketsLostCount(), getExpectedPacketCount());
    }

    public long getPacketsRecoveredCount() {
        return m_packetsRecoveredCount.get();
    }

    public double getRelativePacketRecovery() {
        return safeFraction(getPacketsRecoveredCount(), getExpectedPacketCount());
    }

    public long getPacketsSkippedCount() {
        return getPacketsLostCount() - getPacketsRecoveredCount();
    }

    public double getRelativePacketsSkipped() {
        return safeFraction(getPacketsSkippedCount(), getExpectedPacketCount());
    }

    public RtpRegularPacket next() throws Exception {
        m_queueMonitor.enter();
        try {
            unsafeFillQueue();
            unsafeAwaitQueueHeadPresentable();
            return unsafePopQueueHead();
        } finally {
            m_queueMonitor.leave();
        }
    }

    private void unsafeFillQueue() throws InterruptedException {
        if (m_queue.size() >= MIN_QUEUE_SIZE) {
            return;
        }

        LOGGER.info("waiting for queue to be filled");
        m_rateLimiter.reset();

        do {
            m_queueMonitor.waitFor(m_queueBufferedGuard, MAX_BUFFER_SECONDS, TimeUnit.SECONDS);
        } while (m_queue.size() < MIN_QUEUE_SIZE);
    }

    private void unsafeAwaitQueueHeadPresentable() throws InterruptedException {
        boolean queueHeadChanged = false;

        do {
            RtpRegularPacket packet = m_queue.peek();

            long sleepTime = m_rateLimiter.getSleepTime(packet.getTimestamp());
            if (sleepTime > 0) {
                // Sleep until the time to present (return) the packet has passed, or retry
                // if the queue head has changed due to packet reordering or FEC recovery.
                LOGGER.debug("waiting for queue head for {}ms", sleepTime);
                queueHeadChanged = m_queueMonitor.waitFor(new QueueHeadChangedGuard(packet), sleepTime, TimeUnit.MILLISECONDS);
                if (queueHeadChanged) {
                    LOGGER.debug("queue head changed");
                }
            }
        } while (queueHeadChanged);
    }

    private RtpRegularPacket unsafePopQueueHead() {
        RtpRegularPacket packet = m_queue.poll();
        LOGGER.debug("removing packet seq={}", packet.getSequenceNumber());
        m_rateLimiter.present(packet.getTimestamp());
        return packet;
    }

    @Override
    protected void triggerShutdown() {
        m_socket.close();
    }

    @Override
    protected void run() throws Exception {
        while (isRunning()) {
            RtpPacket packet = receivePacket();
            if (packet == null) {
                return;
            }

            updateReceiveStats(packet);

            m_queueMonitor.enterWhen(m_queueNotFullGuard);
            try {
                if (packet instanceof RtpFecPacket) {
                    handleFecPacket((RtpFecPacket) packet);
                } else {
                    handleRegularPacket((RtpRegularPacket) packet);
                }
            } finally {
                m_queueMonitor.leave();
            }
        }
    }

    @Nullable
    private RtpPacket receivePacket() {
        ByteBuffer bb = ByteBuffer.allocate(64 * 1024);
        DatagramPacket datagramPacket = new DatagramPacket(bb.array(), bb.limit());

        while (true) {
            try {
                m_socket.receive(datagramPacket);
            } catch (IOException ignored) {
                return null;
            }

            // Make sure the datagram didn't get truncated.
            // (Theoretically impossible to happen with the above Buffer size of 64 kiB.)
            if (datagramPacket.getLength() == bb.limit()) {
                continue;
            }

            ByteBuffer packetData = bb.duplicate();
            packetData.limit(datagramPacket.getLength());

            RtpPacket packet;

            try {
                switch (RtpPacket.guessType(packetData)) {
                    case UNKNOWN:
                        continue;
                    case FEC:
                        packet = new RtpFecPacket(packetData);
                        break;
                    default:
                        packet = new RtpRegularPacket(packetData);
                        break;
                }
            } catch (Throwable e) {
                LOGGER.debug("failed to parse packet", e);
                continue;
            }

            LOGGER.debug("received packet seq={} len={}", packet.getSequenceNumber(), packetData.remaining());
            return packet;
        }
    }

    private void handleFecPacket(RtpFecPacket packet) {
        packet.recover(m_queue).ifPresent(p -> {
            LOGGER.info("recovered seq={}", p.getSequenceNumber());
            m_packetsRecoveredCount.getAndIncrement();
            handleRegularPacket(p);
        });
    }

    private void handleRegularPacket(RtpRegularPacket packet) {
        LOGGER.debug("adding packet seq={}", packet.getSequenceNumber());
        m_queue.add(packet);
    }

    private void updateReceiveStats(RtpPacket packet) {
        if (gotPreviousSequenceNumber) {
            int seqDelta = (packet.getSequenceNumber() - previousSequenceNumber) & 0xffff;
            m_expectedPacketCount.getAndAdd(seqDelta);
            m_packetsLostCount.getAndAdd(seqDelta - 1);
        } else {
            m_expectedPacketCount.getAndIncrement();
        }

        previousSequenceNumber = packet.getSequenceNumber();
        gotPreviousSequenceNumber = true;
    }

    private class QueueHeadChangedGuard extends Monitor.Guard {
        private final RtpRegularPacket m_currentHead;

        QueueHeadChangedGuard(RtpRegularPacket currentHead) {
            super(m_queueMonitor);
            m_currentHead = currentHead;
        }

        @Override
        public boolean isSatisfied() {
            RtpRegularPacket head = m_queue.peek();
            return head != null && head != m_currentHead;
        }
    }
}
