package io.hecker.rtsp;

import javax.annotation.Nonnull;
import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAccessor;
import java.util.*;

public class RtspHeaderMap implements Iterable<Map.Entry<RtspHeader, String>> {
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.RFC_1123_DATE_TIME.withZone(ZoneOffset.UTC);

    private final EnumMap<RtspHeader, String> m_map = new EnumMap<>(RtspHeader.class);

    RtspHeaderMap() {
    }

    public boolean contains(RtspHeader key) {
        return m_map.containsKey(key);
    }

    public void setIfAbsent(RtspHeader key, String value) {
        m_map.putIfAbsent(key, value);
    }

    public void setDateToNow() {
        setDate(Instant.now());
    }

    private void setDate(TemporalAccessor temporal) {
        set(RtspHeader.DATE, DATE_FORMATTER.format(temporal));
    }

    public void set(RtspHeader key, String value) {
        m_map.put(key, value);
    }

    public void remove(RtspHeader key) {
        m_map.remove(key);
    }

    @Nonnull
    @Override
    public Iterator<Map.Entry<RtspHeader, String>> iterator() {
        return m_map.entrySet().iterator();
    }

    void deserializeFrom(DataInput input) throws IOException, RtspServerException {
        while (true) {
            // Get a header line - we do not support line breaks in header lines.
            String line = input.readLine();
            if (line == null || line.isEmpty()) {
                break;
            }

            // Find the colon separating the key from the value.
            int idx = line.indexOf(':');
            if (idx == -1) {
                throw new RtspServerException(RtspStatus.BAD_REQUEST, "invalid header line: " + line);
            }

            String keyStr = line.substring(0, idx).toLowerCase(Locale.ROOT);
            String val = line.substring(idx + 1).trim();

            RtspHeader key;
            try {
                key = RtspHeader.valueOfLowerHypenated(keyStr);
            } catch (IllegalArgumentException ignored) {
                continue;
            }

            if (val.isEmpty()) {
                continue;
            }

            // Add the value to the list of of values for this header key.
            m_map.compute(key, (k, v) -> (v == null) ? val : v + ", " + val);
        }
    }

    void serializeInto(DataOutput output) throws IOException {
        for (Map.Entry<RtspHeader, String> entry : this) {
            output.writeBytes(entry.getKey().toString());
            output.writeBytes(": ");
            output.writeBytes(entry.getValue());
            output.writeBytes("\r\n");
        }
    }

    void copySelectionFrom(RtspHeaderMap from, RtspHeader... keys) {
        for (RtspHeader key : keys) {
            from.get(key).ifPresent(val -> set(key, val));
        }
    }

    public Optional<String> get(RtspHeader key) {
        return Optional.ofNullable(m_map.get(key));
    }
}
