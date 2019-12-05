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

/**
 * 请求拦截器
 * Zero or more {@code RequestInterceptors} may be configured for purposes such as adding headers to
 * all requests. No guarantees are give with regards to the order that interceptors are applied.
 * Once interceptors are applied, {@link Target#apply(RequestTemplate)} is called to create the
 * immutable http request sent via {@link Client#execute(Request, feign.Request.Options)}.
 */
public interface RequestInterceptor {

    /**
     * Called for every request. Add data using methods on the supplied {@link RequestTemplate}.
     */
    void apply(RequestTemplate template);
}
