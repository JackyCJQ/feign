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
package feign.querymap;

import feign.QueryMapEncoder;
import feign.codec.EncodeException;

import java.lang.reflect.Field;
import java.util.*;
import java.util.stream.Collectors;

/**
 * the query map will be generated using member variable names as query parameter names.
 * <p>
 * eg: "/uri?name={name}&number={number}"
 * <p>
 * order of included query parameters not guaranteed, and as usual, if any value is null, it will be
 * left out
 */
public class FieldQueryMapEncoder implements QueryMapEncoder {

    //缓存所有的参数类
    private final Map<Class<?>, ObjectParamMetadata> classToMetadata = new HashMap<Class<?>, ObjectParamMetadata>();

    /**
     * 获取对象中的属性和值
     */
    @Override
    public Map<String, Object> encode(Object object) throws EncodeException {
        try {
            //获取参数的类信息
            ObjectParamMetadata metadata = getMetadata(object.getClass());
            Map<String, Object> fieldNameToValue = new HashMap<String, Object>();
            //获取这个对象中的所有字段
            for (Field field : metadata.objectFields) {
                //通过反射的方式获取属性值
                Object value = field.get(object);
                if (value != null && value != object) {
                    fieldNameToValue.put(field.getName(), value);
                }
            }
            return fieldNameToValue;
        } catch (IllegalAccessException e) {
            throw new EncodeException("Failure encoding object into query map", e);
        }
    }

    private ObjectParamMetadata getMetadata(Class<?> objectType) {
        ObjectParamMetadata metadata = classToMetadata.get(objectType);
        if (metadata == null) {
            metadata = ObjectParamMetadata.parseObjectType(objectType);
            classToMetadata.put(objectType, metadata);
        }
        return metadata;
    }

    //存放了Object的字段信息
    private static class ObjectParamMetadata {

        private final List<Field> objectFields;

        private ObjectParamMetadata(List<Field> objectFields) {
            this.objectFields = Collections.unmodifiableList(objectFields);
        }

        private static ObjectParamMetadata parseObjectType(Class<?> type) {
            //获取所有的属性，并且这是可获取
            return new ObjectParamMetadata(
                    Arrays.stream(type.getDeclaredFields())
                            .filter(field -> !field.isSynthetic())
                            .peek(field -> field.setAccessible(true))
                            .collect(Collectors.toList()));
        }
    }
}
