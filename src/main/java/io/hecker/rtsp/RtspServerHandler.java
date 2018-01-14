package io.hecker.rtsp;

@FunctionalInterface
public interface RtspServerHandler {
    void accept(RtspIncomingRequest req, RtspOutgoingResponse res) throws Exception;
}
