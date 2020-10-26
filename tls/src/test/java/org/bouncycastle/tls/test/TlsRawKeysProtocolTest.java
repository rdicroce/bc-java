package org.bouncycastle.tls.test;

import java.io.OutputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.security.SecureRandom;

import org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters;
import org.bouncycastle.tls.AlertDescription;
import org.bouncycastle.tls.CertificateType;
import org.bouncycastle.tls.TlsClient;
import org.bouncycastle.tls.TlsClientProtocol;
import org.bouncycastle.tls.TlsExtensionsUtils;
import org.bouncycastle.tls.TlsFatalAlertReceived;
import org.bouncycastle.tls.TlsServer;
import org.bouncycastle.tls.TlsServerProtocol;
import org.bouncycastle.util.Arrays;
import org.bouncycastle.util.io.Streams;

import junit.framework.TestCase;

public class TlsRawKeysProtocolTest
    extends TestCase
{
    private SecureRandom rng = new SecureRandom();

    public void testClientSendsExtensionButServerDoesNotSupportIt() throws Exception
    {
        MockRawKeysTlsClient client = new MockRawKeysTlsClient(
                CertificateType.X509,
                (short) -1,
                new short[] {CertificateType.RawPublicKey, CertificateType.X509},
                null,
                generateKeypair());
        MockRawKeysTlsServer server = new MockRawKeysTlsServer(
                CertificateType.X509,
                (short) -1,
                null,
                generateKeypair());
        pumpData(client, server);
    }

    public void testExtensionsAreOmittedIfSpecifiedButOnlyContainX509() throws Exception
    {
        MockRawKeysTlsClient client = new MockRawKeysTlsClient(
                CertificateType.X509,
                CertificateType.X509,
                new short[] {CertificateType.X509},
                new short[] {CertificateType.X509},
                generateKeypair());
        MockRawKeysTlsServer server = new MockRawKeysTlsServer(
                CertificateType.X509,
                CertificateType.X509,
                new short[] {CertificateType.X509},
                generateKeypair());
        pumpData(client, server);

        assertFalse(
                "client cert type extension should not be sent",
                server.receivedClientExtensions.containsKey(TlsExtensionsUtils.EXT_client_certificate_type));
        assertFalse(
                "server cert type extension should not be sent",
                server.receivedClientExtensions.containsKey(TlsExtensionsUtils.EXT_server_certificate_type));
    }

    public void testBothSidesUseRawKey() throws Exception
    {
        MockRawKeysTlsClient client = new MockRawKeysTlsClient(
                CertificateType.RawPublicKey,
                CertificateType.RawPublicKey,
                new short[] {CertificateType.RawPublicKey},
                new short[] {CertificateType.RawPublicKey},
                generateKeypair());
        MockRawKeysTlsServer server = new MockRawKeysTlsServer(
                CertificateType.RawPublicKey,
                CertificateType.RawPublicKey,
                new short[] {CertificateType.RawPublicKey},
                generateKeypair());
        pumpData(client, server);
    }

    public void testServerUsesRawKeyAndClientIsAnonymous() throws Exception
    {
        MockRawKeysTlsClient client = new MockRawKeysTlsClient(
                CertificateType.RawPublicKey,
                (short) -1,
                new short[] {CertificateType.RawPublicKey},
                null,
                generateKeypair());
        MockRawKeysTlsServer server = new MockRawKeysTlsServer(
                CertificateType.RawPublicKey,
                (short) -1,
                null,
                generateKeypair());
        pumpData(client, server);
    }

    public void testServerUsesRawKeyAndClientUsesX509() throws Exception
    {
        MockRawKeysTlsClient client = new MockRawKeysTlsClient(
                CertificateType.RawPublicKey,
                CertificateType.X509,
                new short[] {CertificateType.RawPublicKey},
                null,
                generateKeypair());
        MockRawKeysTlsServer server = new MockRawKeysTlsServer(
                CertificateType.RawPublicKey,
                CertificateType.X509,
                null,
                generateKeypair());
        pumpData(client, server);
    }

    public void testServerUsesX509AndClientUsesRawKey() throws Exception
    {
        MockRawKeysTlsClient client = new MockRawKeysTlsClient(
                CertificateType.X509,
                CertificateType.RawPublicKey,
                null,
                new short[] {CertificateType.RawPublicKey},
                generateKeypair());
        MockRawKeysTlsServer server = new MockRawKeysTlsServer(
                CertificateType.X509,
                CertificateType.RawPublicKey,
                new short[] {CertificateType.RawPublicKey},
                generateKeypair());
        pumpData(client, server);
    }

    public void testClientSendsClientCertExtensionButServerHasNoCommonTypes() throws Exception
    {
        try
        {
            MockRawKeysTlsClient client = new MockRawKeysTlsClient(
                    CertificateType.X509,
                    CertificateType.RawPublicKey,
                    null,
                    new short[] {CertificateType.RawPublicKey},
                    generateKeypair());
            MockRawKeysTlsServer server = new MockRawKeysTlsServer(
                    CertificateType.X509,
                    CertificateType.X509,
                    new short[] {CertificateType.X509},
                    generateKeypair());
            pumpData(client, server);
            fail("Should have caused unsupported_certificate alert");
        }
        catch (TlsFatalAlertReceived alert)
        {
            assertEquals("Should have caused unsupported_certificate alert", alert.getAlertDescription(), AlertDescription.unsupported_certificate);
        }
    }

    public void testClientSendsServerCertExtensionButServerHasNoCommonTypes() throws Exception
    {
        try
        {
            MockRawKeysTlsClient client = new MockRawKeysTlsClient(
                    CertificateType.RawPublicKey,
                    CertificateType.RawPublicKey,
                    new short[] {CertificateType.RawPublicKey},
                    null,
                    generateKeypair());
            MockRawKeysTlsServer server = new MockRawKeysTlsServer(
                    CertificateType.X509,
                    CertificateType.RawPublicKey,
                    new short[] {CertificateType.RawPublicKey},
                    generateKeypair());
            pumpData(client, server);
            fail("Should have caused unsupported_certificate alert");
        }
        catch (TlsFatalAlertReceived alert)
        {
            assertEquals("Should have caused unsupported_certificate alert", alert.getAlertDescription(), AlertDescription.unsupported_certificate);
        }
    }

    private Ed25519PrivateKeyParameters generateKeypair()
    {
        return new Ed25519PrivateKeyParameters(rng);
    }

    private void pumpData(TlsClient client, TlsServer server) throws Exception
    {
        SecureRandom secureRandom = new SecureRandom();

        PipedInputStream clientRead = TlsTestUtils.createPipedInputStream();
        PipedInputStream serverRead = TlsTestUtils.createPipedInputStream();
        PipedOutputStream clientWrite = new PipedOutputStream(serverRead);
        PipedOutputStream serverWrite = new PipedOutputStream(clientRead);

        TlsClientProtocol clientProtocol = new TlsClientProtocol(clientRead, clientWrite);
        TlsServerProtocol serverProtocol = new TlsServerProtocol(serverRead, serverWrite);

        ServerThread serverThread = new ServerThread(serverProtocol, server);
        serverThread.start();

        clientProtocol.connect(client);

        // NOTE: Because we write-all before we read-any, this length can't be more than the pipe capacity
        int length = 1000;

        byte[] data = new byte[length];
        secureRandom.nextBytes(data);

        OutputStream output = clientProtocol.getOutputStream();
        output.write(data);

        byte[] echo = new byte[data.length];
        int count = Streams.readFully(clientProtocol.getInputStream(), echo);

        assertEquals(count, data.length);
        assertTrue(Arrays.areEqual(data, echo));

        output.close();

        serverThread.join();
    }

    static class ServerThread
        extends Thread
    {
        private final TlsServerProtocol serverProtocol;
        private final TlsServer server;

        ServerThread(TlsServerProtocol serverProtocol, TlsServer server)
        {
            this.serverProtocol = serverProtocol;
            this.server = server;
        }

        public void run()
        {
            try
            {
                serverProtocol.accept(server);
                Streams.pipeAll(serverProtocol.getInputStream(), serverProtocol.getOutputStream());
                serverProtocol.close();
            }
            catch (Exception e)
            {
//                throw new RuntimeException(e);
            }
        }
    }
}
