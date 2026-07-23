package com.bot.aibot.network.packet;

import com.bot.aibot.client.ClientPacketHandler;
import com.bot.aibot.network.PacketStrings;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * 服务端 -> 客户端
 * 统一音乐指令包：包含播放、搜索、控制、GUI操作等所有指令
 */
public class S2CMusicCommandPacket {

    // 定义指令动作枚举
    public enum Action {
        PLAY_DIRECT,    // 直接播放 (参数: URL)
        STOP,           // 停止/暂停 (参数: 无)
        SEARCH_AND_PLAY,// 搜索并播放 (参数: 关键词)
        OPEN_GUI,       // 打开播放器界面 (参数: 无)
        PLAY_MY_LIKE,
        RESET_COOLDOWN// 【新增】随机播放我的红心歌单 (参数: 无)
    }

    private final Action action;
    private final String data;   // 泛用数据字段 (URL, 关键词, 或者空)
    private final long extra;    // 额外数据 (如时长, 或者是 bool 标志位)

    // 构造函数 1: 基础指令
    public S2CMusicCommandPacket(Action action) {
        this(action, "", 0);
    }

    // 构造函数 2: 带数据的指令 (如搜索)
    public S2CMusicCommandPacket(Action action, String data) {
        this(action, data, 0);
    }

    // 全参构造
    public S2CMusicCommandPacket(Action action, String data, long extra) {
        this.action = action;
        this.data = data;
        this.extra = extra;
    }

    // 解码 (从 ByteBuf 读取)
    public S2CMusicCommandPacket(FriendlyByteBuf buf) {
        int actionId = buf.readVarInt();
        if (actionId < 0 || actionId >= Action.values().length) throw new IllegalArgumentException("Invalid music action");
        this.action = Action.values()[actionId];
        this.data = PacketStrings.readUtf8(buf, 2048, 2048);
        this.extra = buf.readLong();
        validateFields();
    }

    // 编码 (写入 ByteBuf)
    public void encode(FriendlyByteBuf buf) {
        validateFields();
        buf.writeVarInt(this.action.ordinal());
        PacketStrings.writeUtf8(buf, this.data, 2048, 2048);
        buf.writeLong(this.extra);
    }

    // 处理逻辑 (转发给 ClientPacketHandler)
    public static void handle(S2CMusicCommandPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> ClientPacketHandler.handle(msg.action, msg.data, msg.extra)));
        ctx.get().setPacketHandled(true);
    }

    public Action getAction() {
        return action;
    }

    public String getData() {
        return data;
    }

    public long getExtra() {
        return extra;
    }

    private void validateFields() {
        if (action == null || data == null || data.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 2048)
            throw new IllegalArgumentException("Invalid music command");
        if (action == Action.PLAY_DIRECT && (data.isEmpty() || extra <= 0 || extra > 86_400_000L))
            throw new IllegalArgumentException("Invalid direct-play command");
        if ((action == Action.SEARCH_AND_PLAY || action == Action.PLAY_MY_LIKE)
                && data.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 256)
            throw new IllegalArgumentException("Music command data too long");
        if (action == Action.SEARCH_AND_PLAY && extra != 0 && extra != 1)
            throw new IllegalArgumentException("Invalid search mode");
        if (action != Action.PLAY_DIRECT && action != Action.SEARCH_AND_PLAY && extra != 0)
            throw new IllegalArgumentException("Unexpected music command data");
    }
}
