package org.obliquid.scripts;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.security.KeyStore;
import java.security.MessageDigest;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLException;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;

/*
 * Copyright 2006 Sun Microsystems, Inc.  All Rights Reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *
 *   - Redistributions of source code must retain the above copyright
 *     notice, this list of conditions and the following disclaimer.
 *
 *   - Redistributions in binary form must reproduce the above copyright
 *     notice, this list of conditions and the following disclaimer in the
 *     documentation and/or other materials provided with the distribution.
 *
 *   - Neither the name of Sun Microsystems nor the names of its
 *     contributors may be used to endorse or promote products derived
 *     from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS
 * IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO,
 * THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR
 * PURPOSE ARE DISCLAIMED.  IN NO EVENT SHALL THE COPYRIGHT OWNER OR
 * CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO,
 * PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR
 * PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF
 * LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

/**
 * Usage: java InstallCert <host>[:port] [passphrase]
 * 
 * Install HTTPS certificates, actually save them to a file. That file has to be
 * referenced by the VM. Used to be in SUN blobs, but it has been deleted and I
 * can't find the new URL on Oracle.
 */
public final class InstallCert {

        /** SSL port. */
        private static final int SSL_PORT = 443;

        /**
         * Socket timeout in milliseconds.
         */
        private static final int TIMEOUT_MS = 10000;

        /** Hex digits. */
        private static final char[] HEXDIGITS = "0123456789abcdef".toCharArray();

        /**
         * Main class.
         */
        private InstallCert() {
        }

        /**
         * Execution entry point.
         * 
         * @param args
         *                command line parameters
         * @throws Exception
         *                 in case of problems
         */
        public static void main(final String[] args) throws Exception {
                String host;
                int port;
                char[] passphrase;
                if ((args.length == 1) || (args.length == 2)) {
                        String[] c = args[0].split(":");
                        host = c[0];
                        if (c.length == 1) {
                                port = SSL_PORT;
                        } else {
                                port = Integer.parseInt(c[1]);
                        }
                        String p;
                        if (args.length == 1) {
                                p = "changeit";
                        } else {
                                p = args[1];
                        }
                        passphrase = p.toCharArray();
                } else {
                        System.out.println("Usage: java InstallCert <host>[:port] [passphrase]");
                        return;
                }

                File file = new File("jssecacerts");
                if (!file.isFile()) {
                        char sep = File.separatorChar;
                        File dir = new File(System.getProperty("java.home") + sep + "lib" + sep + "security");
                        file = new File(dir, "jssecacerts");
                        if (!file.isFile()) {
                                file = new File(dir, "cacerts");
                        }
                }
                System.out.println("Loading KeyStore " + file + "...");
                InputStream in = new FileInputStream(file);
                KeyStore ks = KeyStore.getInstance(KeyStore.getDefaultType());
                ks.load(in, passphrase);
                in.close();

                SSLContext context = SSLContext.getInstance("TLS");
                TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory
                                .getDefaultAlgorithm());
                tmf.init(ks);
                X509TrustManager defaultTrustManager = (X509TrustManager) tmf.getTrustManagers()[0];
                SavingTrustManager tm = new SavingTrustManager(defaultTrustManager);
                context.init(null, new TrustManager[] { tm }, null);
                SSLSocketFactory factory = context.getSocketFactory();

                System.out.println("Opening connection to " + host + ":" + port + "...");
                SSLSocket socket = (SSLSocket) factory.createSocket(host, port);
                socket.setSoTimeout(TIMEOUT_MS);
                try {
                        System.out.println("Starting SSL handshake...");
                        socket.startHandshake();
                        socket.close();
                        System.out.println();
                        System.out.println("No errors, certificate is already trusted");
                } catch (SSLException e) {
                        System.out.println();
                        e.printStackTrace(System.out);
                }

                X509Certificate[] chain = tm.chain;
                if (chain == null) {
                        System.out.println("Could not obtain server certificate chain");
                        return;
                }

                BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));

                System.out.println();
                System.out.println("Server sent " + chain.length + " certificate(s):");
                System.out.println();
                MessageDigest sha1 = MessageDigest.getInstance("SHA1");
                MessageDigest md5 = MessageDigest.getInstance("MD5");
                for (int i = 0; i < chain.length; i++) {
                        X509Certificate cert = chain[i];
                        System.out.println(" " + (i + 1) + " Subject " + cert.getSubjectDN());
                        System.out.println("   Issuer  " + cert.getIssuerDN());
                        sha1.update(cert.getEncoded());
                        System.out.println("   sha1    " + toHexString(sha1.digest()));
                        md5.update(cert.getEncoded());
                        System.out.println("   md5     " + toHexString(md5.digest()));
                        System.out.println();
                }

                System.out.println("Enter certificate to add to trusted keystore or 'q' to quit: [1]");
                String line = reader.readLine();
                if (line == null) {
                        line = "";
                }
                line = line.trim();
                int k;
                try {
                        if (line.length() == 0) {
                                k = 0;
                        } else {
                                k = Integer.parseInt(line) - 1;
                        }
                } catch (NumberFormatException e) {
                        System.out.println("KeyStore not changed");
                        return;
                }

                X509Certificate cert = chain[k];
                String alias = host + "-" + (k + 1);
                ks.setCertificateEntry(alias, cert);

                OutputStream out = null;
                try {
                        out = new FileOutputStream("jssecacerts");
                        ks.store(out, passphrase);
                } finally {
                        if (out != null) {
                                out.close();
                        }
                }
                System.out.println();
                System.out.println(cert);
                System.out.println();
                System.out.println("Added certificate to keystore 'jssecacerts' using alias '" + alias + "'");
        }

        /**
         * Convert an array of bytes to hex String.
         * 
         * @param bytes
         *                the array to convert
         * @return an Hex number in String form
         */
        private static String toHexString(final byte[] bytes) {
                final int sizeFactor = 3;
                final int byteMask = 0xFF;
                final int digitBits = 4;
                final int highByteMask = 15;
                StringBuilder sb = new StringBuilder(bytes.length * sizeFactor);
                for (int b : bytes) {
                        b &= byteMask;
                        sb.append(HEXDIGITS[b >> digitBits]);
                        sb.append(HEXDIGITS[b & highByteMask]);
                        sb.append(' ');
                }
                return sb.toString();
        }

        /**
         * Saving Trust Manager implementation.
         * 
         * @author stivlo
         * 
         */
        private static class SavingTrustManager implements X509TrustManager {

                /** A certificate trust manager. */
                private final X509TrustManager tm;

                /** A chain of certificates. */
                private X509Certificate[] chain;

                /**
                 * Constructor.
                 * 
                 * @param tmIn
                 *                a trust manager
                 */
                SavingTrustManager(final X509TrustManager tmIn) {
                        tm = tmIn;
                }

                @Override
                public X509Certificate[] getAcceptedIssuers() {
                        throw new UnsupportedOperationException();
                }

                @Override
                public void checkClientTrusted(final X509Certificate[] chainIn, final String authType)
                                throws CertificateException {
                        throw new UnsupportedOperationException();
                }

                @Override
                public void checkServerTrusted(final X509Certificate[] chainIn, final String authType)
                                throws CertificateException {
                        chain = chainIn;
                        tm.checkServerTrusted(chain, authType);
                }

        }

}
