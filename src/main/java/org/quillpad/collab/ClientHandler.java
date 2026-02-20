package org.quillpad.collab;

import java.io.*;
import java.net.Socket;

public class ClientHandler implements Runnable {
    private final Socket socket;
    private final CollabServer server;
    private ObjectOutputStream output;
    private ObjectInputStream input;
    private volatile boolean connected;
    private String userId;
    
    public ClientHandler(Socket socket, CollabServer server) {
        this.socket = socket;
        this.server = server;
        this.connected = true;
        
        try {
            output = new ObjectOutputStream(socket.getOutputStream());
            output.flush();
            input = new ObjectInputStream(socket.getInputStream());
        } catch (IOException e) {
            System.err.println("Error setting up streams: " + e.getMessage());
            connected = false;
        }
    }
    
    @Override
    public void run() {
        try {
            while (connected && !socket.isClosed()) {
                try {
                    Message message = (Message) input.readObject();
                    handleMessage(message);
                } catch (ClassNotFoundException e) {
                    System.err.println("Invalid message received: " + e.getMessage());
                } catch (IOException e) {
                    if (connected) {
                        System.out.println("Client disconnected: " + userId);
                    }
                    break;
                }
            }
        } finally {
            disconnect();
        }
    }
    
    private void handleMessage(Message message) {
        if (message.getType() != Message.Type.CONNECT && (userId == null || userId.isBlank())) {
            String sender = message.getSenderId();
            if (sender != null && !sender.isBlank()) {
                this.userId = server.registerClient(sender, this);
                sendMessage(Message.connect(this.userId, sender));
                server.sendDocumentList(this);
            } else {
                sendMessage(Message.error("Connect first before sending collaboration actions"));
                return;
            }
        }

        switch (message.getType()) {
            case CONNECT:
                String requestedUserId = message.getSenderId();
                this.userId = server.registerClient(requestedUserId, this);
                sendMessage(Message.connect(this.userId, requestedUserId));
                server.sendDocumentList(this);
                System.out.println("User connected: requested=" + requestedUserId + ", assigned=" + userId);
                break;
                
            case DISCONNECT:
                connected = false;
                break;
                
            case JOIN_DOCUMENT:
                server.handleJoinDocument(userId, message.getDocumentId(), this);
                break;
                
            case LEAVE_DOCUMENT:
                server.handleLeaveDocument(userId, message.getDocumentId());
                break;
                
            case DOCUMENT_UPDATE:
                if (message.getDocument() != null) {
                    server.handleDocumentUpdate(userId, message.getDocumentId(), message.getDocument(), this);
                }
                break;
                
            case CREATE_DOCUMENT:
                server.handleCreateDocument(userId, message.getPayload(), this);
                break;
                
            case DELETE_DOCUMENT:
                server.handleDeleteDocument(userId, message.getDocumentId(), this);
                break;
                
            case CURSOR_UPDATE:
                try {
                    int position = Integer.parseInt(message.getPayload());
                    server.handleCursorUpdate(userId, message.getDocumentId(), position);
                } catch (NumberFormatException e) {
                    System.err.println("Invalid cursor position: " + message.getPayload());
                }
                break;

            case REQUEST_WRITE_ACCESS:
                server.handleRequestWriteAccess(userId, message.getDocumentId(), this);
                break;

            case RELEASE_WRITE_ACCESS:
                server.handleReleaseWriteAccess(userId, message.getDocumentId(), this);
                break;

            case GRANT_WRITE_ACCESS:
                server.handleGrantWriteAccess(userId, message.getDocumentId(), message.getPayload(), this);
                break;
                
            default:
                System.out.println("Unknown message type: " + message.getType());
        }
    }
    
    public void sendMessage(Message message) {
        if (connected && output != null) {
            try {
                output.writeObject(message);
                output.flush();
            } catch (IOException e) {
                System.err.println("Error sending message to " + userId + ": " + e.getMessage());
                disconnect();
            }
        }
    }
    
    public void disconnect() {
        connected = false;
        
        if (userId != null) {
            server.unregisterClient(userId);
        }
        
        try {
            if (input != null) input.close();
            if (output != null) output.close();
            if (socket != null && !socket.isClosed()) socket.close();
        } catch (IOException e) {
            System.err.println("Error closing connection: " + e.getMessage());
        }
    }
    
    public String getUserId() {
        return userId;
    }
    
    public boolean isConnected() {
        return connected;
    }
}
