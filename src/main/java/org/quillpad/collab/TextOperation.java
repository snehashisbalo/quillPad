package org.quillpad.collab;

import java.io.Serializable;

public class TextOperation implements Serializable {
    private static final long serialVersionUID = 1L;
    
    public enum Type {
        INSERT,
        DELETE,
        RETAIN
    }
    
    private Type type;
    private int position;
    private String text;
    private int length;
    private long timestamp;
    private String userId;
    private int revision;
    
    public TextOperation() {
        this.timestamp = System.currentTimeMillis();
    }
    
    public static TextOperation insert(int position, String text, String userId, int revision) {
        TextOperation op = new TextOperation();
        op.type = Type.INSERT;
        op.position = position;
        op.text = text;
        op.length = text.length();
        op.userId = userId;
        op.revision = revision;
        return op;
    }
    
    public static TextOperation delete(int position, int length, String userId, int revision) {
        TextOperation op = new TextOperation();
        op.type = Type.DELETE;
        op.position = position;
        op.length = length;
        op.userId = userId;
        op.revision = revision;
        return op;
    }
    
    public static TextOperation retain(int position, int length, String userId, int revision) {
        TextOperation op = new TextOperation();
        op.type = Type.RETAIN;
        op.position = position;
        op.length = length;
        op.userId = userId;
        op.revision = revision;
        return op;
    }
    
    public Type getType() { return type; }
    public void setType(Type type) { this.type = type; }
    
    public int getPosition() { return position; }
    public void setPosition(int position) { this.position = position; }
    
    public String getText() { return text; }
    public void setText(String text) { this.text = text; }
    
    public int getLength() { return length; }
    public void setLength(int length) { this.length = length; }
    
    public long getTimestamp() { return timestamp; }
    public void setTimestamp(long timestamp) { this.timestamp = timestamp; }
    
    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }
    
    public int getRevision() { return revision; }
    public void setRevision(int revision) { this.revision = revision; }
    
    public String apply(String content) {
        if (content == null) content = "";
        
        switch (type) {
            case INSERT:
                if (position < 0) position = 0;
                if (position > content.length()) position = content.length();
                return content.substring(0, position) + text + content.substring(position);
                
            case DELETE:
                if (position < 0) position = 0;
                if (position >= content.length()) return content;
                int end = Math.min(position + length, content.length());
                return content.substring(0, position) + content.substring(end);
                
            case RETAIN:
            default:
                return content;
        }
    }
    
    public TextOperation transform(TextOperation other) {
        if (other == null) return this;
        
        switch (type) {
            case INSERT:
                if (other.type == Type.INSERT && other.position <= this.position) {
                    return TextOperation.insert(this.position + other.length, this.text, this.userId, this.revision);
                } else if (other.type == Type.DELETE) {
                    if (other.position + other.length <= this.position) {
                        return TextOperation.insert(this.position - other.length, this.text, this.userId, this.revision);
                    } else if (other.position <= this.position) {
                        return TextOperation.insert(other.position, this.text, this.userId, this.revision);
                    }
                }
                break;
                
            case DELETE:
                if (other.type == Type.INSERT) {
                    if (other.position <= this.position) {
                        return TextOperation.delete(this.position + other.length, this.length, this.userId, this.revision);
                    }
                } else if (other.type == Type.DELETE) {
                    if (other.position + other.length <= this.position) {
                        return TextOperation.delete(this.position - other.length, this.length, this.userId, this.revision);
                    } else if (other.position < this.position) {
                        int overlapStart = Math.max(this.position, other.position);
                        int overlapEnd = Math.min(this.position + this.length, other.position + other.length);
                        int overlap = overlapEnd - overlapStart;
                        if (overlap > 0) {
                            return TextOperation.delete(other.position, this.length - overlap, this.userId, this.revision);
                        }
                    }
                }
                break;
                
            default:
                break;
        }
        
        return this;
    }
    
    @Override
    public String toString() {
        return String.format("TextOperation{type=%s, pos=%d, text='%s', len=%d, user=%s, rev=%d}",
                type, position, text != null ? text : "", length, userId, revision);
    }
}
