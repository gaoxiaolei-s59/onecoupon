package org.puregxl.distribution.mq.base;


import lombok.*;

@Builder
@NoArgsConstructor
@AllArgsConstructor
@Data
@RequiredArgsConstructor
public class MessageWrapper<T> {


    @NonNull
    private String keys;

    /**
     * 消息体
     */
    @NonNull
    private T message;

    /**
     * 消息发送时间
     */
    private Long timestamp = System.currentTimeMillis();
}
