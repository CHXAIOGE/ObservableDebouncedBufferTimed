# ObservableDebouncedBufferTimed

RxJava buffer operator extension

More flexible for certain condition like { Messages Receive And Update UI}

Use Three Dimension for Events control

(1) debounce

Just like timeout. which emit data if no more event buffered.

收到消息一定时间没有新消息后触发更新, debounce 导致的延时不超过window

(2) window

emit data with a certain time,

[can be reset by 【debounce】 or 【sizeBoundary】 emits]

定时更新  window > debounce

(3) sizeBoundary

emit data if reach limit

收到指定数量的消息后更新,达到存储上限

Just for Simplify:

[Unsupported Operations]

timeSkip, unbounded buffer

<b>API Usage:</b>

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
