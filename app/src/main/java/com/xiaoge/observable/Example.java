package com.xiaoge.observable;

import android.text.TextUtils;
import android.util.ArrayMap;
import android.util.Log;

import com.xiaoge.extension.buffer.ObservableDebouncedBufferTimed;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import io.reactivex.Observable;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.CompositeDisposable;
import io.reactivex.functions.Consumer;
import io.reactivex.internal.functions.ObjectHelper;
import io.reactivex.internal.util.ArrayListSupplier;
import io.reactivex.plugins.RxJavaPlugins;
import io.reactivex.schedulers.Schedulers;
import io.reactivex.subjects.PublishSubject;

public class Example {

    private CompositeDisposable disposable = new CompositeDisposable();

    public void example() {
        PublishSubject<Integer> source = PublishSubject.create();
        source.subscribeOn(Schedulers.single()).observeOn(AndroidSchedulers.mainThread()).onTerminateDetach();
        Observable<List<Integer>> observableBuffer = debounceBuffer(source, 3000, TimeUnit.MILLISECONDS, 100, 500);
        disposable.add(observableBuffer
                .observeOn(Schedulers.io())
                .onTerminateDetach()
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(new Consumer<List<Integer>>() {
                    @Override
                    public void accept(List<Integer> list) throws Exception {
                        //data process
                    }
                }, new Consumer<Throwable>() {
                    @Override
                    public void accept(Throwable throwable) throws Exception {
                        //data process
                    }
                }));

    }



    /**
     * @param source   observableSource
     * @param timeSpan the period of time each buffer collects items before it is emitted and replaced with a new
     *                 buffer
     * @param unit     the unit of time which applies to the {@code timespan} and  {@code timeout}argument
     * @param count    the maximum size of each buffer before it is emitted
     * @param timeout  the time each item has to be "the most recent" of those emitted by the source ObservableSource to
     *                 ensure that it's not dropped like debounce
     */
    private Observable<List<Integer>> debounceBuffer(Observable source, long timeSpan, TimeUnit unit,
                                                     int count, long timeout) {
        ObjectHelper.requireNonNull(source, "source is null");
        ObjectHelper.requireNonNull(unit, "unit is null");

        if (ObjectHelper.compare(timeSpan, timeout) <= 0) {
            throw new IllegalArgumentException("timespan(" + timeSpan + ") > timeout(" + timeout + ") required but it was not.");
        }
        return RxJavaPlugins.onAssembly(new ObservableDebouncedBufferTimed<>(source,
                timeSpan, unit, Schedulers.computation(),
                ArrayListSupplier.<Integer>asCallable(), count,
                true, timeout));
    }

}
