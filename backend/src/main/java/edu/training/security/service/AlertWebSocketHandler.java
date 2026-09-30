package edu.training.security.service;

import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class AlertWebSocketHandler extends TextWebSocketHandler {

    private final Set<WebSocketSession> sessions = ConcurrentHashMap.newKeySet();

    @Override
    public void afterConnectionEstablished(WebSocketSession s) {
        sessions.add(s);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession s, CloseStatus st) {
        sessions.remove(s);
    }

    public void broadcast(String msg) {
        for (WebSocketSession s : sessions) {
            if (s.isOpen()) {
                try {
                    s.sendMessage(new TextMessage(msg));
                } catch (IOException ignored) {
                }
            }
        }
    }

    public int clientCount() {
        return sessions.size();
    }
}
