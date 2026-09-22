package com.themoon.y1;

import org.junit.Test;
import java.io.*;
import java.net.Socket;
import java.nio.file.*;
import static org.junit.Assert.*;

public class Y1WebServerTest {
    private String request(Y1WebServer server, String request) throws Exception {
        try (Socket socket = new Socket("127.0.0.1", server.getListeningPort())) {
            socket.setSoTimeout(3000);
            socket.getOutputStream().write(request.getBytes("UTF-8"));
            socket.shutdownOutput();
            ByteArrayOutputStream response = new ByteArrayOutputStream();
            byte[] buffer = new byte[1024];
            int count;
            while ((count = socket.getInputStream().read(buffer)) != -1) response.write(buffer, 0, count);
            return response.toString("UTF-8");
        }
    }

    @Test public void uploadTraversalFailuresAndShutdown() throws Exception {
        File root = Files.createTempDirectory("inni-server-test").toFile();
        Y1WebServer server = new Y1WebServer(null, root, 0);
        server.start();
        try {
            long deadline = System.currentTimeMillis() + 3000;
            while (server.getListeningPort() <= 0 && System.currentTimeMillis() < deadline) Thread.sleep(10);
            assertTrue(server.getListeningPort() > 0);
            assertTrue(request(server, "POST /api/upload?name=song.mp3 HTTP/1.1\r\nContent-Length: 3\r\n\r\nabc").startsWith("HTTP/1.1 200"));
            File song = new File(root, "song.mp3");
            assertEquals("abc", new String(Files.readAllBytes(song.toPath()), "UTF-8"));
            assertTrue(request(server, "POST /api/save?path=song.mp3 HTTP/1.1\r\nContent-Length: 5\r\n\r\nx").startsWith("HTTP/1.1 400"));
            assertEquals("abc", new String(Files.readAllBytes(song.toPath()), "UTF-8"));
            assertTrue(request(server, "GET /api/file?path=..%2Foutside HTTP/1.1\r\n\r\n").startsWith("HTTP/1.1 400"));
            assertTrue(request(server, "POST /api/delete?path= HTTP/1.1\r\n\r\n").startsWith("HTTP/1.1 400"));
            assertTrue(root.isDirectory());
            assertTrue(request(server, "POST /api/delete?path=song.mp3 HTTP/1.1\r\nHost: localhost\r\nOrigin: https://other.example\r\n\r\n").startsWith("HTTP/1.1 400"));
            assertTrue(song.exists());
            assertTrue(request(server, "POST /api/rename?old=song.mp3&new=renamed.mp3 HTTP/1.1\r\n\r\n").startsWith("HTTP/1.1 200"));
            assertTrue(request(server, "GET /api/file?path=renamed.mp3 HTTP/1.1\r\n\r\n").endsWith("abc"));
            assertTrue(request(server, "GET /missing HTTP/1.1\r\n\r\n").startsWith("HTTP/1.1 404"));
        } finally {
            server.stopServer();
            server.join(3000);
            assertFalse(server.isAlive());
            for (File file : root.listFiles()) Files.delete(file.toPath());
            Files.delete(root.toPath());
        }
    }
}
