package com.xiaoge.extension.buffer;

import java.util.Collection;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import io.reactivex.ObservableSource;
import io.reactivex.Observer;
import io.reactivex.Scheduler;
import io.reactivex.Scheduler.Worker;
import io.reactivex.disposables.Disposable;
import io.reactivex.exceptions.Exceptions;
import io.reactivex.internal.disposables.DisposableHelper;
import io.reactivex.internal.disposables.EmptyDisposable;
import io.reactivex.internal.functions.ObjectHelper;
import io.reactivex.internal.observers.QueueDrainObserver;
import io.reactivex.internal.queue.MpscLinkedQueue;
import io.reactivex.internal.util.QueueDrainHelper;
import io.reactivex.observers.SerializedObserver;
import io.reactivex.plugins.RxJavaPlugins;


/**
 *
 * 自定义Observable,扩展buffer 操作符的功能
 *
 * 使用三个维度进行消息发送控制
 *
 * debounce维度, 收到消息一定时间没有新消息后触发更新, debounce 导致的延时不超过window
 * window维度,定时更新  window > debounce
 * sizeboundary, 收到指定数量的消息后更新
 *
 * 简化复杂度:
 * 不支持timeSkip,不支持unbounded buffer
 * */

public final class ObservableDebouncedBufferTimed<T, U extends Collection<? super T>>
        extends AbstractObservableWithUpstream<T, U> {

    private final long timeSpan;
    private final TimeUnit unit;
    private final Scheduler scheduler;
    private final Callable<U> bufferSupplier;
    private final int maxSize;
    private final boolean restartTimerOnMaxSize;

    private final long timeout;

    public ObservableDebouncedBufferTimed(ObservableSource<T> source, long timespan, TimeUnit unit, Scheduler scheduler,
                                          Callable<U> bufferSupplier, int maxSize,
                                          boolean restartTimerOnMaxSize, long timeout) {
        super(source);
        this.timeSpan = timespan;
        this.unit = unit;
        this.scheduler = scheduler;
        this.bufferSupplier = bufferSupplier;
        this.maxSize = maxSize;
        this.restartTimerOnMaxSize = restartTimerOnMaxSize;
        this.timeout = timeout;
    }

    @Override
    protected void subscribeActual(Observer<? super U> t) {
        if (maxSize == Integer.MAX_VALUE) {
            throw new IllegalArgumentException("Unbounded buffer not supported yet");
        }
        Worker w = scheduler.createWorker();

        source.subscribe(new DebouncedBufferExactBoundedObserver<T, U>(
                new SerializedObserver<>(t),
                bufferSupplier,
                timeSpan, timeout, unit, maxSize, restartTimerOnMaxSize, w
        ));
    }

    static final class DebouncedBufferExactBoundedObserver<T, U extends Collection<? super T>>
            extends QueueDrainObserver<T, U, U> implements Runnable, Disposable, IEmitter<T>{
        final Callable<U> bufferSupplier;
        final long timespan;
        final long timeout;
        final TimeUnit unit;
        final int maxSize;
        final boolean restartTimerOnMaxSize;
        final Worker w;

        U buffer;

        Disposable timer;

        Disposable s;

        long producerIndex;

        long consumerIndex;

        //extension for debounce
        final AtomicReference<Disposable> disposableTimer = new AtomicReference<Disposable>();
        volatile long index;
        boolean debounceDone;

        DebouncedBufferExactBoundedObserver(
                Observer<? super U> actual,
                Callable<U> bufferSupplier,
                long timespan, long timeout, TimeUnit unit, int maxSize,
                boolean restartOnMaxSize, Worker w) {
            super(actual, new MpscLinkedQueue<U>());
            this.bufferSupplier = bufferSupplier;
            this.timespan = timespan;
            this.timeout = timeout;
            this.unit = unit;
            this.maxSize = maxSize;
            this.restartTimerOnMaxSize = restartOnMaxSize;
            this.w = w;
        }

        @Override
        public void onSubscribe(Disposable s) {
            if (DisposableHelper.validate(this.s, s)) {
                this.s = s;

                U b;

                try {
                    b = ObjectHelper.requireNonNull(bufferSupplier.call(), "The buffer supplied is null");
                } catch (Throwable e) {
                    Exceptions.throwIfFatal(e);
                    s.dispose();
                    EmptyDisposable.error(e, actual);
                    w.dispose();
                    return;
                }

                buffer = b;

                actual.onSubscribe(this);

                timer = w.schedulePeriodically(this, timespan, timespan, unit);
            }
        }

        @Override
        public void onNext(T t) {
            U b;
            synchronized (this) {
                b = buffer;
                if (b == null) {
                    return;
                }

                b.add(t);

                if (b.size() < maxSize) {
                    debounceOnNext(t);
                    return;
                }
                buffer = null;
                producerIndex++;
            }

            if (restartTimerOnMaxSize) {
                timer.dispose();
            }

            debounceOnNext(t);

            fastPathOrderedEmit(b, false, this);

            try {
                b = ObjectHelper.requireNonNull(bufferSupplier.call(), "The buffer supplied is null");
            } catch (Throwable e) {
                Exceptions.throwIfFatal(e);
                actual.onError(e);
                dispose();
                return;
            }

            synchronized (this) {
                buffer = b;
                consumerIndex++;
            }

            if (restartTimerOnMaxSize) {
                timer = w.schedulePeriodically(this, timespan, timespan, unit);
            }
        }

        private void debounceOnNext(T t) {
            if (!done) {
                long idx = index + 1;
                index = idx;

                Disposable d = disposableTimer.get();
                if (d != null) {
                    d.dispose();
                }

                DebounceEmitter<T> de = new DebounceEmitter<T>(idx, this);
                if (disposableTimer.compareAndSet(d, de)) {
                    d = w.schedule(de, timeout, unit);

                    de.setResource(d);
                }
            }
        }

        @Override
        public void onError(Throwable t) {
            synchronized (this) {
                buffer = null;
            }
            actual.onError(t);
            w.dispose();

            if (debounceDone) {
                RxJavaPlugins.onError(t);
                return;
            }

            debounceDone = true;
            DisposableHelper.dispose(disposableTimer);

        }

        @Override
        public void onComplete() {
            w.dispose();

            U b;
            synchronized (this) {
                b = buffer;
                buffer = null;
            }

            queue.offer(b);
            done = true;
            if (enter()) {
                QueueDrainHelper.drainLoop(queue, actual, false, this, this);
            }

            if (debounceDone) {
                return;
            }
            debounceDone = true;
            Disposable d = disposableTimer.get();
            if (d != DisposableHelper.DISPOSED) {
                @SuppressWarnings("unchecked")
                DebounceEmitter<T> de = (DebounceEmitter<T>)d;
                if (de != null) {
                    de.run();
                }
            }

        }

        @Override
        public void accept(Observer<? super U> a, U v) {
            a.onNext(v);
        }


        @Override
        public void dispose() {
            if (!cancelled) {
                cancelled = true;
                s.dispose();
                w.dispose();
                synchronized (this) {
                    buffer = null;
                }
            }
        }

        @Override
        public boolean isDisposed() {
            return cancelled;
        }

        @Override
        public void run() {
            U next;

            try {
                next = ObjectHelper.requireNonNull(bufferSupplier.call(), "The bufferSupplier returned a null buffer");
            } catch (Throwable e) {
                Exceptions.throwIfFatal(e);
                dispose();
                actual.onError(e);
                return;
            }

            U current;

            synchronized (this) {
                current = buffer;
                if (current == null || producerIndex != consumerIndex) {
                    return;
                }
                buffer = next;
            }

            fastPathOrderedEmit(current, false, this);
        }

        @Override
        public void emit(long idx, DebounceEmitter<T> emitter) {
            if (idx == index) {

                timer.dispose();

                run();

                timer = w.schedulePeriodically(this, timespan, timespan, unit);
                emitter.dispose();
            }
        }
    }

    static final class DebounceEmitter<T> extends AtomicReference<Disposable> implements Runnable, Disposable {

        private static final long serialVersionUID = 6812032969491025141L;

        final long idx;
        final IEmitter<T> parent;

        final AtomicBoolean once = new AtomicBoolean();

        DebounceEmitter(long idx, IEmitter<T> parent) {
            this.idx = idx;
            this.parent = parent;
        }

        @Override
        public void run() {
            if (once.compareAndSet(false, true)) {
                parent.emit(idx, this);
            }
        }

        @Override
        public void dispose() {
            DisposableHelper.dispose(this);
        }

        @Override
        public boolean isDisposed() {
            return get() == DisposableHelper.DISPOSED;
        }

        public void setResource(Disposable d) {
            DisposableHelper.replace(this, d);
        }
    }

    private interface IEmitter<T> {
        void emit(long idx, DebounceEmitter<T> emitter);
    }

}
