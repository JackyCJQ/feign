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

import java.io.IOException;

import static feign.Util.UTF_8;
import static java.lang.String.format;

/**
 * Origin exception type for all Http Apis.
 */
public class FeignException extends RuntimeException {

    private static final long serialVersionUID = 0;
    private int status;
    private byte[] content;

    //多定义了一个状态码
    protected FeignException(int status, String message, Throwable cause) {
        super(message, cause);
        this.status = status;
    }

    //
    protected FeignException(int status, String message, Throwable cause, byte[] content) {
        super(message, cause);
        this.status = status;
        this.content = content;
    }

    protected FeignException(int status, String message) {
        super(message);
        this.status = status;
    }

    protected FeignException(int status, String message, byte[] content) {
        super(message);
        this.status = status;
        this.content = content;
    }

    public int status() {
        return this.status;
    }

    public byte[] content() {
        return this.content;
    }

    //这里又是用的utf-8编码转化的
    public String contentUTF8() {
        if (content != null) {
            return new String(content, UTF_8);
        } else {
            return "";
        }
    }

    //读取返回结果的时候抛出的错
    static FeignException errorReading(Request request, Response response, IOException cause) {
        return new FeignException(
                response.status(),
                format("%s reading %s %s", cause.getMessage(), request.httpMethod(), request.url()),
                cause,
                request.body());
    }

    //读取返回结果的时候的错误
    public static FeignException errorStatus(String methodKey, Response response) {
        String message = format("status %s reading %s", response.status(), methodKey);

        byte[] body = {};
        try {
            if (response.body() != null) {
                body = Util.toByteArray(response.body().asInputStream());
            }
        } catch (IOException ignored) { // NOPMD
        }

        return errorStatus(response.status(), message, body);
    }

    //细分了错误的类型
    private static FeignException errorStatus(int status, String message, byte[] body) {
        switch (status) {
            case 400:
                return new BadRequest(message, body);
            case 401:
                return new Unauthorized(message, body);
            case 403:
                return new Forbidden(message, body);
            case 404:
                return new NotFound(message, body);
            case 405:
                return new MethodNotAllowed(message, body);
            case 406:
                return new NotAcceptable(message, body);
            case 409:
                return new Conflict(message, body);
            case 410:
                return new Gone(message, body);
            case 415:
                return new UnsupportedMediaType(message, body);
            case 429:
                return new TooManyRequests(message, body);
            case 422:
                return new UnprocessableEntity(message, body);
            case 500:
                return new InternalServerError(message, body);
            case 501:
                return new NotImplemented(message, body);
            case 502:
                return new BadGateway(message, body);
            case 503:
                return new ServiceUnavailable(message, body);
            case 504:
                return new GatewayTimeout(message, body);
            default:
                return new FeignException(status, message, body);
        }
    }

    static FeignException errorExecuting(Request request, IOException cause) {
        return new RetryableException(
                -1,
                format("%s executing %s %s", cause.getMessage(), request.httpMethod(), request.url()),
                request.httpMethod(),
                cause,
                null);
    }

    public static class BadRequest extends FeignException {
        public BadRequest(String message, byte[] body) {
            super(400, message, body);
        }
    }

    public static class Unauthorized extends FeignException {
        public Unauthorized(String message, byte[] body) {
            super(401, message, body);
        }
    }

    public static class Forbidden extends FeignException {
        public Forbidden(String message, byte[] body) {
            super(403, message, body);
        }
    }

    public static class NotFound extends FeignException {
        public NotFound(String message, byte[] body) {
            super(404, message, body);
        }
    }

    public static class MethodNotAllowed extends FeignException {
        public MethodNotAllowed(String message, byte[] body) {
            super(405, message, body);
        }
    }

    public static class NotAcceptable extends FeignException {
        public NotAcceptable(String message, byte[] body) {
            super(406, message, body);
        }
    }

    public static class Conflict extends FeignException {
        public Conflict(String message, byte[] body) {
            super(409, message, body);
        }
    }

    public static class Gone extends FeignException {
        public Gone(String message, byte[] body) {
            super(410, message, body);
        }
    }

    public static class UnsupportedMediaType extends FeignException {
        public UnsupportedMediaType(String message, byte[] body) {
            super(415, message, body);
        }
    }

    public static class TooManyRequests extends FeignException {
        public TooManyRequests(String message, byte[] body) {
            super(429, message, body);
        }
    }

    public static class UnprocessableEntity extends FeignException {
        public UnprocessableEntity(String message, byte[] body) {
            super(422, message, body);
        }
    }

    public static class InternalServerError extends FeignException {
        public InternalServerError(String message, byte[] body) {
            super(500, message, body);
        }
    }

    public static class NotImplemented extends FeignException {
        public NotImplemented(String message, byte[] body) {
            super(501, message, body);
        }
    }

    public static class BadGateway extends FeignException {
        public BadGateway(String message, byte[] body) {
            super(502, message, body);
        }
    }

    public static class ServiceUnavailable extends FeignException {
        public ServiceUnavailable(String message, byte[] body) {
            super(503, message, body);
        }
    }

    public static class GatewayTimeout extends FeignException {
        public GatewayTimeout(String message, byte[] body) {
            super(504, message, body);
        }
    }
}
