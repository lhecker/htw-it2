package io.hecker.rtp;

import java.util.concurrent.TimeUnit;

class RateLimiter {
    private long m_previousTimestamp;
    private long m_targetTime;
    private boolean m_gotPrevious = false;

    synchronized long getSleepTime(long timestamp) {
        if (!m_gotPrevious) {
            return 0;
        }

        long delta = Math.max(0, timestamp - m_previousTimestamp);
        long now = TimeUnit.NANOSECONDS.toMillis(System.nanoTime());
        long targetTime = m_targetTime + delta;
        return Math.max(0, targetTime - now);
    }

    synchronized void present(long timestamp) {
        long now = TimeUnit.NANOSECONDS.toMillis(System.nanoTime());
        long delta = 0;

        if (m_gotPrevious) {
            delta = Math.max(0, timestamp - m_previousTimestamp);
        }

        m_previousTimestamp = timestamp;
        m_targetTime = Math.max(now, m_targetTime + delta);
        m_gotPrevious = true;
    }

    synchronized void reset() {
        m_gotPrevious = false;
    }
}
