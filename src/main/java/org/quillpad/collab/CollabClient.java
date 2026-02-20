package org.quillpad.collab;

import javafx.application.Platform;
import javafx.beans.property.*;

import java.io.*;
import java.net.Socket;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

public class CollabClient {
    private static final String DEFAULT_HOST = "localhost";
    private static final int DEFAULT_PORT = 9999;
    
    private final String host;
    private final int port;
    
    private Socket socket;
    private ObjectOutputStream output;
    private ObjectInputStream input;
    private volatile boolean connected;
    private Thread listenerThread;
    
    private final StringProperty userId = new SimpleStringProperty();
    private final StringProperty currentDocumentId = new SimpleStringProperty();
    private final BooleanProperty connectedProperty = new SimpleBooleanProperty(false);
    private final ObjectProperty<Document> currentDocument = new SimpleObjectProperty<>();
    private final ListProperty<String> documentList = new SimpleListProperty<>(
            javafx.collections.FXCollections.observableArrayList()
    );
    private final ListProperty<String> activeUsers = new SimpleListProperty<>(
            javafx.collections.FXCollections.observableArrayList()
    );
    
    private final List<Consumer<Message>> messageHandlers = new CopyOnWriteArrayList<>();
    private final List<Runnable> connectionListeners = new CopyOnWriteArrayList<>();
    private final List<Consumer<String>> errorListeners = new CopyOnWriteArrayList<>();
    
    public CollabClient() {
        this(DEFAULT_HOST, DEFAULT_PORT);
    }
    
    public CollabClient(String host, int port) {
        this.host = host;
        this.port = port;
    }
    
    public boolean connect(String userId) {
        if (connected) {
            return true;
        }
        
        try {
            socket = new Socket(host, port);
            output = new ObjectOutputStream(socket.getOutputStream());
            output.flush();
            input = new ObjectInputStream(socket.getInputStream());
            
            this.userId.set(userId);
            connected = true;
            connectedProperty.set(true);
            
            sendMessage(Message.connect(userId, userId));
            
            listenerThread = new Thread(this::listenForMessages);
            listenerThread.setDaemon(true);
            listenerThread.start();
            
            for (Runnable listener : connectionListeners) {
                Platform.runLater(listener);
            }
            
            return true;
        } catch (IOException e) {
            notifyError("Failed to connect to server: " + e.getMessage());
            return false;
        }
    }
    
    public void disconnect() {
        if (!connected) return;
        
        connected = false;
        connectedProperty.set(false);
        
        try {
            if (output != null) {
                sendMessage(Message.disconnect(userId.get()));
            }
            
            if (listenerThread != null) {
                listenerThread.interrupt();
            }
            
            if (input != null) input.close();
            if (output != null) output.close();
            if (socket != null) socket.close();
        } catch (IOException e) {
            System.err.println("Error during disconnect: " + e.getMessage());
        }
        
        Platform.runLater(() -> {
            documentList.clear();
            activeUsers.clear();
            currentDocument.set(null);
            currentDocumentId.set(null);
        });
    }
    
    private void listenForMessages() {
        try {
            while (connected && !Thread.currentThread().isInterrupted()) {
                try {
                    Message message = (Message) input.readObject();
                    handleMessage(message);
                } catch (ClassNotFoundException e) {
                    System.err.println("Invalid message format: " + e.getMessage());
                }
            }
        } catch (IOException e) {
            if (connected) {
                notifyError("Connection lost: " + e.getMessage());
                Platform.runLater(() -> {
                    connected = false;
                    connectedProperty.set(false);
                });
            }
        }
    }
    
