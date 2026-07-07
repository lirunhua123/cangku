package com.sky.context;
//这里封装是为了提高代码可读性和可维护性，这里可以直接不需要这个包，这届用ThreadLocal.get.set.remove方法
public class BaseContext {

    public static ThreadLocal<Long> threadLocal = new ThreadLocal<>();

    public static void setCurrentId(Long id) {
        threadLocal.set(id);
    }

    public static Long getCurrentId() {
        return threadLocal.get();
    }

    public static void removeCurrentId() {
        threadLocal.remove();
    }

}
