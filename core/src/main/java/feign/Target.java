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

import static feign.Util.checkNotNull;
import static feign.Util.emptyToNull;

public interface Target<T> {

    /* The type of the interface this target applies to. */
    Class<T> type();

    /* configuration key associated with this target. */
    String name();

    /* base HTTP URL of the target. */
    String url();

    /**
     * Targets a template to this target, adding the {@link #url() base url} and any target-specific
     * headers or query parameters. <br>
     * <br>
     * For example: <br>
     * <pre>
     * public Request apply(RequestTemplate input) {
     *   input.insert(0, url());
     *   input.replaceHeader(&quot;X-Auth&quot;, currentToken);
     *   return input.asRequest();
     * }
     */
    public Request apply(RequestTemplate input);

    /**
     * url硬编码实现
     *
     * @param <T>
     */
    public static class HardCodedTarget<T> implements Target<T> {

        private final Class<T> type;
        private final String name;
        private final String url;

        public HardCodedTarget(Class<T> type, String url) {
            this(type, url, url);
        }

        public HardCodedTarget(Class<T> type, String name, String url) {
            this.type = checkNotNull(type, "type");
            this.name = checkNotNull(emptyToNull(name), "name");
            this.url = checkNotNull(emptyToNull(url), "url");
        }

        @Override
        public Class<T> type() {
            return type;
        }

        @Override
        public String name() {
            return name;
        }

        @Override
        public String url() {
            return url;
        }

        /* no authentication or other special activity. just insert the url. */
        @Override
        public Request apply(RequestTemplate input) {
            if (input.url().indexOf("http") != 0) {
                //直接请求地址
                input.target(url());
            }
            return input.request();
        }

        @Override
        public boolean equals(Object obj) {
            if (obj instanceof HardCodedTarget) {
                HardCodedTarget<?> other = (HardCodedTarget) obj;
                return type.equals(other.type)
                        && name.equals(other.name)
                        && url.equals(other.url);
            }
            return false;
        }

        @Override
        public int hashCode() {
            int result = 17;
            result = 31 * result + type.hashCode();
            result = 31 * result + name.hashCode();
            result = 31 * result + url.hashCode();
            return result;
        }

        @Override
        public String toString() {
            //紧紧做了简单的区分
            if (name.equals(url)) {
                return "HardCodedTarget(type=" + type.getSimpleName() + ", url=" + url + ")";
            }
            return "HardCodedTarget(type=" + type.getSimpleName() + ", name=" + name + ", url=" + url
                    + ")";
        }
    }

    //创建一个空的接口实现类
    public static final class EmptyTarget<T> implements Target<T> {

        private final Class<T> type;
        private final String name;

        EmptyTarget(Class<T> type, String name) {
            this.type = checkNotNull(type, "type");
            this.name = checkNotNull(emptyToNull(name), "name");
        }

        public static <T> EmptyTarget<T> create(Class<T> type) {
            return new EmptyTarget<T>(type, "empty:" + type.getSimpleName());
        }

        public static <T> EmptyTarget<T> create(Class<T> type, String name) {
            return new EmptyTarget<T>(type, name);
        }

        @Override
        public Class<T> type() {
            return type;
        }

        @Override
        public String name() {
            return name;
        }

        @Override
        public String url() {
            throw new UnsupportedOperationException("Empty targets don't have URLs");
        }

        @Override
        public Request apply(RequestTemplate input) {
            if (input.url().indexOf("http") != 0) {
                throw new UnsupportedOperationException(
                        "Request with non-absolute URL not supported with empty target");
            }
            return input.request();
        }

        @Override
        public boolean equals(Object obj) {
            if (obj instanceof EmptyTarget) {
                EmptyTarget<?> other = (EmptyTarget) obj;
                return type.equals(other.type)
                        && name.equals(other.name);
            }
            return false;
        }

        @Override
        public int hashCode() {
            int result = 17;
            result = 31 * result + type.hashCode();
            result = 31 * result + name.hashCode();
            return result;
        }

        @Override
        public String toString() {
            if (name.equals("empty:" + type.getSimpleName())) {
                return "EmptyTarget(type=" + type.getSimpleName() + ")";
            }
            return "EmptyTarget(type=" + type.getSimpleName() + ", name=" + name + ")";
        }
    }
}
