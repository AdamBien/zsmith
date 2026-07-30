package airhacks.zsmith.agentcore.boundary;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import com.sun.net.httpserver.HttpServer;

import airhacks.zsmith.agentcore.control.InvocationsHandler;
import airhacks.zsmith.agentcore.control.PingHandler;
import airhacks.zsmith.agentcore.control.RuntimeSessions;
import airhacks.zsmith.http.boundary.ChatEngine;
import airhacks.zsmith.logging.control.Log;

public class AgentCoreServer {

    static String HOST = "0.0.0.0";
    static int BACKLOG = 0;

    HttpServer server;
    ExecutorService executor;

    AgentCoreServer(HttpServer server, ExecutorService executor) {
        this.server = server;
        this.executor = executor;
    }

    public static AgentCoreServer start(ChatEngine engine, int port) {
        try {
            var server = HttpServer.create(new InetSocketAddress(HOST, port), BACKLOG);
            var sessions = new RuntimeSessions();
            server.createContext("/invocations", new InvocationsHandler(engine, sessions));
            server.createContext("/ping", new PingHandler());
            var executor = Executors.newCachedThreadPool();
            server.setExecutor(executor);
            server.start();
            Log.agent("AgentCore server listening on " + HOST + ":" + server.getAddress().getPort());
            return new AgentCoreServer(server, executor);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to start AgentCore server on port " + port, e);
        }
    }

    public int port() {
        return this.server.getAddress().getPort();
    }

    public void stop() {
        this.server.stop(0);
        this.executor.shutdownNow();
    }
}
