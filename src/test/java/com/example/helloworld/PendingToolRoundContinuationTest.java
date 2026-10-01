package com.example.helloworld;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PendingToolRoundContinuationTest {

    @Test
    void resumesOnlyOnceAfterActionAndCommandBothComplete_ActionFirst() {
        List<String> continuations = new ArrayList<>();
        PendingToolRoundContinuation group = new PendingToolRoundContinuation(continuations::add);

        group.seal(2);
        group.record("位置查询结果：-697, 37, 310");
        assertEquals(List.of(), continuations);

        group.record("已执行建议命令：/gamemode creative");
        group.record("不应触发第二次续跑");

        assertEquals(List.of("位置查询结果：-697, 37, 310\n\n已执行建议命令：/gamemode creative"), continuations);
    }

    @Test
    void buffersEarlyCommandResultUntilGroupIsSealed_CommandFirst() {
        List<String> continuations = new ArrayList<>();
        PendingToolRoundContinuation group = new PendingToolRoundContinuation(continuations::add);

        // 命令可以在 process() 返回、调用方得知总挂起数量之前被玩家立即执行。
        group.record("已执行建议命令：/gamemode creative");
        assertEquals(List.of(), continuations);

        group.seal(2);
        assertEquals(List.of(), continuations);

        group.record("查询玩家结果：-697, 37, 310");
        assertEquals(List.of("已执行建议命令：/gamemode creative\n\n查询玩家结果：-697, 37, 310"), continuations);
    }
}
