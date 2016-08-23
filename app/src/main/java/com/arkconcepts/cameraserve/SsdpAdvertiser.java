package com.arkconcepts.cameraserve;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.wifi.WifiManager;
import android.os.PowerManager;
import android.preference.PreferenceManager;
import android.text.format.Formatter;

import org.apache.http.Header;
import org.apache.http.HttpMessage;
import org.apache.http.HttpRequest;
import org.apache.http.HttpRequestFactory;
import org.apache.http.MethodNotSupportedException;
import org.apache.http.RequestLine;
import org.apache.http.impl.io.AbstractSessionInputBuffer;
import org.apache.http.impl.io.HttpRequestParser;
import org.apache.http.message.BasicHttpRequest;
import org.apache.http.params.BasicHttpParams;
import org.apache.http.params.HttpParams;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.net.MulticastSocket;
import java.net.SocketTimeoutException;

public class SsdpAdvertiser implements Runnable {
    private boolean enabled = false;
    private WifiManager wifiManager;
    private WifiManager.MulticastLock multicastLock;
    private PowerManager powerManager;
    private PowerManager.WakeLock wakeLock;

    private static InetSocketAddress ssdpSocketAddress = new InetSocketAddress("239.255.255.250", 1900);
    private static String serviceType = "urn:arkconcepts-com:service:camera-mjpeg:1";
    private static String responseTemplate = "HTTP/1.1 200 OK\r\n" +
            "Ext: \r\n" +
            "Cache-Control: max-age=120, no-cache=\"Ext\"\r\n" +
            "ST: " + serviceType + "\r\n" +
            "USN: %s::" + serviceType + "\r\n" +
            "Server: CameraServe/" + BuildConfig.VERSION_NAME + "\r\n" +
            "X-Stream-Location: http://%s:%s/\r\n\r\n";

    @Override
    public void run() {
        try {
            setup();
            runLoop();
        } finally {
            teardown();
        }
    }

    private void setup() {
        Context ctx = AndroidApplication.getInstance().getApplicationContext();
        wifiManager = (WifiManager) ctx.getSystemService(Context.WIFI_SERVICE);
        multicastLock = wifiManager.createMulticastLock("SSDP-Lock");
        powerManager = (PowerManager) ctx.getSystemService(Context.POWER_SERVICE);
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Multicast-Lock");
    }

    private void runLoop() {
        MulticastSocket receivingSocket = null;
        DatagramPacket packet = new DatagramPacket(new byte[2048], 2048);

        while (true) {
            if (ConnectivityChangeReceiver.Changed) {
                enabled = false;
                ConnectivityChangeReceiver.Changed = false;
            }

            if (!enabled && shouldBeEnabled()) {
                try {
                    if (!multicastLock.isHeld()) multicastLock.acquire();
                    if (!wakeLock.isHeld()) wakeLock.acquire();
                    if (receivingSocket != null && !receivingSocket.isClosed()) receivingSocket.close();
                    receivingSocket = new MulticastSocket(ssdpSocketAddress.getPort());
                    receivingSocket.setReuseAddress(true);
                    receivingSocket.joinGroup(ssdpSocketAddress.getAddress());
                    receivingSocket.setSoTimeout(3000);
                    enabled = true;
                } catch (IOException e) {
                    e.printStackTrace();
                    receivingSocket = null;
                    if (multicastLock.isHeld()) multicastLock.release();
                    if (wakeLock.isHeld()) wakeLock.release();
                    enabled = false;
                }
            } else if (enabled && !shouldBeEnabled()) {
                try {
                    if (receivingSocket != null) {
                        receivingSocket.leaveGroup(ssdpSocketAddress.getAddress());
                        receivingSocket.close();
                        receivingSocket = null;
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
                if (multicastLock.isHeld()) multicastLock.release();
                if (wakeLock.isHeld()) wakeLock.release();
                enabled = false;
            }

            if (enabled) {
                try {
                    receivingSocket.receive(packet);
                    processSsdpPacket(packet);
                } catch (SocketTimeoutException ste) {
                    // continue silently
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }

            sleep(700);
        }
    }

    private void processSsdpPacket(DatagramPacket packet) {
        byte[] data = new byte[packet.getLength()];
        System.arraycopy(packet.getData(), packet.getOffset(), data, 0, packet.getLength());
        HttpMessage message = getHttpMessage(data);
        if (message == null) return;

        Header mandatoryExtensionHeader = message.getFirstHeader("man");
        if (mandatoryExtensionHeader == null || !mandatoryExtensionHeader.getValue().equals("\"ssdp:discover\""))
            return;

        Header serviceTypeHeader = message.getFirstHeader("st");
        if (serviceTypeHeader == null) return;
        if (serviceTypeHeader.getValue().equals("ssdp:all") || serviceTypeHeader.getValue().equals(serviceType)) {
            // respond

            String ip = Formatter.formatIpAddress(wifiManager.getConnectionInfo().getIpAddress());
            String response = String.format(responseTemplate, getServiceId(), ip, getPort());
            DatagramPacket responsePacket = new DatagramPacket(response.getBytes(), response.length(), packet.getAddress(), packet.getPort());

            try {
                DatagramSocket sendingSocket = new DatagramSocket();
                sendingSocket.send(responsePacket);
                sendingSocket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private HttpMessage getHttpMessage(final byte[] data) {
        final HttpParams httpParams = new BasicHttpParams();
        final AbstractSessionInputBuffer inputBuffer = new AbstractSessionInputBuffer() {
            {
                init(new ByteArrayInputStream(data), 128, httpParams);
            }

            @Override
            public boolean isDataAvailable(int i) throws IOException {
                return this.hasBufferedData();
            }
        };
        final HttpRequestFactory msearchRequestFactory = new HttpRequestFactory() {
            @Override
            public HttpRequest newHttpRequest(RequestLine requestLine) throws MethodNotSupportedException {
                if (!requestLine.getMethod().equalsIgnoreCase("m-search"))
                    throw new MethodNotSupportedException("Invalid method: " + requestLine.getMethod());
                if (!requestLine.getUri().equals("*"))
                    throw new MethodNotSupportedException("Invalid URI: " + requestLine.getUri());

                return new BasicHttpRequest(requestLine);
            }

            @Override
            public HttpRequest newHttpRequest(String method, String uri) throws MethodNotSupportedException {
                if (!method.equalsIgnoreCase("m-search"))
                    throw new MethodNotSupportedException("Invalid method: " + method);
                if (!uri.equals("*"))
                    throw new MethodNotSupportedException("Invalid URI: " + uri);

                return new BasicHttpRequest(method, uri);
            }
        };

        HttpRequestParser requestParser = new HttpRequestParser(inputBuffer, null, msearchRequestFactory, httpParams);
        try {
            return requestParser.parse();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    private void teardown() {
        if (multicastLock != null && multicastLock.isHeld())
            multicastLock.release();
        multicastLock = null;

        if (wakeLock != null && wakeLock.isHeld())
            wakeLock.release();
        wakeLock = null;
    }

    private boolean shouldBeEnabled() {
        Context ctx = AndroidApplication.getInstance().getApplicationContext();
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(ctx);

        return preferences.getBoolean("discoverable", false);
    }

    private String getServiceId() {
        Context ctx = AndroidApplication.getInstance().getApplicationContext();
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(ctx);

        return preferences.getString("ssdp_id", "UNKNOWN");
    }

    private String getPort() {
        Context ctx = AndroidApplication.getInstance().getApplicationContext();
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(ctx);

        return preferences.getString("port", "8080");
    }

    private void sleep(int msec) {
        try {
            Thread.sleep(msec);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }
}
