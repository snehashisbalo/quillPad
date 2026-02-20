package org.quillpad.collab;

import java.io.Serializable;

public class Message implements Serializable {
    private static final long serialVersionUID = 1L;
    
    public enum Type {
        CONNECT,
        DISCONNECT,
        JOIN_DOCUMENT,
        LEAVE_DOCUMENT,
        DOCUMENT_UPDATE,
        DOCUMENT_SYNC,
        DOCUMENT_LIST,
        CREATE_DOCUMENT,
        DELETE_DOCUMENT,
        ERROR,
        USER_LIST,
        CURSOR_UPDATE,
        REQUEST_WRITE_ACCESS,
        RELEASE_WRITE_ACCESS,
        WRITE_ACCESS_STATUS,
        WRITE_ACCESS_REQUEST,
        GRANT_WRITE_ACCESS
    }
    
    private Type type;
    private String senderId;
    private String documentId;
    private Document document;
    private String payload;
    private long timestamp;
    
    public Message() {
        this.timestamp = System.currentTimeMillis();
    }
    
    public Message(Type type) {
        this();
        this.type = type;
    }
    
    public static Message connect(String userId, String userName) {
        Message msg = new Message(Type.CONNECT);
        msg.setSenderId(userId);
        msg.setPayload(userName);
        return msg;
    }
    
    public static Message disconnect(String userId) {
        Message msg = new Message(Type.DISCONNECT);
        msg.setSenderId(userId);
        return msg;
    }
    
    public static Message joinDocument(String userId, String documentId) {
        Message msg = new Message(Type.JOIN_DOCUMENT);
        msg.setSenderId(userId);
        msg.setDocumentId(documentId);
        return msg;
    }
    
    public static Message leaveDocument(String userId, String documentId) {
        Message msg = new Message(Type.LEAVE_DOCUMENT);
        msg.setSenderId(userId);
        msg.setDocumentId(documentId);
        return msg;
    }
    
    public static Message documentUpdate(String userId, String documentId, Document document) {
        Message msg = new Message(Type.DOCUMENT_UPDATE);
        msg.setSenderId(userId);
        msg.setDocumentId(documentId);
        msg.setDocument(document);
        return msg;
    }
    
    public static Message documentSync(Document document) {
        Message msg = new Message(Type.DOCUMENT_SYNC);
        msg.setDocument(document);
        return msg;
    }
    
    public static Message documentList(String documents) {
        Message msg = new Message(Type.DOCUMENT_LIST);
        msg.setPayload(documents);
        return msg;
    }
    
    public static Message createDocument(String userId, String documentName) {
        Message msg = new Message(Type.CREATE_DOCUMENT);
        msg.setSenderId(userId);
        msg.setPayload(documentName);
        return msg;
    }
    
    public static Message deleteDocument(String userId, String documentId) {
        Message msg = new Message(Type.DELETE_DOCUMENT);
        msg.setSenderId(userId);
        msg.setDocumentId(documentId);
        return msg;
    }
    
    public static Message error(String errorMessage) {
        Message msg = new Message(Type.ERROR);
        msg.setPayload(errorMessage);
        return msg;
    }
    
    public static Message userList(String users) {
        Message msg = new Message(Type.USER_LIST);
        msg.setPayload(users);
        return msg;
    }
    
    public static Message cursorUpdate(String userId, String documentId, int position) {
        Message msg = new Message(Type.CURSOR_UPDATE);
        msg.setSenderId(userId);
        msg.setDocumentId(documentId);
        msg.setPayload(String.valueOf(position));
        return msg;
    }

    public static Message requestWriteAccess(String userId, String documentId) {
        Message msg = new Message(Type.REQUEST_WRITE_ACCESS);
        msg.setSenderId(userId);
        msg.setDocumentId(documentId);
        return msg;
    }

    public static Message releaseWriteAccess(String userId, String documentId) {
        Message msg = new Message(Type.RELEASE_WRITE_ACCESS);
        msg.setSenderId(userId);
        msg.setDocumentId(documentId);
        return msg;
    }

    public static Message writeAccessStatus(String documentId, String writeOwnerUserId) {
        return writeAccessStatus(documentId, writeOwnerUserId, null);
    }

    public static Message writeAccessStatus(String documentId, String writeOwnerUserId, String documentOwnerUserId) {
        Message msg = new Message(Type.WRITE_ACCESS_STATUS);
        msg.setDocumentId(documentId);
        msg.setSenderId(writeOwnerUserId);
        String writer = writeOwnerUserId == null ? "" : writeOwnerUserId;
        String owner = documentOwnerUserId == null ? "" : documentOwnerUserId;
        msg.setPayload(writer + "|" + owner);
        return msg;
    }

    public static Message writeAccessRequest(String requesterUserId, String documentId) {
        Message msg = new Message(Type.WRITE_ACCESS_REQUEST);
        msg.setSenderId(requesterUserId);
        msg.setDocumentId(documentId);
        msg.setPayload(requesterUserId);
        return msg;
    }

    public static Message grantWriteAccess(String ownerUserId, String documentId, String targetUserId) {
        Message msg = new Message(Type.GRANT_WRITE_ACCESS);
        msg.setSenderId(ownerUserId);
        msg.setDocumentId(documentId);
        msg.setPayload(targetUserId);
        return msg;
    }
    
    public Type getType() { return type; }
    public void setType(Type type) { this.type = type; }
    public String getSenderId() { return senderId; }
    public void setSenderId(String senderId) { this.senderId = senderId; }
    public String getDocumentId() { return documentId; }
    public void setDocumentId(String documentId) { this.documentId = documentId; }
    public Document getDocument() { return document; }
    public void setDocument(Document document) { this.document = document; }
    public String getPayload() { return payload; }
    public void setPayload(String payload) { this.payload = payload; }
    public long getTimestamp() { return timestamp; }
    public void setTimestamp(long timestamp) { this.timestamp = timestamp; }
    
    @Override
    public String toString() {
        return "Message{type=" + type + ", senderId='" + senderId + "', documentId='" + documentId + "'}";
    }
}
