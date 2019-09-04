package com;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ConcurrentMap;
/**
 * 从socket.io 的android实现版本截取过来,实现事件驱动
 * The event emitter which is ported from the JavaScript module. This class is thread-safe.
 *
 * @see <a href="https://github.com/component/emitter">https://github.com/component/emitter</a>
 */
public class Emitter {

    private ConcurrentMap<String, ConcurrentLinkedQueue<Listener>> callbacks
            = new ConcurrentHashMap<String, ConcurrentLinkedQueue<Listener>>();

    /**
     * Listens on the event.
     * @param event event name.
     * @param fn
     * @return a reference to this object.
     */
    public Emitter on(String event, Listener fn) {
        ConcurrentLinkedQueue<Listener> callbacks = this.callbacks.get(event);
        if (callbacks == null) {
            callbacks = new ConcurrentLinkedQueue <Listener>();
            ConcurrentLinkedQueue<Listener> _callbacks = this.callbacks.putIfAbsent(event, callbacks);
            if (_callbacks != null) {
                callbacks = _callbacks;
            }
        }
        callbacks.add(fn);
        return this;
    }



    /**
     * Executes each of listeners with the given args.
     *
     * @param event an event name.
     * @param args
     * @return a reference to this object.
     */
    public Emitter emit(String event, Object... args) {
        ConcurrentLinkedQueue<Listener> callbacks = this.callbacks.get(event);
        if (callbacks != null) {
            for (Listener fn : callbacks) {
                fn.call(args);
            }
        }
        return this;
    }


    public static interface Listener {

        public void call(Object... args);
    }

}
