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

import feign.Request.HttpMethod;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.net.URI;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static feign.Util.checkState;
import static feign.Util.emptyToNull;

/**
 * 注解解析器
 */
public interface Contract {

    /**
     * 把方法解析成MethodMetadata
     */
    List<MethodMetadata> parseAndValidatateMetadata(Class<?> targetType);

    abstract class BaseContract implements Contract {

        @Override
        public List<MethodMetadata> parseAndValidatateMetadata(Class<?> targetType) {
            //接口必须为不带范型的接口
            checkState(targetType.getTypeParameters().length == 0, "Parameterized types unsupported: %s", targetType.getSimpleName());
            //校验仅仅支持继承一个接口的类
            checkState(targetType.getInterfaces().length <= 1, "Only single inheritance supported: %s",
                    targetType.getSimpleName());
            //如果继承了一个接口
            if (targetType.getInterfaces().length == 1) {
                //还必须仅仅支持继承一层的接口
                checkState(targetType.getInterfaces()[0].getInterfaces().length == 0,
                        "Only single-level inheritance supported: %s", targetType.getSimpleName());
            }
            //解析接口中的方法，生成的数据
            Map<String, MethodMetadata> result = new LinkedHashMap<String, MethodMetadata>();
            //遍历接口中的方法
            for (Method method : targetType.getMethods()) {
                //如果是Object中的方法，或者是static方法或者是默认方法
                if (method.getDeclaringClass() == Object.class || (method.getModifiers() & Modifier.STATIC) != 0 || Util.isDefault(method)) {
                    continue;
                }
                //解析方法获取元数据
                MethodMetadata metadata = parseAndValidateMetadata(targetType, method);
                //并且重写的方法还不支持
                checkState(!result.containsKey(metadata.configKey()), "Overrides unsupported: %s", metadata.configKey());
                //添加进缓存
                result.put(metadata.configKey(), metadata);
            }
            return new ArrayList<>(result.values());
        }

        /**
         * 解析接口上的方法
         * Called indirectly by {@link #parseAndValidatateMetadata(Class)}.
         */
        protected MethodMetadata parseAndValidateMetadata(Class<?> targetType, Method method) {
            MethodMetadata data = new MethodMetadata();
            //方法的返回类型
            data.returnType(Types.resolve(targetType, targetType, method.getGenericReturnType()));
            //每个方法生成一个唯一值
            data.configKey(Feign.configKey(targetType, method));

            if (targetType.getInterfaces().length == 1) {
                //解析接口上的注解
                processAnnotationOnClass(data, targetType.getInterfaces()[0]);
            }
            //解析当前接口上的注解，通用到每一个接口
            processAnnotationOnClass(data, targetType);

            //解析方法上的注解
            for (Annotation methodAnnotation : method.getAnnotations()) {
                processAnnotationOnMethod(data, methodAnnotation, method);
            }
            //验证方法的名字
            checkState(data.template().method() != null,
                    "Method %s not annotated with HTTP method type (ex. GET, POST)",
                    method.getName());
            //获取参数上的注解
            Class<?>[] parameterTypes = method.getParameterTypes();
            Type[] genericParameterTypes = method.getGenericParameterTypes();
            //获取参数中的注解
            Annotation[][] parameterAnnotations = method.getParameterAnnotations();
            int count = parameterAnnotations.length;
            for (int i = 0; i < count; i++) {
                boolean isHttpAnnotation = false;
                if (parameterAnnotations[i] != null) {
                    //获取参数上的注解
                    isHttpAnnotation = processAnnotationsOnParameter(data, parameterAnnotations[i], i);
                }
                if (parameterTypes[i] == URI.class) {
                    data.urlIndex(i);
                } else if (!isHttpAnnotation) {
                    checkState(data.formParams().isEmpty(),
                            "Body parameters cannot be used with form parameters.");
                    checkState(data.bodyIndex() == null, "Method has too many Body parameters: %s", method);
                    data.bodyIndex(i);
                    data.bodyType(Types.resolve(targetType, targetType, genericParameterTypes[i]));
                }
            }

            if (data.headerMapIndex() != null) {
                checkMapString("HeaderMap", parameterTypes[data.headerMapIndex()],
                        genericParameterTypes[data.headerMapIndex()]);
            }

            if (data.queryMapIndex() != null) {
                if (Map.class.isAssignableFrom(parameterTypes[data.queryMapIndex()])) {
                    checkMapKeys("QueryMap", genericParameterTypes[data.queryMapIndex()]);
                }
            }

            return data;
        }

