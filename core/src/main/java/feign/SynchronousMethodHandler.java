/**
 * Copyright 2012-2019 The Feign Authors
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package feign;

import feign.InvocationHandlerFactory.MethodHandler;
import feign.Request.Options;
import feign.codec.DecodeException;
import feign.codec.Decoder;
import feign.codec.ErrorDecoder;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static feign.ExceptionPropagationPolicy.UNWRAP;
import static feign.FeignException.errorExecuting;
import static feign.FeignException.errorReading;
import static feign.Util.checkNotNull;
import static feign.Util.ensureClosed;

/**
 * 同步方法处理器
 */
final class SynchronousMethodHandler implements MethodHandler {

    private static final long MAX_RESPONSE_BUFFER_SIZE = 8192L;

    private final MethodMetadata metadata;
    private final Target<?> target;
    private final Client client;
    private final Retryer retryer;
    private final List<RequestInterceptor> requestInterceptors;
    private final Logger logger;
    private final Logger.Level logLevel;
    private final RequestTemplate.Factory buildTemplateFromArgs;
    private final Options options;
    private final Decoder decoder;
    private final ErrorDecoder errorDecoder;
    private final boolean decode404;
    private final boolean closeAfterDecode;
    private final ExceptionPropagationPolicy propagationPolicy;

    private SynchronousMethodHandler(Target<?> target,
                                     Client client,
                                     Retryer retryer,
                                     List<RequestInterceptor> requestInterceptors,
                                     Logger logger,
                                     Logger.Level logLevel,
                                     MethodMetadata metadata,
                                     RequestTemplate.Factory buildTemplateFromArgs,
                                     Options options,
                                     Decoder decoder,
                                     ErrorDecoder errorDecoder,
                                     boolean decode404,
                                     boolean closeAfterDecode,
                                     ExceptionPropagationPolicy propagationPolicy) {
        this.target = checkNotNull(target, "target");
        this.client = checkNotNull(client, "client for %s", target);
        this.retryer = checkNotNull(retryer, "retryer for %s", target);
        this.requestInterceptors =
                checkNotNull(requestInterceptors, "requestInterceptors for %s", target);
        this.logger = checkNotNull(logger, "logger for %s", target);
        this.logLevel = checkNotNull(logLevel, "logLevel for %s", target);
        this.metadata = checkNotNull(metadata, "metadata for %s", target);
        this.buildTemplateFromArgs = checkNotNull(buildTemplateFromArgs, "metadata for %s", target);
        this.options = checkNotNull(options, "options for %s", target);
        this.errorDecoder = checkNotNull(errorDecoder, "errorDecoder for %s", target);
        this.decoder = checkNotNull(decoder, "decoder for %s", target);
        this.decode404 = decode404;
        this.closeAfterDecode = closeAfterDecode;
        this.propagationPolicy = propagationPolicy;
    }

    //实际执行的入口
    @Override
    public Object invoke(Object[] argv) throws Throwable {
        //根据参数生成请求的模板
        RequestTemplate template = buildTemplateFromArgs.create(argv);
        Retryer retryer = this.retryer.clone();
        while (true) {
            try {
                return executeAndDecode(template);
            } catch (RetryableException e) {
                try {
                    //交由错误处理器来处理
                    retryer.continueOrPropagate(e);
                } catch (RetryableException th) {
                    Throwable cause = th.getCause();
                    //如果不在包裹 就直接抛出异常
                    if (propagationPolicy == UNWRAP && cause != null) {
                        throw cause;
                    } else {
                        throw th;
                    }
                }
                if (logLevel != Logger.Level.NONE) {
                    logger.logRetry(metadata.configKey(), logLevel);
                }
                continue;
            }
        }
    }

    /**
     * 直接执行入口方法
     *
     * @param template
     * @return
     * @throws Throwable
     */
    Object executeAndDecode(RequestTemplate template) throws Throwable {
        //走拦截器,生成最终的请求
        Request request = targetRequest(template);

        if (logLevel != Logger.Level.NONE) {
            //打印请求
            logger.logRequest(metadata.configKey(), logLevel, request);
        }

        Response response;
        long start = System.nanoTime();
        try {
            //交由具体的 http客户端去执行
            response = client.execute(request, options);
        } catch (IOException e) {
            if (logLevel != Logger.Level.NONE) {
                logger.logIOException(metadata.configKey(), logLevel, e, elapsedTime(start));
            }
            throw errorExecuting(request, e);
        }
        long elapsedTime = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);

