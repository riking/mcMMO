package com.gmail.nossr50.runnables;

import com.google.common.base.Function;

public class Callback<T> implements Runnable {
    Function<T, ?> callback;
    T object;

    public Callback(Function<T, ?> callback, T object) {
        this.callback = callback;
        this.object = object;
    }

    @Override
    public void run() {
        callback.apply(object);
    }
}
