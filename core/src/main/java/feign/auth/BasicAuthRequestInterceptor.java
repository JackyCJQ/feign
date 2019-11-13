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
package feign.auth;

import feign.RequestInterceptor;
import feign.RequestTemplate;

import java.nio.charset.Charset;

import static feign.Util.ISO_8859_1;
import static feign.Util.checkNotNull;

/**
 * 在请求头中添加基本的验证信息
 */
public class BasicAuthRequestInterceptor implements RequestInterceptor {

    private final String headerValue;

    public BasicAuthRequestInterceptor(String username, String password) {
        this(username, password, ISO_8859_1);
    }


    public BasicAuthRequestInterceptor(String username, String password, Charset charset) {
        checkNotNull(username, "username");
        checkNotNull(password, "password");
        this.headerValue = "Basic " + base64Encode((username + ":" + password).getBytes(charset));
    }

    //出于安全性要求，进行了base64加密
    private static String base64Encode(byte[] bytes) {
        return Base64.encode(bytes);
    }

    //请求头中添加认证信息
    @Override
    public void apply(RequestTemplate template) {
        template.header("Authorization", headerValue);
    }
}

