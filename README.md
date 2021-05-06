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
