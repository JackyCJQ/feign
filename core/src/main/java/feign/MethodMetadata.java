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

import feign.Param.Expander;

import java.io.Serializable;
import java.lang.reflect.Type;
import java.util.*;

/**
 * 方法的元数据类，就是一个记录的model,没有实际的逻辑处理
 */
public final class MethodMetadata implements Serializable {

    private static final long serialVersionUID = 1L;
    private String configKey; //每个方法生成一个唯一的字符串
    private transient Type returnType;//transient 该关键字修饰的变量不会被序列化
    private Integer urlIndex;
    private Integer bodyIndex;
    private Integer headerMapIndex;
    private Integer queryMapIndex;
    private boolean queryMapEncoded;
    private transient Type bodyType;//
    private RequestTemplate template = new RequestTemplate();//请求模板
    private List<String> formParams = new ArrayList<String>();//参数
    private Map<Integer, Collection<String>> indexToName = new LinkedHashMap<Integer, Collection<String>>();
    private Map<Integer, Class<? extends Expander>> indexToExpanderClass = new LinkedHashMap<Integer, Class<? extends Expander>>();
    private Map<Integer, Boolean> indexToEncoded = new LinkedHashMap<Integer, Boolean>();
    private transient Map<Integer, Expander> indexToExpander;

    MethodMetadata() {
    }

    public String configKey() {
        return configKey;
    }

    public MethodMetadata configKey(String configKey) {
        this.configKey = configKey;
        return this;
    }

    public Type returnType() {
        return returnType;
    }

    public MethodMetadata returnType(Type returnType) {
        this.returnType = returnType;
        return this;
    }

    public Integer urlIndex() {
        return urlIndex;
    }

    public MethodMetadata urlIndex(Integer urlIndex) {
        this.urlIndex = urlIndex;
        return this;
    }

    public Integer bodyIndex() {
        return bodyIndex;
    }

    public MethodMetadata bodyIndex(Integer bodyIndex) {
        this.bodyIndex = bodyIndex;
        return this;
    }

    public Integer headerMapIndex() {
        return headerMapIndex;
    }

    public MethodMetadata headerMapIndex(Integer headerMapIndex) {
        this.headerMapIndex = headerMapIndex;
        return this;
    }

    public Integer queryMapIndex() {
        return queryMapIndex;
    }

    public MethodMetadata queryMapIndex(Integer queryMapIndex) {
        this.queryMapIndex = queryMapIndex;
        return this;
    }

    public boolean queryMapEncoded() {
        return queryMapEncoded;
    }

    public MethodMetadata queryMapEncoded(boolean queryMapEncoded) {
        this.queryMapEncoded = queryMapEncoded;
        return this;
    }

    public Type bodyType() {
        return bodyType;
    }

    public MethodMetadata bodyType(Type bodyType) {
        this.bodyType = bodyType;
        return this;
    }

    public RequestTemplate template() {
        return template;
    }

    public List<String> formParams() {
        return formParams;
    }

    public Map<Integer, Collection<String>> indexToName() {
        return indexToName;
    }

    public Map<Integer, Boolean> indexToEncoded() {
        return indexToEncoded;
    }

    public Map<Integer, Class<? extends Expander>> indexToExpanderClass() {
        return indexToExpanderClass;
    }

    public MethodMetadata indexToExpander(Map<Integer, Expander> indexToExpander) {
        this.indexToExpander = indexToExpander;
        return this;
    }

    /**
     * When not null, this value will be used instead of {@link #indexToExpander()}.
     */
    public Map<Integer, Expander> indexToExpander() {
        return indexToExpander;
    }
}
