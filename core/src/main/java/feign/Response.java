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

import java.io.*;
import java.nio.charset.Charset;
import java.util.*;

import static feign.Util.*;

/**
 * An immutable response to an http invocation which only returns string content.
 */
public final class Response implements Closeable {

    private final int status;//返回的状态码
    private final String reason;//返回的文案
    private final Map<String, Collection<String>> headers;//返回头
    private final Body body;//返回头
    private final Request request; //请求

    private Response(Builder builder) {
        //保留请求
        checkState(builder.request != null, "original request is required");
        this.status = builder.status;
        this.request = builder.request;
        this.reason = builder.reason; // nullable
        this.headers = (builder.headers != null)
                ? Collections.unmodifiableMap(caseInsensitiveCopyOf(builder.headers)) : new LinkedHashMap<>();
        this.body = builder.body; // nullable
    }

    public Builder toBuilder() {
        return new Builder(this);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        int status;//返回的状态码
        String reason; //
        Map<String, Collection<String>> headers;//请求头
        Body body; //返回体内容
        Request request;//请求

        Builder() {
        }

        Builder(Response source) {
            this.status = source.status;
            this.reason = source.reason;
            this.headers = source.headers;
            this.body = source.body;
            this.request = source.request;
        }

        public Builder status(int status) {
            this.status = status;
            return this;
        }

        public Builder reason(String reason) {
            this.reason = reason;
            return this;
        }

        public Builder headers(Map<String, Collection<String>> headers) {
            this.headers = headers;
            return this;
        }

        public Builder body(Body body) {
            this.body = body;
            return this;
        }

        public Builder body(InputStream inputStream, Integer length) {
            this.body = InputStreamBody.orNull(inputStream, length);
            return this;
        }

        public Builder body(byte[] data) {
            this.body = ByteArrayBody.orNull(data);
            return this;
        }

        public Builder body(String text, Charset charset) {
            this.body = ByteArrayBody.orNull(text, charset);
            return this;
        }

        public Builder request(Request request) {
            checkNotNull(request, "request is required");
            this.request = request;
            return this;
        }

        public Response build() {
            return new Response(this);
        }
    }

    public int status() {
        return status;
    }

    public String reason() {
        return reason;
    }

    public Map<String, Collection<String>> headers() {
        return headers;
    }

    public Body body() {
        return body;
    }

    public Request request() {
        return request;
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder("HTTP/1.1 ").append(status);
        if (reason != null)
            builder.append(' ').append(reason);
        builder.append('\n');
        for (String field : headers.keySet()) {
            for (String value : valuesOrEmpty(headers, field)) {
                builder.append(field).append(": ").append(value).append('\n');
            }
        }
        if (body != null)
            builder.append('\n').append(body);
        return builder.toString();
    }

    @Override
    public void close() {
        Util.ensureClosed(body);
    }

    public interface Body extends Closeable {

        Integer length();

        boolean isRepeatable();

        InputStream asInputStream() throws IOException;

        Reader asReader() throws IOException;

        Reader asReader(Charset charset) throws IOException;
    }

    private static final class InputStreamBody implements Response.Body {

        private final InputStream inputStream;
        private final Integer length;

        private InputStreamBody(InputStream inputStream, Integer length) {
            this.inputStream = inputStream;
            this.length = length;
        }

        private static Body orNull(InputStream inputStream, Integer length) {
            if (inputStream == null) {
                return null;
            }
            return new InputStreamBody(inputStream, length);
        }

        @Override
        public Integer length() {
            return length;
        }

        @Override
        public boolean isRepeatable() {
            return false;
        }

        @Override
        public InputStream asInputStream() throws IOException {
            return inputStream;
        }

        @Override
        public Reader asReader() throws IOException {
            return new InputStreamReader(inputStream, UTF_8);
        }

        @Override
        public Reader asReader(Charset charset) throws IOException {
            checkNotNull(charset, "charset should not be null");
            return new InputStreamReader(inputStream, charset);
        }

        @Override
        public void close() throws IOException {
            inputStream.close();
        }
    }

    private static final class ByteArrayBody implements Response.Body {

        private final byte[] data;

        public ByteArrayBody(byte[] data) {
            this.data = data;
        }

        private static Body orNull(byte[] data) {
            if (data == null) {
                return null;
            }
            return new ByteArrayBody(data);
        }

        private static Body orNull(String text, Charset charset) {
            if (text == null) {
                return null;
            }
            checkNotNull(charset, "charset");
            return new ByteArrayBody(text.getBytes(charset));
        }

        @Override
        public Integer length() {
            return data.length;
        }

        @Override
        public boolean isRepeatable() {
            return true;
        }

        @Override
        public InputStream asInputStream() throws IOException {
            return new ByteArrayInputStream(data);
        }

        @Override
        public Reader asReader() throws IOException {
            return new InputStreamReader(asInputStream(), UTF_8);
        }

        @Override
        public Reader asReader(Charset charset) throws IOException {
            checkNotNull(charset, "charset should not be null");
            return new InputStreamReader(asInputStream(), charset);
        }

        @Override
        public void close() throws IOException {
        }

        @Override
        public String toString() {
            return decodeOrDefault(data, UTF_8, "Binary data");
        }
    }

    private static Map<String, Collection<String>> caseInsensitiveCopyOf(Map<String, Collection<String>> headers) {
        //按照大小写进行了排序
        Map<String, Collection<String>> result = new TreeMap<String, Collection<String>>(String.CASE_INSENSITIVE_ORDER);

        for (Map.Entry<String, Collection<String>> entry : headers.entrySet()) {
            String headerName = entry.getKey();
            if (!result.containsKey(headerName)) {
                result.put(headerName.toLowerCase(Locale.ROOT), new LinkedList<String>());
            }
            result.get(headerName).addAll(entry.getValue());
        }
        return result;
    }
}
