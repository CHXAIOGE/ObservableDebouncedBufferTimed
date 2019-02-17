# ObservableDebouncedBufferTimed

RxJava buffer operator extension

自定义Observable,扩展buffer 操作符的功能

使用三个维度进行消息发送控制

debounce维度, 收到消息一定时间没有新消息后触发更新, debounce 导致的延时不超过window

window维度,定时更新  window > debounce

sizeboundary, 收到指定数量的消息后更新

简化复杂度:

不支持timeSkip,不支持unbounded buffer
