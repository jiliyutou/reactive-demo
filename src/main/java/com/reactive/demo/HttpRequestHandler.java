package com.reactive.demo;

import io.netty.buffer.ByteBuf;
import io.reactivex.netty.protocol.http.client.HttpClient;
import io.reactivex.netty.protocol.http.client.HttpClientRequest;
import io.reactivex.netty.protocol.http.client.HttpClientResponse;
import io.reactivex.netty.protocol.http.server.HttpServerRequest;
import io.reactivex.netty.protocol.http.server.HttpServerResponse;
import io.reactivex.netty.protocol.http.server.RequestHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import rx.Observable;
import rx.Observer;
import rx.Subscriber;
import rx.functions.Action1;

import java.util.Map;
import java.util.concurrent.TimeUnit;

/***
 * HTTP请求处理器
 *
 * @author haikuo.zhk
 */
public class HttpRequestHandler implements RequestHandler<ByteBuf, ByteBuf> {

    private final static Logger LOG = LoggerFactory.getLogger(HttpRequestHandler.class);

    private static final String CONFIG_URI = "/_ops/config";

    // httpClient路由
    private Map<String, HttpClient> httpClientRouters;

    public HttpRequestHandler(final Map<String, HttpClient> httpClientRouters) {
        this.httpClientRouters = httpClientRouters;
    }

    @Override
    public Observable<Void> handle(HttpServerRequest<ByteBuf> httpServerRequest, HttpServerResponse<ByteBuf> httpServerResponse) {
        // TODO：热更新操作，返回一个空Observable
        if (CONFIG_URI.equals(httpServerRequest.getUri())) {
            return Observable.empty();
        }

        // 返回一个Observable
        return Observable.create(new Observable.OnSubscribe<Void>() {
            @Override
            public void call(Subscriber<? super Void> subscriber) {
                httpServerRequest.getContent().subscribe(new Observer<ByteBuf>() {
                    @Override
                    public void onCompleted() {
                    }

                    @Override
                    public void onError(Throwable throwable) {
                        LOG.error("subscriber error", throwable);
                    }

                    @Override
                    public void onNext(ByteBuf byteBuf) {
                        // declare response observer
                        Observer<ByteBuf> reqObserver = new Observer<ByteBuf>() {
                            @Override
                            public void onCompleted() {
                            }

                            @Override
                            public void onError(Throwable throwable) {
                                LOG.error("reqObserver error", throwable);
                            }

                            @Override
                            public void onNext(ByteBuf byteBuf) {
                                httpServerResponse.writeBytes(byteBuf);
                                httpServerResponse.close();
                            }
                        };

                        // 路由找到相应HttpClient
                        String uri = null;
                        HttpClient<ByteBuf, ByteBuf> httpClient = null;
                        for(String key : httpClientRouters.keySet()) {
                            if(httpServerRequest.getUri().startsWith(key)) {
                                uri = httpServerRequest.getUri().substring(key.length());
                                httpClient = httpClientRouters.get(key);
                            }
                        }

                        // 构造代理请求
                        final HttpClientRequest<ByteBuf> proxyRequest = HttpClientRequest.create(
                                httpServerRequest.getHttpVersion(), httpServerRequest.getHttpMethod(), uri);
                        // 填充Header信息
                        for (final Map.Entry<String, String> entry : httpServerRequest.getHeaders().entries()) {
                            proxyRequest.withHeader(entry.getKey(), entry.getValue());
                        }
                        // 填充Body信息
                        if(byteBuf != null && byteBuf.readableBytes() > 0) {
                            // increase ref count to avoid releasing by netty handler
                            byteBuf.retain();
                            proxyRequest.withContent(byteBuf);
                        }

                        // 触发API调用
                        invokeApi(httpClient, proxyRequest, reqObserver);
                    }
                });
            }
        });
    }

    /***
     * invoke API调用
     *
     * @param httpClient
     * @param proxyRequest
     * @param reqObserver
     */
    private void invokeApi(final HttpClient<ByteBuf, ByteBuf> httpClient,
                           final HttpClientRequest<ByteBuf> proxyRequest,
                           final Observer<ByteBuf> reqObserver) {
        // 转发请求并订阅观察者处理
        httpClient.submit(proxyRequest)
                .timeout(60000, TimeUnit.MILLISECONDS)
                .flatMap(HttpClientResponse::getContent)
                .doOnNext(new Action1<ByteBuf>() {
                    @Override
                    public void call(ByteBuf byteBuf) {
                        // increase ref count to avoid releasing by netty handler
                        byteBuf.retain();
                    }
                })
                .subscribe(reqObserver);
    }
}
