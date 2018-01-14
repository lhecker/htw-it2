package io.hecker.it2;

import com.google.common.util.concurrent.Service;
import io.hecker.rtp.RtpReceiver;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;

class ClientFrame extends JFrame {
    private static final String INFO_FORMAT = ""
        + "<html><table>"
        + "<tr><th>metric</th><th>absolute</th><th>relative</th></tr>"
        + "<tr><td>expected</td><td>%d</td><td></td></tr>"
        + "<tr><td>lost</td><td>%d</td><td>%6.2f%%</td></tr>"
        + "<tr><td>recovered</td><td>%d</td><td>%6.2f%%</td></tr>"
        + "<tr><td>skipped</td><td>%d</td><td>%6.2f%%</td></tr>"
        + "</table></html>";

    private final Client m_client;
    private final JLabel m_videoCanvas;
    private final JLabel m_infoLabel;
    private boolean m_isPlaying;

    ClientFrame(InetSocketAddress address) {
        super("RTSP client");

        //
        // 1. Set up the GUI
        //

        m_videoCanvas = new JLabel();
        m_videoCanvas.setText("Click here to toggle play/pause");
        m_videoCanvas.setFont(new Font(Font.DIALOG, Font.PLAIN, 32));

        m_infoLabel = new JLabel();
        m_infoLabel.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 24));

        JPanel mainPanel = new JPanel();
        mainPanel.setLayout(new GridLayout(0, 1));
        mainPanel.add(m_videoCanvas);
        mainPanel.add(m_infoLabel);
        getContentPane().add(mainPanel);

        setSize(640, 480);
        setVisible(true);

        //
        // 2. Set up the RTSP client and the RTP receiver
        //

        m_client = new Client(address, "/sample.mjpeg", this::update);
        m_client.addListener(new Service.Listener() {
            @Override
            public void running() {
                createInfoTimer();
            }

            @Override
            public void failed(Service.State from, Throwable failure) {
                failure.printStackTrace();
                System.exit(1);
            }
        }, AwtExecutor.instance());
        m_client.startAsync();

        // 3. Add GUI handlers combining the above (exit, pause/play)
        //

        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                shutdown();
            }
        });

        addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent event) {
                setIsPlaying(!m_isPlaying);
            }
        });
    }

    private void createInfoTimer() {
        final Timer infoTimer = new Timer(250, e -> onInfoTimeout());
        infoTimer.setRepeats(true);
        infoTimer.start();

        onInfoTimeout();
    }

    private void shutdown() {
        try {
            m_client.stopAsync();
            m_client.awaitTerminated();
            System.exit(0);
        } catch (Throwable e) {
            e.printStackTrace();
            System.exit(1);
        }
    }

    private void setIsPlaying(boolean isPlaying) {
        m_isPlaying = isPlaying;
        m_videoCanvas.setText(null);

        try {
            m_client.setPlay(isPlaying);
        } catch (Throwable e) {
            e.printStackTrace();
            System.exit(1);
        }
    }

    private void onInfoTimeout() {
        RtpReceiver receiver = m_client.getReceiver();
        m_infoLabel.setText(
            String.format(
                INFO_FORMAT,
                receiver.getExpectedPacketCount(),
                receiver.getPacketsLostCount(),
                receiver.getRelativePacketsLost() * 100.0,
                receiver.getPacketsRecoveredCount(),
                receiver.getRelativePacketRecovery() * 100.0,
                receiver.getPacketsSkippedCount(),
                receiver.getRelativePacketsSkipped() * 100.0
            )
        );
    }

    private void update(ByteBuffer data) {
        Image image = Toolkit.getDefaultToolkit().createImage(data.array(), data.arrayOffset(), data.limit());
        EventQueue.invokeLater(() -> update(image));
    }

    private void update(Image image) {
        m_videoCanvas.setIcon(new ImageIcon(image));
    }
}