        private static void checkMapString(String name, Class<?> type, Type genericType) {
            checkState(Map.class.isAssignableFrom(type),
                    "%s parameter must be a Map: %s", name, type);
            checkMapKeys(name, genericType);
        }

        private static void checkMapKeys(String name, Type genericType) {
            Class<?> keyClass = null;

            // assume our type parameterized
            if (ParameterizedType.class.isAssignableFrom(genericType.getClass())) {
                Type[] parameterTypes = ((ParameterizedType) genericType).getActualTypeArguments();
                keyClass = (Class<?>) parameterTypes[0];
            } else if (genericType instanceof Class<?>) {
                // raw class, type parameters cannot be inferred directly, but we can scan any extended
                // interfaces looking for any explict types
                Type[] interfaces = ((Class) genericType).getGenericInterfaces();
                if (interfaces != null) {
                    for (Type extended : interfaces) {
                        if (ParameterizedType.class.isAssignableFrom(extended.getClass())) {
                            // use the first extended interface we find.
                            Type[] parameterTypes = ((ParameterizedType) extended).getActualTypeArguments();
                            keyClass = (Class<?>) parameterTypes[0];
                            break;
                        }
                    }
                }
            }

            if (keyClass != null) {
                checkState(String.class.equals(keyClass),
                        "%s key must be a String: %s", name, keyClass.getSimpleName());
            }
        }

        protected abstract void processAnnotationOnClass(MethodMetadata data, Class<?> clz);

        protected abstract void processAnnotationOnMethod(MethodMetadata data,
                                                          Annotation annotation,
                                                          Method method);

        protected abstract boolean processAnnotationsOnParameter(MethodMetadata data,
                                                                 Annotation[] annotations,
                                                                 int paramIndex);

        protected void nameParam(MethodMetadata data, String name, int i) {
            Collection<String> names =
                    data.indexToName().containsKey(i) ? data.indexToName().get(i) : new ArrayList<String>();
            names.add(name);
            data.indexToName().put(i, names);
        }
    }

    /**
     * 解析feign注解默认的实现
     */
    class Default extends BaseContract {

        static final Pattern REQUEST_LINE_PATTERN = Pattern.compile("^([A-Z]+)[ ]*(.*)$");

        //header只能加在类上
        @Override
        protected void processAnnotationOnClass(MethodMetadata data, Class<?> targetType) {
            //如果接口上面有@Header注解
            if (targetType.isAnnotationPresent(Headers.class)) {
                String[] headersOnType = targetType.getAnnotation(Headers.class).value();
                checkState(headersOnType.length > 0, "Headers annotation was empty on type %s.",
                        targetType.getName());
                Map<String, Collection<String>> headers = toMap(headersOnType);
                //这里好像没有意义
                headers.putAll(data.template().headers());
                //先清空在设置
                data.template().headers(null);
                data.template().headers(headers);
            }
        }

