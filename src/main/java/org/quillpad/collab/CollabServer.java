package org.quillpad.collab;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class CollabServer {
    private static final int DEFAULT_PORT = 9999;
    private static final String DOCUMENTS_DIR = "collab_documents";
    
    private final int port;
    private ServerSocket serverSocket;
    private volatile boolean running;
    
    private final Map<String, ClientHandler> clients = new ConcurrentHashMap<>();
    private final Map<String, Document> documents = new ConcurrentHashMap<>();
    private final Map<String, Set<String>> documentUsers = new ConcurrentHashMap<>();
    private final Map<String, String> userDocuments = new ConcurrentHashMap<>();
    private final Map<String, String> documentWriteOwner = new ConcurrentHashMap<>();
    
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
            if (doc.getOwner() != null && !doc.getOwner().isBlank()) {
                documentWriteOwner.put(doc.getDocumentId(), doc.getOwner());
            }
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
    
    String registerClient(String requestedUserId, ClientHandler handler) {
        String baseId = (requestedUserId == null || requestedUserId.isBlank())
                ? "user"
                : requestedUserId.trim();
        String assignedId = baseId;
        int suffix = 2;

        while (clients.putIfAbsent(assignedId, handler) != null) {
            assignedId = baseId + "#" + suffix++;
        }

        System.out.println("Client registered: requested=" + baseId + ", assigned=" + assignedId);
        return assignedId;
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
                releaseWriteAccessIfOwner(userId, documentId);
            }
        }
        System.out.println("Client unregistered: " + userId);
    }
    
    void handleJoinDocument(String userId, String documentId, ClientHandler handler) {
        if (documentId != null) {
            documentId = documentId.trim();
        }
        Document doc = documents.get(documentId);
        if (doc == null) {
            handler.sendMessage(Message.error("Document not found: " + documentId));
            return;
        }
        
        String currentDoc = userDocuments.get(userId);
        if (currentDoc != null) {
            Set<String> currentUsers = documentUsers.get(currentDoc);
            if (currentUsers != null) {
                currentUsers.remove(userId);
            }
        }
        
        userDocuments.put(userId, documentId);
        documentUsers.computeIfAbsent(documentId, k -> ConcurrentHashMap.newKeySet()).add(userId);
        ensureWriteOwnerForJoin(documentId, doc, userId);
        
        handler.sendMessage(Message.documentSync(doc));
        sendWriteAccessStatus(handler, documentId);
        broadcastWriteAccessStatus(documentId);
        broadcastUserList(documentId);
        
        System.out.println("User " + userId + " joined document " + documentId);
    }

    private void ensureWriteOwnerForJoin(String documentId, Document doc, String joiningUserId) {
        if (documentId == null || documentId.isBlank() || doc == null || joiningUserId == null || joiningUserId.isBlank()) {
            return;
        }

        boolean changed = false;

        if (doc.getOwner() == null || doc.getOwner().isBlank()) {
            doc.setOwner(joiningUserId);
            changed = true;
        }

        String writeOwner = documentWriteOwner.get(documentId);
        if (writeOwner == null || writeOwner.isBlank() || !clients.containsKey(writeOwner)) {
            documentWriteOwner.put(documentId, joiningUserId);
        }

        if (changed) {
            saveDocumentToDisk(doc);
        }
    }
    
    void handleLeaveDocument(String userId, String documentId) {
        Set<String> users = documentUsers.get(documentId);
        if (users != null) {
            users.remove(userId);
        }
        userDocuments.remove(userId);
        releaseWriteAccessIfOwner(userId, documentId);
        broadcastUserList(documentId);
    }
    
    void handleDocumentUpdate(String userId, String documentId, Document updatedDoc, ClientHandler handler) {
        if (documentId == null || documentId.isEmpty()) {
            System.err.println("handleDocumentUpdate called with null/empty documentId from user: " + userId);
            return;
        }
        
        System.out.println("handleDocumentUpdate: user=" + userId + " docId=" + documentId);
        
        Document doc = documents.get(documentId);
        if (doc != null) {
            Set<String> users = documentUsers.computeIfAbsent(documentId, k -> ConcurrentHashMap.newKeySet());
            if (!users.contains(userId)) {
                String currentDoc = userDocuments.get(userId);
                if (currentDoc != null && !currentDoc.equals(documentId)) {
                    Set<String> currentUsers = documentUsers.get(currentDoc);
                    if (currentUsers != null) {
                        currentUsers.remove(userId);
                        broadcastUserList(currentDoc);
                    }
                }
                userDocuments.put(userId, documentId);
                users.add(userId);
            }

            documentWriteOwner.put(documentId, userId);
            doc.setPlainText(updatedDoc.getPlainText());
            doc.setStyleSegments(updatedDoc.getStyleSegments());
            doc.setLastModified(System.currentTimeMillis());
            doc.setVersion(doc.getVersion() + 1);
            
            saveDocumentToDisk(doc);
            handler.sendMessage(Message.documentSync(doc));
            broadcastWriteAccessStatus(documentId);
            broadcastUserList(documentId);
            
            System.out.println("Calling broadcastDocumentUpdate...");
            broadcastDocumentUpdate(documentId, doc, userId);
        } else {
            System.err.println("Document not found: " + documentId);
        }
    }
    
    void handleCreateDocument(String userId, String documentName, ClientHandler handler) {
        if (userId == null || userId.isBlank()) {
            handler.sendMessage(Message.error("Invalid user session. Reconnect and try again."));
            return;
        }
        if (documentName == null || documentName.isBlank()) {
            handler.sendMessage(Message.error("Document name cannot be empty"));
            return;
        }
        documentName = documentName.trim();

        String documentId = UUID.randomUUID().toString();
        Document doc = new Document(documentId, documentName, userId);
        doc.setPlainText("");
        
        documents.put(documentId, doc);
        documentUsers.put(documentId, ConcurrentHashMap.newKeySet());
        
        // Creator automatically joins the document
        userDocuments.put(userId, documentId);
        documentUsers.get(documentId).add(userId);
        documentWriteOwner.put(documentId, userId);
        
        saveDocumentToDisk(doc);
        
        handler.sendMessage(Message.documentSync(doc));
        sendWriteAccessStatus(handler, documentId);
        broadcastWriteAccessStatus(documentId);
        broadcastUserList(documentId);
        broadcastDocumentList();
        
        System.out.println("Document created: " + documentName + " by " + userId);
    }
    
    void handleDeleteDocument(String userId, String documentId, ClientHandler handler) {
        Document doc = documents.get(documentId);
        if (doc != null) {
            Set<String> users = documentUsers.get(documentId);
            if (users != null) {
                for (String user : users) {
                    ClientHandler userHandler = clients.get(user);
                    if (userHandler != null) {
                        userHandler.sendMessage(Message.error("Document has been deleted"));
                    }
                    userDocuments.remove(user);
                }
            }
            
            documents.remove(documentId);
            documentUsers.remove(documentId);
            documentWriteOwner.remove(documentId);
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

    void handleRequestWriteAccess(String userId, String documentId, ClientHandler handler) {
        if (documentId == null || documentId.isBlank()) {
            handler.sendMessage(Message.error("Invalid document for write access request"));
            return;
        }
        documentId = documentId.trim();

        Document doc = documents.get(documentId);
        if (doc == null) {
            String fallbackDocId = userDocuments.get(userId);
            if (fallbackDocId != null) {
                Document fallbackDoc = documents.get(fallbackDocId);
                if (fallbackDoc != null) {
                    documentId = fallbackDocId;
                    doc = fallbackDoc;
                }
            }
        }
        if (doc == null) {
            handler.sendMessage(Message.error("Document not found: " + documentId));
            return;
        }

        // Auto-join if needed so write requests are resilient to client-side state drift.
        Set<String> users = documentUsers.computeIfAbsent(documentId, k -> ConcurrentHashMap.newKeySet());
        if (!users.contains(userId)) {
            String currentDoc = userDocuments.get(userId);
            if (currentDoc != null && !currentDoc.equals(documentId)) {
                Set<String> currentUsers = documentUsers.get(currentDoc);
                if (currentUsers != null) {
                    currentUsers.remove(userId);
                    releaseWriteAccessIfOwner(userId, currentDoc);
                    broadcastUserList(currentDoc);
                }
            }
            userDocuments.put(userId, documentId);
            users.add(userId);
            handler.sendMessage(Message.documentSync(doc));
        }

        String docOwner = doc.getOwner();
        if (docOwner == null || docOwner.isBlank()) {
            docOwner = userId;
            doc.setOwner(userId);
            saveDocumentToDisk(doc);
        }

        // Owner can reclaim write access immediately.
        if (docOwner.equals(userId)) {
            documentWriteOwner.put(documentId, userId);
            broadcastWriteAccessStatus(documentId);
            return;
        }

        ClientHandler ownerHandler = clients.get(docOwner);
        if (ownerHandler == null) {
            documentWriteOwner.put(documentId, userId);
            broadcastWriteAccessStatus(documentId);
            handler.sendMessage(Message.error("Document owner is offline. You were granted temporary write access."));
            return;
        }

        ownerHandler.sendMessage(Message.writeAccessRequest(userId, documentId));
        handler.sendMessage(Message.error("Write access request sent to owner: " + docOwner));
        sendWriteAccessStatus(handler, documentId);
    }

    void handleGrantWriteAccess(String ownerUserId, String documentId, String targetUserId, ClientHandler handler) {
        if (documentId == null || documentId.isBlank() || targetUserId == null || targetUserId.isBlank()) {
            handler.sendMessage(Message.error("Invalid write access grant request"));
            return;
        }
        documentId = documentId.trim();
        targetUserId = targetUserId.trim();

        Document doc = documents.get(documentId);
        if (doc == null) {
            String fallbackDocId = userDocuments.get(ownerUserId);
            if (fallbackDocId != null) {
                Document fallbackDoc = documents.get(fallbackDocId);
                if (fallbackDoc != null) {
                    documentId = fallbackDocId;
                    doc = fallbackDoc;
                }
            }
        }
        if (doc == null) {
            handler.sendMessage(Message.error("Document not found: " + documentId));
            return;
        }

        if (!ownerUserId.equals(doc.getOwner())) {
            handler.sendMessage(Message.error("Only the document owner can grant write access"));
            return;
        }

        Set<String> users = documentUsers.get(documentId);
        if (users == null || !users.contains(targetUserId)) {
            handler.sendMessage(Message.error("Requested user is not currently in this document"));
            return;
        }

        documentWriteOwner.put(documentId, targetUserId);
        broadcastWriteAccessStatus(documentId);
    }

    void handleReleaseWriteAccess(String userId, String documentId, ClientHandler handler) {
        if (documentId == null || documentId.isBlank()) {
            return;
        }
        documentId = documentId.trim();
        String owner = documentWriteOwner.get(documentId);
        if (owner == null) {
            sendWriteAccessStatus(handler, documentId);
            return;
        }
        if (!owner.equals(userId)) {
            handler.sendMessage(Message.error("Only the current writer can release write access"));
            sendWriteAccessStatus(handler, documentId);
            return;
        }

        Document doc = documents.get(documentId);
        if (doc != null && doc.getOwner() != null && !doc.getOwner().isBlank()) {
            documentWriteOwner.put(documentId, doc.getOwner());
        } else {
            documentWriteOwner.remove(documentId);
        }
        broadcastWriteAccessStatus(documentId);
    }
    
    private void broadcastDocumentUpdate(String documentId, Document doc, String excludeUserId) {
        System.out.println("Broadcasting update for doc: " + documentId + " (excluding: " + excludeUserId + ")");
        Message updateMsg = Message.documentUpdate(excludeUserId, documentId, doc);
        for (Map.Entry<String, ClientHandler> entry : clients.entrySet()) {
            String user = entry.getKey();
            if (excludeUserId != null && excludeUserId.equals(user)) {
                continue;
            }
            ClientHandler handler = entry.getValue();
            if (handler != null) {
                handler.sendMessage(updateMsg);
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

    private void releaseWriteAccessIfOwner(String userId, String documentId) {
        if (documentId == null || documentId.isBlank()) {
            return;
        }
        String owner = documentWriteOwner.get(documentId);
        if (userId != null && userId.equals(owner)) {
            documentWriteOwner.remove(documentId);
            broadcastWriteAccessStatus(documentId);
        }
    }

    private void broadcastWriteAccessStatus(String documentId) {
        Set<String> users = documentUsers.get(documentId);
        String owner = documentWriteOwner.get(documentId);
        Document doc = documents.get(documentId);
        String docOwner = doc != null ? doc.getOwner() : null;
        Message msg = Message.writeAccessStatus(documentId, owner, docOwner);
        if (users != null) {
            for (String user : users) {
                ClientHandler handler = clients.get(user);
                if (handler != null) {
                    handler.sendMessage(msg);
                }
            }
        }
    }

    private void sendWriteAccessStatus(ClientHandler handler, String documentId) {
        if (handler == null || documentId == null || documentId.isBlank()) {
            return;
        }
        String owner = documentWriteOwner.get(documentId);
        Document doc = documents.get(documentId);
        String docOwner = doc != null ? doc.getOwner() : null;
        handler.sendMessage(Message.writeAccessStatus(documentId, owner, docOwner));
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
