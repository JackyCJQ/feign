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

import feign.Request.Options;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLSocketFactory;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.GZIPOutputStream;

import static feign.Util.*;
import static java.lang.String.format;

/**
 * Submits HTTP {@link Request requests}. Implementations are expected to be thread-safe.
 */
public interface Client {

    /**
     * Executes a request against its {@link Request#url() url} and returns a response.
     */
    Response execute(Request request, Options options) throws IOException;

    public static class Default implements Client {

        private final SSLSocketFactory sslContextFactory;
        private final HostnameVerifier hostnameVerifier;

        public Default(SSLSocketFactory sslContextFactory, HostnameVerifier hostnameVerifier) {
            this.sslContextFactory = sslContextFactory;
            this.hostnameVerifier = hostnameVerifier;
        }

        @Override
        public Response execute(Request request, Options options) throws IOException {
            //转换参数并进行请求
            HttpURLConnection connection = convertAndSend(request, options);
            //获取请求的结果
            return convertResponse(connection, request);
        }

        HttpURLConnection convertAndSend(Request request, Options options) throws IOException {
            //利用jdk获取一个连接
            final HttpURLConnection connection =
                    (HttpURLConnection) new URL(request.url()).openConnection();
            if (connection instanceof HttpsURLConnection) {
                HttpsURLConnection sslCon = (HttpsURLConnection) connection;
                if (sslContextFactory != null) {
                    sslCon.setSSLSocketFactory(sslContextFactory);
                }
                if (hostnameVerifier != null) {
                    sslCon.setHostnameVerifier(hostnameVerifier);
                }
            }
            connection.setConnectTimeout(options.connectTimeoutMillis());
            connection.setReadTimeout(options.readTimeoutMillis());
            connection.setAllowUserInteraction(false);
            connection.setInstanceFollowRedirects(options.isFollowRedirects());
            //设置请求的方法 post  get等方式
            connection.setRequestMethod(request.httpMethod().name());
            //获取内容编码方式
            Collection<String> contentEncodingValues = request.headers().get(CONTENT_ENCODING);
            //配置的两种压缩的方式
            boolean gzipEncodedRequest =
                    contentEncodingValues != null && contentEncodingValues.contains(ENCODING_GZIP);
            boolean deflateEncodedRequest =
                    contentEncodingValues != null && contentEncodingValues.contains(ENCODING_DEFLATE);

            boolean hasAcceptHeader = false;
            Integer contentLength = null;
            for (String field : request.headers().keySet()) {
                if (field.equalsIgnoreCase("Accept")) {
                    hasAcceptHeader = true;
                }
                for (String value : request.headers().get(field)) {
                    if (field.equals(CONTENT_LENGTH)) {
                        //如果没有配置压缩
                        if (!gzipEncodedRequest && !deflateEncodedRequest) {
                            contentLength = Integer.valueOf(value);
                            //设置content-length:23423
                            connection.addRequestProperty(field, value);
                        }
                    } else {
                        //其他的属性就原封不动的回写
                        connection.addRequestProperty(field, value);
                    }
                }
            }
            // Some servers choke on the default accept string.
            if (!hasAcceptHeader) {
                connection.addRequestProperty("Accept", "*/*");
            }

            if (request.body() != null) {
                if (contentLength != null) {
                    connection.setFixedLengthStreamingMode(contentLength);
                } else {
                    connection.setChunkedStreamingMode(8196);
                }
                //获取返回的结果
                connection.setDoOutput(true);
                OutputStream out = connection.getOutputStream();
                //根据压缩获取对应的压缩结果
                if (gzipEncodedRequest) {
                    out = new GZIPOutputStream(out);
                } else if (deflateEncodedRequest) {
                    out = new DeflaterOutputStream(out);
                }
                try {
                    out.write(request.body());
                } finally {
                    try {
                        out.close();
                    } catch (IOException suppressed) { // NOPMD
                    }
                }
            }
            return connection;
        }

        Response convertResponse(HttpURLConnection connection, Request request) throws IOException {
            int status = connection.getResponseCode();
            String reason = connection.getResponseMessage();

            if (status < 0) {
                throw new IOException(format("Invalid status(%s) executing %s %s", status,
                        connection.getRequestMethod(), connection.getURL()));
            }

            Map<String, Collection<String>> headers = new LinkedHashMap<String, Collection<String>>();
            for (Map.Entry<String, List<String>> field : connection.getHeaderFields().entrySet()) {
                // response message
                if (field.getKey() != null) {
                    headers.put(field.getKey(), field.getValue());
                }
            }
            //返回结果的长度吗
            Integer length = connection.getContentLength();
            if (length == -1) {
                length = null;
            }
            InputStream stream;
            if (status >= 400) {
                stream = connection.getErrorStream();
            } else {
                stream = connection.getInputStream();
            }
            return Response.builder()
                    .status(status)
                    .reason(reason)
                    .headers(headers)
                    .request(request)
                    .body(stream, length)
                    .build();
        }
    }
}
