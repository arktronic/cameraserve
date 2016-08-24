package com.arkconcepts.cameraserve;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;

public class MjpegServer implements Runnable {
    private static volatile int port = 8080;
    public static void setPort(int portNum) {
        port = portNum;
    }

    private static volatile boolean allIpsAllowed = false;
    public static void setAllIpsAllowed(boolean allowAll) {
        allIpsAllowed = allowAll;
    }

    @Override
    public void run() {
        android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_BACKGROUND);

        ServerSocket server;
        try {
            server = new ServerSocket(port);
            server.setSoTimeout(5000);
        } catch (IOException e) {
            e.printStackTrace();
            return;
        }

        while(true) {
            try {
                Socket socket = server.accept();
                if (allIpsAllowed || socket.getInetAddress().isSiteLocalAddress()) {
                    MjpegSocket mjpegSocket = new MjpegSocket(socket);
                    new Thread(mjpegSocket).start();
                } else {
                    socket.close();
                }
            } catch (SocketTimeoutException ste) {
                // continue silently
            } catch (IOException ioe) {
                ioe.printStackTrace();
            }

            if(port != server.getLocalPort()) {
                try {
                    server.close();
                    server = new ServerSocket(port);
                    server.setSoTimeout(5000);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }

    }
}