        @Override
        protected void processAnnotationOnMethod(MethodMetadata data,
                                                 Annotation methodAnnotation,
                                                 Method method) {
            //获取注解的类型
            Class<? extends Annotation> annotationType = methodAnnotation.annotationType();
            if (annotationType == RequestLine.class) {
                //可以强制转化
                String requestLine = RequestLine.class.cast(methodAnnotation).value();
                checkState(emptyToNull(requestLine) != null,
                        "RequestLine annotation was empty on method %s.", method.getName());

                //注解分为两个部分
                Matcher requestLineMatcher = REQUEST_LINE_PATTERN.matcher(requestLine);
                if (!requestLineMatcher.find()) {
                    throw new IllegalStateException(String.format(
                            "RequestLine annotation didn't start with an HTTP verb on method %s",
                            method.getName()));
                } else {
                    //添加请求的方法和请求的方式
                    data.template().method(HttpMethod.valueOf(requestLineMatcher.group(1)));
                    data.template().uri(requestLineMatcher.group(2));
                }
                //添加另外两个属性
                data.template().decodeSlash(RequestLine.class.cast(methodAnnotation).decodeSlash());
                data.template().collectionFormat(RequestLine.class.cast(methodAnnotation).collectionFormat());

            } else if (annotationType == Body.class) {
                String body = Body.class.cast(methodAnnotation).value();
                checkState(emptyToNull(body) != null, "Body annotation was empty on method %s.",
                        method.getName());
                if (body.indexOf('{') == -1) {
                    data.template().body(body);
                } else {
                    //带不带有什么区别吗？
                    data.template().bodyTemplate(body);
                }
            } else if (annotationType == Headers.class) {
                //还可以在添加header信息
                String[] headersOnMethod = Headers.class.cast(methodAnnotation).value();
                checkState(headersOnMethod.length > 0, "Headers annotation was empty on method %s.",
                        method.getName());
                data.template().headers(toMap(headersOnMethod));
            }
        }

        @Override
        protected boolean processAnnotationsOnParameter(MethodMetadata data,
                                                        Annotation[] annotations,
                                                        int paramIndex) {
            boolean isHttpAnnotation = false;
            for (Annotation annotation : annotations) {
                Class<? extends Annotation> annotationType = annotation.annotationType();
                if (annotationType == Param.class) {
                    Param paramAnnotation = (Param) annotation;
                    String name = paramAnnotation.value();
                    checkState(emptyToNull(name) != null, "Param annotation was empty on param %s.",
                            paramIndex);
                    nameParam(data, name, paramIndex);
                    Class<? extends Param.Expander> expander = paramAnnotation.expander();
                    if (expander != Param.ToStringExpander.class) {
                        data.indexToExpanderClass().put(paramIndex, expander);
                    }
                    data.indexToEncoded().put(paramIndex, paramAnnotation.encoded());
                    isHttpAnnotation = true;
                    if (!data.template().hasRequestVariable(name)) {
                        data.formParams().add(name);
                    }
                } else if (annotationType == QueryMap.class) {
                    checkState(data.queryMapIndex() == null,
                            "QueryMap annotation was present on multiple parameters.");
                    data.queryMapIndex(paramIndex);
                    data.queryMapEncoded(QueryMap.class.cast(annotation).encoded());
                    isHttpAnnotation = true;
                } else if (annotationType == HeaderMap.class) {
                    checkState(data.headerMapIndex() == null,
                            "HeaderMap annotation was present on multiple parameters.");
                    data.headerMapIndex(paramIndex);
                    isHttpAnnotation = true;
                }
            }
            return isHttpAnnotation;
        }

        /**
         * 解析同名的header
         *
         * @param input
         * @return
         */
        private static Map<String, Collection<String>> toMap(String[] input) {
            Map<String, Collection<String>> result = new LinkedHashMap<String, Collection<String>>(input.length);
            //header已:分隔key和value
            for (String header : input) {
                int colon = header.indexOf(':');
                String name = header.substring(0, colon);
                if (!result.containsKey(name)) {
                    result.put(name, new ArrayList<String>(1));
                }
                result.get(name).add(header.substring(colon + 1).trim());
            }
            return result;
        }
    }
}
