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

import feign.Logger.NoOpLogger;
import feign.ReflectiveFeign.ParseHandlersByName;
import feign.Request.Options;
import feign.Target.HardCodedTarget;
import feign.codec.Decoder;
import feign.codec.Encoder;
import feign.codec.ErrorDecoder;

import java.io.IOException;
import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

import static feign.ExceptionPropagationPolicy.NONE;

/**
 * Feign's purpose is to ease development against http apis that feign restfulness. <br>
 * In implementation, Feign is a {@link Feign#newInstance factory} for generating {@link Target
 * targeted} http apis.
 */
public abstract class Feign {
    //builder方式进行构建
    public static Builder builder() {
        return new Builder();
    }

    /**
     * 为类中的方法生成一个唯一的key
     *
     * @see MethodMetadata#configKey()
     */
    public static String configKey(Class targetType, Method method) {
        StringBuilder builder = new StringBuilder();
        builder.append(targetType.getSimpleName());
        builder.append('#').append(method.getName()).append('(');
        //解析每个方法中的参数
        for (Type param : method.getGenericParameterTypes()) {
            param = Types.resolve(targetType, targetType, param);
            builder.append(Types.getRawType(param).getSimpleName()).append(',');
        }
        //去掉最后一个,
        if (method.getParameterTypes().length > 0) {
            builder.deleteCharAt(builder.length() - 1);
        }
        return builder.append(')').toString();
    }

    public abstract <T> T newInstance(Target<T> target);

    public static class Builder {
        //过滤器集合
        private final List<RequestInterceptor> requestInterceptors = new ArrayList<RequestInterceptor>();
        //默认没有日志
        private Logger.Level logLevel = Logger.Level.NONE;
        //默认解析接口上的注解
        private Contract contract = new Contract.Default();
        //默认client
        private Client client = new Client.Default(null, null);
        //失败重试
        private Retryer retryer = new Retryer.Default();
        //默认是没有日志
        private Logger logger = new NoOpLogger();
        private Encoder encoder = new Encoder.Default();
        private Decoder decoder = new Decoder.Default();
        //
        private QueryMapEncoder queryMapEncoder = new QueryMapEncoder.Default();
        //默认的错误解码器
        private ErrorDecoder errorDecoder = new ErrorDecoder.Default();
        //可选择
        private Options options = new Options();
        //反射工厂
        private InvocationHandlerFactory invocationHandlerFactory =
                new InvocationHandlerFactory.Default();
        private boolean decode404;
        private boolean closeAfterDecode = true;
        //异常传递策略
        private ExceptionPropagationPolicy propagationPolicy = NONE;

        //日志的级别
        public Builder logLevel(Logger.Level logLevel) {
            this.logLevel = logLevel;
            return this;
        }

        //注解解析器
        public Builder contract(Contract contract) {
            this.contract = contract;
            return this;
        }

        //http执行器
        public Builder client(Client client) {
            this.client = client;
            return this;
        }

        //错误重试
        public Builder retryer(Retryer retryer) {
            this.retryer = retryer;
            return this;
        }

        //日志
        public Builder logger(Logger logger) {
            this.logger = logger;
            return this;
        }

        //编码器
        public Builder encoder(Encoder encoder) {
            this.encoder = encoder;
            return this;
        }

        //解码器
        public Builder decoder(Decoder decoder) {
            this.decoder = decoder;
            return this;
        }

        //请求map编码器
        public Builder queryMapEncoder(QueryMapEncoder queryMapEncoder) {
            this.queryMapEncoder = queryMapEncoder;
            return this;
        }

        //匹配和解码
        public Builder mapAndDecode(ResponseMapper mapper, Decoder decoder) {
            this.decoder = new ResponseMappingDecoder(mapper, decoder);
            return this;
        }

        //404处理器
        public Builder decode404() {
            this.decode404 = true;
            return this;
        }

        //错误解码器
        public Builder errorDecoder(ErrorDecoder errorDecoder) {
            this.errorDecoder = errorDecoder;
            return this;
        }

        //错误解码器
        public Builder options(Options options) {
            this.options = options;
            return this;
        }

        //请求拦截器
        public Builder requestInterceptor(RequestInterceptor requestInterceptor) {
            this.requestInterceptors.add(requestInterceptor);
            return this;
        }

        /**
         * 会清空之前的拦截器
         */
        public Builder requestInterceptors(Iterable<RequestInterceptor> requestInterceptors) {
            this.requestInterceptors.clear();
            for (RequestInterceptor requestInterceptor : requestInterceptors) {
                this.requestInterceptors.add(requestInterceptor);
            }
            return this;
        }

        /**
         * 可以自己写反射
         * Allows you to override how reflective dispatch works inside of Feign.
         */
        public Builder invocationHandlerFactory(InvocationHandlerFactory invocationHandlerFactory) {
            this.invocationHandlerFactory = invocationHandlerFactory;
            return this;
        }

        //在获取到结果之后是否要关闭连接
        public Builder doNotCloseAfterDecode() {
            this.closeAfterDecode = false;
            return this;
        }

        //异常传递策略
        public Builder exceptionPropagationPolicy(ExceptionPropagationPolicy propagationPolicy) {
            this.propagationPolicy = propagationPolicy;
            return this;
        }

        //目标接口，硬编码的url
        public <T> T target(Class<T> apiType, String url) {
            return target(new HardCodedTarget<T>(apiType, url));
        }

        //目标接口
        public <T> T target(Target<T> target) {
            return build().newInstance(target);
        }

        public Feign build() {
            SynchronousMethodHandler.Factory synchronousMethodHandlerFactory =
                    new SynchronousMethodHandler.Factory(client, retryer, requestInterceptors, logger, logLevel, decode404, closeAfterDecode, propagationPolicy);
            ParseHandlersByName handlersByName =
                    new ParseHandlersByName(contract, options, encoder, decoder, queryMapEncoder, errorDecoder, synchronousMethodHandlerFactory);
            return new ReflectiveFeign(handlersByName, invocationHandlerFactory, queryMapEncoder);
        }
    }


    static class ResponseMappingDecoder implements Decoder {

        private final ResponseMapper mapper;
        private final Decoder delegate;

        ResponseMappingDecoder(ResponseMapper mapper, Decoder decoder) {
            this.mapper = mapper;
            this.delegate = decoder;
        }

        @Override
        public Object decode(Response response, Type type) throws IOException {
            return delegate.decode(mapper.map(response, type), type);
        }
    }
}
