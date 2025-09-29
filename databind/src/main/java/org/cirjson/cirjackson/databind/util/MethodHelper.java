package org.cirjson.cirjackson.databind.util;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

public final class MethodHelper {

    @Nullable
    public static Object callOn(@NotNull Method method,
            @Nullable Object pojo) throws IllegalAccessException, IllegalArgumentException, InvocationTargetException {
        return method.invoke(pojo, (Object[]) null);
    }

}
