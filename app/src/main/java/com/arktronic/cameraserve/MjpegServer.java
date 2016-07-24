package com.arktronic.cameraserve;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;

public class MjpegServer implements Runnable {
    private static volatile int port = 8080;
    public static void setPort(int portNum) {
        port = portNum;
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
                MjpegSocket mjpegSocket = new MjpegSocket(socket);
                new Thread(mjpegSocket).start();
            } catch (IOException e) {
                e.printStackTrace();
                // and continue
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
