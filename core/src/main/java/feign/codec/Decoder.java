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
package feign.codec;

import feign.FeignException;
import feign.Response;
import feign.Util;

import java.io.IOException;
import java.lang.reflect.Type;

/**
 * Decodes an HTTP response into a single object of the given type. Invoked when
 * Response.status() is in the 2xx range and the return type is neither  void nor
 * Response.
 */
public interface Decoder {

    /**
     * Decodes an http response into an object corresponding to its
     * {@link java.lang.reflect.Method#getGenericReturnType() generic return type}.
     */
    Object decode(Response response, Type type) throws IOException, DecodeException, FeignException;

    /**
     * Default implementation of {@code Decoder}.
     */
    public class Default extends StringDecoder {

        @Override
        public Object decode(Response response, Type type) throws IOException {
            //如果是返回404或204则返回null
            if (response.status() == 404 || response.status() == 204)
                return Util.emptyValueOf(type);
            //如果没有返回则也是null
            if (response.body() == null)
                return null;
            //如果是字节数组，则返回字节数组
            if (byte[].class.equals(type)) {
                return Util.toByteArray(response.body().asInputStream());
            }
            //转化为String类型
            return super.decode(response, type);
        }
    }
}
