package org.quillpad.collab;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.*;

public class CollabServer {
    private static final Logger logger = Logger.getLogger(CollabServer.class.getName());
    
    static {
        try {
            FileHandler fileHandler = new FileHandler("collab_server.log", true);
            fileHandler.setFormatter(new SimpleFormatter());
            logger.addHandler(fileHandler);
            logger.setLevel(Level.ALL);
        } catch (IOException e) {
            System.err.println("Failed to create log file: " + e.getMessage());
        }
    }
    private static final int DEFAULT_PORT = 9999;
    private static final String DOCUMENTS_DIR = "collab_documents";
    
    private final int port;
    private ServerSocket serverSocket;
    private volatile boolean running;
    
    private final Map<String, ClientHandler> clients = new ConcurrentHashMap<>();
    private final Map<String, Document> documents = new ConcurrentHashMap<>();
    private final Map<String, Set<String>> documentUsers = new ConcurrentHashMap<>();
    private final Map<String, String> userDocuments = new ConcurrentHashMap<>();
    
    private final Path documentsPath;
    
    public CollabServer() {
        this(DEFAULT_PORT);
    }
    
    public CollabServer(int port) {
        this.port = port;
        this.documentsPath = Paths.get(DOCUMENTS_DIR);
        initializeDocumentStorage();
    }
    
    private void initializeDocumentStorage() {
        try {
            Files.createDirectories(documentsPath);
            loadDocumentsFromDisk();
        } catch (IOException e) {
            System.err.println("Failed to create documents directory: " + e.getMessage());
        }
    }
    
    private void loadDocumentsFromDisk() {
        try {
            Files.walk(documentsPath)
                .filter(p -> p.toString().endsWith(".doc"))
                .forEach(this::loadDocument);
        } catch (IOException e) {
            System.err.println("Error loading documents: " + e.getMessage());
        }
    }
    
    private void loadDocument(Path path) {
        try (ObjectInputStream ois = new ObjectInputStream(new FileInputStream(path.toFile()))) {
            Document doc = (Document) ois.readObject();
            documents.put(doc.getDocumentId(), doc);
            documentUsers.put(doc.getDocumentId(), ConcurrentHashMap.newKeySet());
            System.out.println("Loaded document: " + doc.getDocumentName());
        } catch (Exception e) {
            System.err.println("Failed to load document " + path + ": " + e.getMessage());
        }
    }
    