        boolean shouldClose = true;
        try {
            if (logLevel != Logger.Level.NONE) {
                response =
                        logger.logAndRebufferResponse(metadata.configKey(), logLevel, response, elapsedTime);
            }
            if (Response.class == metadata.returnType()) {
                if (response.body() == null) {
                    return response;
                }
                if (response.body().length() == null ||
                        response.body().length() > MAX_RESPONSE_BUFFER_SIZE) {
                    shouldClose = false;
                    return response;
                }
                // Ensure the response body is disconnected
                byte[] bodyData = Util.toByteArray(response.body().asInputStream());
                return response.toBuilder().body(bodyData).build();
            }
            if (response.status() >= 200 && response.status() < 300) {
                if (void.class == metadata.returnType()) {
                    return null;
                } else {
                    Object result = decode(response);
                    shouldClose = closeAfterDecode;
                    return result;
                }
            } else if (decode404 && response.status() == 404 && void.class != metadata.returnType()) {
                Object result = decode(response);
                shouldClose = closeAfterDecode;
                return result;
            } else {
                throw errorDecoder.decode(metadata.configKey(), response);
            }
        } catch (IOException e) {
            if (logLevel != Logger.Level.NONE) {
                logger.logIOException(metadata.configKey(), logLevel, e, elapsedTime);
            }
            throw errorReading(request, response, e);
        } finally {
            if (shouldClose) {
                ensureClosed(response.body());
            }
        }
    }

    /**
     * 时间转化
     *
     * @param start
     * @return
     */
    long elapsedTime(long start) {
        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
    }


    /**
     * 调用拦截器添加额外请求信息
     *
     * @param template
     * @return
     */
    Request targetRequest(RequestTemplate template) {
        //适用到本方法处理的拦截器
        for (RequestInterceptor interceptor : requestInterceptors) {
            interceptor.apply(template);
        }
        return target.apply(template);
    }

    //解码response
    Object decode(Response response) throws Throwable {
        try {
            //利用解析器去解析结果，返回用户期望的结果
            return decoder.decode(response, metadata.returnType());
        } catch (FeignException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new DecodeException(response.status(), e.getMessage(), e);
        }
    }

    /**
     * 通过工厂方式来创建每个方法的处理器
     */
    static class Factory {

        private final Client client;
        private final Retryer retryer;
        private final List<RequestInterceptor> requestInterceptors;
        private final Logger logger;
        private final Logger.Level logLevel;
        private final boolean decode404;
        private final boolean closeAfterDecode;
        private final ExceptionPropagationPolicy propagationPolicy;

        Factory(Client client,
                Retryer retryer,
                List<RequestInterceptor> requestInterceptors,
                Logger logger,
                Logger.Level logLevel,
                boolean decode404,
                boolean closeAfterDecode,
                ExceptionPropagationPolicy propagationPolicy) {
            this.client = checkNotNull(client, "client");
            this.retryer = checkNotNull(retryer, "retryer");
            this.requestInterceptors = checkNotNull(requestInterceptors, "requestInterceptors");
            this.logger = checkNotNull(logger, "logger");
            this.logLevel = checkNotNull(logLevel, "logLevel");
            this.decode404 = decode404;
            this.closeAfterDecode = closeAfterDecode;
            this.propagationPolicy = propagationPolicy;
        }

        public MethodHandler create(Target<?> target,
                                    MethodMetadata md,
                                    RequestTemplate.Factory buildTemplateFromArgs,
                                    Options options,
                                    Decoder decoder,
                                    ErrorDecoder errorDecoder) {
            //除了公共的配置  还有一些针对每个方法的单独配置
            return new SynchronousMethodHandler(target, client, retryer, requestInterceptors, logger,
                    logLevel, md, buildTemplateFromArgs, options, decoder,
                    errorDecoder, decode404, closeAfterDecode, propagationPolicy);
        }
    }
}
