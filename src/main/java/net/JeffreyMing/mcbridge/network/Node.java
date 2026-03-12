package net.JeffreyMing.mcbridge.network;

public record Node(String name, int uploadMbps, int downloadMbps, String host, int port, String token, String backupHost) {
    // Constructor with no token for convenience
    public Node(String name, int uploadMbps, int downloadMbps, String host, int port) {
        this(name, uploadMbps, downloadMbps, host, port, "", null);
    }
    
    // Constructor with token and backup host
    public Node(String name, int uploadMbps, int downloadMbps, String host, int port, String token) {
        this(name, uploadMbps, downloadMbps, host, port, token, null);
    }
}
