package com.app.androidlab.sample.tool;

import java.lang.reflect.Field;

public class ReflectionUtils {

    /**
     * 获取对象中指定私有属性的值。
     *
     * @param object 对象实例
     * @param fieldName 要获取的私有属性名称
     * @return 私有属性的值，如果出现异常则返回null
     */
    public static Object getPrivateFieldValue(Object object, String fieldName) {
        try {
            // 获取对象的类类型
            Class<?> clazz = object.getClass();

            // 获取指定的私有字段
            Field field = clazz.getDeclaredField(fieldName);

            // 确保私有字段可以被访问
            field.setAccessible(true);

            // 返回私有字段的值
            return field.get(object);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            e.printStackTrace();
            return null;
        }
    }
}