    private void saveDocumentToDisk(Document doc) {
        Path filePath = documentsPath.resolve(doc.getDocumentId() + ".doc");
        try (ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(filePath.toFile()))) {
            oos.writeObject(doc);
        } catch (IOException e) {
            System.err.println("Failed to save document " + doc.getDocumentId() + ": " + e.getMessage());
        }
    }
    
    private void deleteDocumentFromDisk(String documentId) {
        Path filePath = documentsPath.resolve(documentId + ".doc");
        try {
            Files.deleteIfExists(filePath);
        } catch (IOException e) {
            System.err.println("Failed to delete document file: " + e.getMessage());
        }
    }
    
    public void start() {
        running = true;
        try {
            serverSocket = new ServerSocket(port);
            System.out.println("CollabServer started on port " + port);
            
            while (running) {
                try {
                    Socket clientSocket = serverSocket.accept();
                    System.out.println("New client connection from: " + clientSocket.getInetAddress());
                    
                    ClientHandler handler = new ClientHandler(clientSocket, this);
                    Thread thread = new Thread(handler);
                    thread.setDaemon(true);
                    thread.start();
                } catch (IOException e) {
                    if (running) {
                        System.err.println("Error accepting client: " + e.getMessage());
                    }
                }
            }
        } catch (IOException e) {
            System.err.println("Server error: " + e.getMessage());
        }
    }
    
    public void stop() {
        running = false;
        try {
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
            }
        } catch (IOException e) {
            System.err.println("Error closing server: " + e.getMessage());
        }
        
        for (ClientHandler handler : clients.values()) {
            handler.disconnect();
        }
        clients.clear();
        System.out.println("Server stopped");
    }
    
    void registerClient(String userId, ClientHandler handler) {
        clients.put(userId, handler);
        System.out.println("Client registered: " + userId);
    }
    
    void unregisterClient(String userId) {
        ClientHandler handler = clients.remove(userId);
        if (handler != null) {
            String documentId = userDocuments.remove(userId);
            if (documentId != null) {
                Set<String> users = documentUsers.get(documentId);
                if (users != null) {
                    users.remove(userId);
                    broadcastUserList(documentId);
                }
            }
        }
        System.out.println("Client unregistered: " + userId);
    }
    
    void handleJoinDocument(String userId, String documentId, ClientHandler handler) {
        logger.info("User " + userId + " attempting to join document: " + documentId);
        
        Document doc = documents.get(documentId);
        if (doc == null) {
            logger.warning("Document not found: " + documentId);
            handler.sendMessage(Message.error("Document not found: " + documentId));
            return;
        }
        
        String currentDoc = userDocuments.get(userId);
        if (currentDoc != null && !currentDoc.equals(documentId)) {
            Set<String> currentUsers = documentUsers.get(currentDoc);
            if (currentUsers != null) {
                currentUsers.remove(userId);
            }
        }
        
        userDocuments.put(userId, documentId);
        documentUsers.computeIfAbsent(documentId, k -> ConcurrentHashMap.newKeySet()).add(userId);
        
        logger.info("User " + userId + " joined document " + documentId + ". Current users: " + documentUsers.get(documentId));
        
        handler.sendMessage(Message.documentSync(doc));
        broadcastUserList(documentId);
        
        System.out.println("User " + userId + " joined document " + documentId);
    }
    
    void handleLeaveDocument(String userId, String documentId) {
        Set<String> users = documentUsers.get(documentId);
        if (users != null) {
            users.remove(userId);
        }
        userDocuments.remove(userId);
        broadcastUserList(documentId);
    }
    
    void handleDocumentUpdate(String userId, String documentId, Document updatedDoc) {
        if (documentId == null || documentId.isEmpty()) {
            System.err.println("handleDocumentUpdate called with null/empty documentId from user: " + userId);
            return;
        }
        
        System.out.println("handleDocumentUpdate: user=" + userId + " docId=" + documentId);
        
        Document doc = documents.get(documentId);
        if (doc != null) {
            doc.setPlainText(updatedDoc.getPlainText());
            doc.setStyleSegments(updatedDoc.getStyleSegments());
            doc.setLastModified(System.currentTimeMillis());
            doc.setVersion(doc.getVersion() + 1);
            
            saveDocumentToDisk(doc);
            
            System.out.println("Calling broadcastDocumentUpdate...");
            broadcastDocumentUpdate(documentId, doc, userId);
        } else {
            System.err.println("Document not found: " + documentId);
        }
    }
    
    void handleCreateDocument(String userId, String documentName, ClientHandler handler) {
        String documentId = UUID.randomUUID().toString();
        Document doc = new Document(documentId, documentName, userId);
        doc.setPlainText("");
        
        documents.put(documentId, doc);
        documentUsers.put(documentId, ConcurrentHashMap.newKeySet());
        
        // Creator automatically joins the document
        userDocuments.put(userId, documentId);
        documentUsers.get(documentId).add(userId);
        
        saveDocumentToDisk(doc);
        
        handler.sendMessage(Message.documentSync(doc));
        broadcastUserList(documentId);
        broadcastDocumentList();
        
        System.out.println("Document created: " + documentName + " by " + userId);
    }
    
    void handleDeleteDocument(String userId, String documentId, ClientHandler handler) {
        Document doc = documents.get(documentId);
        if (doc != null) {
            if (!doc.getOwner().equals(userId)) {
                handler.sendMessage(Message.error("Only the owner can delete this document"));
                return;
            }
            
            Set<String> users = documentUsers.get(documentId);
            if (users != null) {
                for (String user : users) {
                    ClientHandler userHandler = clients.get(user);
                    if (userHandler != null) {
                        userHandler.sendMessage(Message.error("Document has been deleted by owner"));
                    }
                    userDocuments.remove(user);
                }
            }
            
            documents.remove(documentId);
            documentUsers.remove(documentId);
            deleteDocumentFromDisk(documentId);
            
            broadcastDocumentList();
            System.out.println("Document deleted: " + documentId);
        }
    }
    
    void handleCursorUpdate(String userId, String documentId, int position) {
        Set<String> users = documentUsers.get(documentId);
        if (users != null) {
            for (String user : users) {
                if (!user.equals(userId)) {
                    ClientHandler handler = clients.get(user);
                    if (handler != null) {
                        handler.sendMessage(Message.cursorUpdate(userId, documentId, position));
                    }
                }
            }
        }
    }
    
    private void broadcastDocumentUpdate(String documentId, Document doc, String excludeUserId) {
        Set<String> users = documentUsers.get(documentId);
        logger.info("Broadcasting update for doc: " + documentId + " to users: " + users + " (excluding: " + excludeUserId + ")");
        System.out.println("Broadcasting update for doc: " + documentId + " to users: " + users + " (excluding: " + excludeUserId + ")");
        
        if (users == null || users.isEmpty()) {
            logger.warning("No users found for document: " + documentId);
            return;
        }
        
        Message updateMsg = Message.documentUpdate(null, documentId, doc);
        
        for (String user : users) {
            if (!user.equals(excludeUserId)) {
                ClientHandler handler = clients.get(user);
                if (handler != null) {
                    logger.info("Sending update to user: " + user);
                    System.out.println("Sending update to user: " + user);
                    handler.sendMessage(updateMsg);
                } else {
                    logger.warning("No handler found for user: " + user);
                }
            }
        }
    }
    
    private void broadcastUserList(String documentId) {
        Set<String> users = documentUsers.get(documentId);
        if (users != null) {
            String userList = String.join(",", users);
            Message msg = Message.userList(userList);
            for (String user : users) {
                ClientHandler handler = clients.get(user);
                if (handler != null) {
                    handler.sendMessage(msg);
                }
            }
        }
    }
    
    private void broadcastDocumentList() {
        StringBuilder sb = new StringBuilder();
        for (Document doc : documents.values()) {
            if (sb.length() > 0) sb.append(";");
            sb.append(doc.getDocumentId()).append(":").append(doc.getDocumentName());
        }
        Message msg = Message.documentList(sb.toString());
        for (ClientHandler handler : clients.values()) {
            handler.sendMessage(msg);
        }
    }
    
    void sendDocumentList(ClientHandler handler) {
        StringBuilder sb = new StringBuilder();
        for (Document doc : documents.values()) {
            if (sb.length() > 0) sb.append(";");
            sb.append(doc.getDocumentId()).append(":").append(doc.getDocumentName());
        }
        handler.sendMessage(Message.documentList(sb.toString()));
    }
    
    Collection<Document> getAllDocuments() {
        return documents.values();
    }
    
    public static void main(String[] args) {
        int port = DEFAULT_PORT;
        if (args.length > 0) {
            try {
                port = Integer.parseInt(args[0]);
            } catch (NumberFormatException e) {
                System.err.println("Invalid port number, using default: " + DEFAULT_PORT);
            }
        }
        
        CollabServer server = new CollabServer(port);
        
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("\nShutting down server...");
            server.stop();
        }));
        
        server.start();
    }
}
