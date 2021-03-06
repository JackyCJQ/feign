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
package feign.template;

import feign.Util;

import java.nio.charset.Charset;
import java.util.Map;

/**
 * Template for @{@link feign.Body} annotated Templates. Unresolved expressions are preserved as
 * literals and literals are not URI encoded.
 */
public final class BodyTemplate extends Template {

    private static final String JSON_TOKEN_START = "{";
    private static final String JSON_TOKEN_END = "}";
    //上面两个字符的转义
    private static final String JSON_TOKEN_START_ENCODED = "%7B";
    private static final String JSON_TOKEN_END_ENCODED = "%7D";
    private boolean json = false;

    public static BodyTemplate create(String template) {
        return new BodyTemplate(template, Util.UTF_8);
    }

    private BodyTemplate(String value, Charset charset) {
        super(value, ExpansionOptions.ALLOW_UNRESOLVED, EncodingOptions.NOT_REQUIRED, false, charset);
        //判断是否是json格式
        if (value.startsWith(JSON_TOKEN_START_ENCODED) && value.endsWith(JSON_TOKEN_END_ENCODED)) {
            this.json = true;
        }
    }

    @Override
    public String expand(Map<String, ?> variables) {
        //属性进行扩展
        String expanded = super.expand(variables);
        //如果是json格式
        if (this.json) {
            /* decode only the first and last character */
            StringBuilder sb = new StringBuilder();
            sb.append(JSON_TOKEN_START);
            //截取属性进行添加
            sb.append(expanded,
                    expanded.indexOf(JSON_TOKEN_START_ENCODED) + JSON_TOKEN_START_ENCODED.length(), expanded.lastIndexOf(JSON_TOKEN_END_ENCODED));
            sb.append(JSON_TOKEN_END);
            return sb.toString();
        }
        return expanded;
    }


}
