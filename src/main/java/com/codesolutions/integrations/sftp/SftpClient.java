package com.codesolutions.integrations.sftp;

import com.jcraft.jsch.Channel;
import com.jcraft.jsch.ChannelSftp;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.Session;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Properties;

/**
 * SFTP client for exchanging batch files with the legacy mainframe.
 *
 * The JD calls out "integrações com sistemas externos via FTP, SFTP".
 * JSch is the standard JVM SFTP client; we keep the API narrow so
 * tests can fake it.
 */
public class SftpClient implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(SftpClient.class);

    private final Session session;
    private final ChannelSftp channel;

    public SftpClient(String host, int port, String user, String privateKeyPath) throws Exception {
        JSch jsch = new JSch();
        jsch.addIdentity(privateKeyPath);
        this.session = jsch.getSession(user, host, port);

        Properties cfg = new Properties();
        cfg.put("StrictHostKeyChecking", "no");
        session.setConfig(cfg);
        session.connect();

        Channel ch = session.openChannel("sftp");
        ch.connect();
        this.channel = (ChannelSftp) ch;
    }

    public void upload(String remotePath, byte[] content) throws Exception {
        try (InputStream is = new java.io.ByteArrayInputStream(content)) {
            channel.put(is, remotePath);
        }
        log.info("Uploaded {} bytes to sftp://{}{}", content.length, session.getHost(), remotePath);
    }

    public String downloadText(String remotePath) throws Exception {
        try (InputStream is = channel.get(remotePath)) {
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    @Override
    public void close() {
        if (channel != null && channel.isConnected()) channel.disconnect();
        if (session != null && session.isConnected()) session.disconnect();
    }
}
