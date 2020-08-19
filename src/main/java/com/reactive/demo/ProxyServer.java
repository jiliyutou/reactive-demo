package com.reactive.demo;

import io.reactivex.netty.RxNetty;
import io.reactivex.netty.protocol.http.client.HttpClient;
import io.reactivex.netty.protocol.http.server.HttpServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/***
 * 代理服务启动类
 *
 * http://localhost:9000/_proxy_1/xxxx 的请求会被代理转发至 http://目标机器/xxxx
 *
 * @author haikuo.zhk
 */
public class ProxyServer {

    private final static Logger LOG	= LoggerFactory.getLogger(ProxyServer.class);

    // 默认服务端口，可以通过-Dproxy.port=9090参数覆盖
    private final static int DEFAULT_PORT = 9000;

    private static int port;

    private static HttpServer httpServer;

    private static Map<String, HttpClient> httpClientRouters;

    public static void main(String[] args) {
        httpClientRouters = generateProxyRouters();

        // 设置服务端绑定端口
        String proxyPort = Optional.ofNullable(System.getProperty("proxy.port")).orElse(String.valueOf(DEFAULT_PORT));
        try {
            port = Integer.valueOf(proxyPort);
        } catch (NumberFormatException e) {
            port = DEFAULT_PORT;
        }
        httpServer = RxNetty.createHttpServer(port, new HttpRequestHandler(httpClientRouters));
        httpServer.startAndWait();
        LOG.info("proxy server started on {} port...", port);

        // 注册程序回调钩子
        addShutdownHook();
    }

    /***
     * 生成代理转发Client路由
     *
     * @return
     */
    private static Map<String, HttpClient> generateProxyRouters() {
        return PropertiesUtil.URI_IPHOST_MAPPING
                .entrySet().stream()
                .collect(Collectors.toMap(
                        e -> e.getKey(),
                        e -> {
                            String[] splits = e.getValue().split(":");
                            return RxNetty.createHttpClient(splits[0], Integer.valueOf(splits[1]));
                        }
                ));
    }

    /***
     * 程序回调钩子
     */
    private static void addShutdownHook() {
        Runtime.getRuntime().addShutdownHook(new Thread() {
            @Override
            public void run() {
                try {
                    httpServer.shutdown();
                    httpClientRouters.values().forEach(r -> r.shutdown());
                } catch (InterruptedException e) {
                } finally {
                    System.exit(0);
                }
            }
        });
    }
}