    private void handleMessage(Message message) {
        System.out.println("Client received message: " + message.getType() + " from: " + message.getSenderId());
        
        for (Consumer<Message> handler : messageHandlers) {
            Platform.runLater(() -> handler.accept(message));
        }
        
        switch (message.getType()) {
            case DOCUMENT_SYNC:
            case DOCUMENT_UPDATE:
                Document doc = message.getDocument();
                if (doc != null) {
                    Platform.runLater(() -> {
                        currentDocument.set(doc);
                        currentDocumentId.set(doc.getDocumentId());
                    });
                }
                break;
                
            case DOCUMENT_LIST:
                String payload = message.getPayload();
                if (payload != null && !payload.isEmpty()) {
                    Platform.runLater(() -> {
                        List<String> docs = new ArrayList<>();
                        for (String part : payload.split(";")) {
                            String[] parts = part.split(":", 2);
                            if (parts.length == 2) {
                                docs.add(parts[0] + ":" + parts[1]);
                            }
                        }
                        documentList.setAll(javafx.collections.FXCollections.observableArrayList(docs));
                    });
                }
                break;
                
            case USER_LIST:
                String users = message.getPayload();
                if (users != null) {
                    Platform.runLater(() -> {
                        List<String> userList = Arrays.asList(users.split(","));
                        activeUsers.setAll(javafx.collections.FXCollections.observableArrayList(userList));
                    });
                }
                break;
                
            case ERROR:
                notifyError(message.getPayload());
                break;
                
            default:
                break;
        }
    }
    
    public void sendMessage(Message message) {
        if (!connected || output == null) {
            notifyError("Not connected to server");
            return;
        }
        
        try {
            output.writeObject(message);
            output.flush();
        } catch (IOException e) {
            notifyError("Failed to send message: " + e.getMessage());
        }
    }
    
    public void joinDocument(String documentId) {
        sendMessage(Message.joinDocument(userId.get(), documentId));
    }
    
    public void leaveDocument() {
        String docId = currentDocumentId.get();
        if (docId != null) {
            sendMessage(Message.leaveDocument(userId.get(), docId));
            currentDocumentId.set(null);
            currentDocument.set(null);
        }
    }
    
    public void updateDocument(Document document) {
        if (document != null && currentDocumentId.get() != null) {
            sendMessage(Message.documentUpdate(userId.get(), document.getDocumentId(), document));
        }
    }
    
    public void createDocument(String documentName) {
        sendMessage(Message.createDocument(userId.get(), documentName));
    }
    
    public void deleteDocument(String documentId) {
        sendMessage(Message.deleteDocument(userId.get(), documentId));
    }
    
    public void sendCursorPosition(int position) {
        String docId = currentDocumentId.get();
        if (docId != null) {
            sendMessage(Message.cursorUpdate(userId.get(), docId, position));
        }
    }
    
    public void addMessageHandler(Consumer<Message> handler) {
        messageHandlers.add(handler);
    }
    
    public void removeMessageHandler(Consumer<Message> handler) {
        messageHandlers.remove(handler);
    }
    
    public void addConnectionListener(Runnable listener) {
        connectionListeners.add(listener);
    }
    
    public void removeConnectionListener(Runnable listener) {
        connectionListeners.remove(listener);
    }
    
    public void addErrorListener(Consumer<String> listener) {
        errorListeners.add(listener);
    }
    
    public void removeErrorListener(Consumer<String> listener) {
        errorListeners.remove(listener);
    }
    
    private void notifyError(String error) {
        for (Consumer<String> listener : errorListeners) {
            Platform.runLater(() -> listener.accept(error));
        }
    }
    
    public String getUserId() {
        return userId.get();
    }
    
    public StringProperty userIdProperty() {
        return userId;
    }
    
    public String getCurrentDocumentId() {
        return currentDocumentId.get();
    }
    
    public StringProperty currentDocumentIdProperty() {
        return currentDocumentId;
    }
    
    public boolean isConnected() {
        return connected;
    }
    
    public BooleanProperty connectedProperty() {
        return connectedProperty;
    }
    
    public Document getCurrentDocument() {
        return currentDocument.get();
    }
    
    public ObjectProperty<Document> currentDocumentProperty() {
        return currentDocument;
    }
    
    public List<String> getDocumentList() {
        return documentList.get();
    }
    
    public ListProperty<String> documentListProperty() {
        return documentList;
    }
    
    public List<String> getActiveUsers() {
        return activeUsers.get();
    }
    
    public ListProperty<String> activeUsersProperty() {
        return activeUsers;
    }
}
