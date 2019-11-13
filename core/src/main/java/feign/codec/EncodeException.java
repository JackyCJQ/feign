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

import static feign.Util.checkNotNull;

//解密时的异常
public class EncodeException extends FeignException {

    private static final long serialVersionUID = 1L;

    public EncodeException(String message) {
        super(-1, checkNotNull(message, "message"));
    }


    public EncodeException(String message, Throwable cause) {
        super(-1, message, checkNotNull(cause, "cause"));
    }
}
