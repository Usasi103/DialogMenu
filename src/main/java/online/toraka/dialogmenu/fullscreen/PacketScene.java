package online.toraka.dialogmenu.fullscreen;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.protocol.game.ClientboundBundlePacket;
import net.minecraft.network.protocol.game.ClientboundRemoveEntitiesPacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Interaction;
import net.minecraft.world.phys.Vec3;

import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.bukkit.entity.Display;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Entities exist only as packet construction objects; never added to a ServerLevel. */
final class PacketScene {
    private final ServerPlayer owner;
    private final List<Entity> entities = new ArrayList<>();
    private final Map<String, TextDisplay> buttons = new LinkedHashMap<>();
    private final Vector3f right;
    private final Vector3f normal;
    private final Location origin;
    private final float pixelsPerBlock;
    final Entity camera;
    final int captureId;
    private final TextDisplay cursor;
    private final TextDisplay status;
    private PointerState.Point lastPoint;
    private String hover;
    private boolean shown;
    private boolean dirty;
    private String statusValue;
    private long updates;
    private final boolean local;

    PacketScene(Player player, float pixelsPerBlock, boolean local) {
        this.owner = ((CraftPlayer) player).getHandle();
        this.local = local;
        this.pixelsPerBlock = pixelsPerBlock;
        Location eye = player.getEyeLocation();
        // Spawn yaw is byte-quantized. Use exactly the same angle for the whole plane.
        float yaw = (float) (Math.floor(eye.getYaw() * 256.0 / 360.0) * 360.0 / 256.0);
        double radians = Math.toRadians(yaw);
        Vector3f forward = new Vector3f((float) -Math.sin(radians), 0, (float) Math.cos(radians));
        normal = new Vector3f(forward).negate();
        right = new Vector3f(0, 1, 0).cross(normal);
        origin = eye.clone().add(forward.x * 0.8, 0, forward.z * 0.8);

        if (local) {
            // Fixed world axes; the resource pack recovers camera rotation from this plane.
            var canvas = createText(eye.clone().add(0, 0, 0.8));
            canvas.text(
                    Component.text(DemoLayout.canvasGlyphs())
                            .font(net.kyori.adventure.key.Key.key("dialogmenu_fullscreen:canvas"))
                            .color(net.kyori.adventure.text.format.TextColor.color(0xfd17ab)));
            // Depth must be written; see-through text lets later clouds/entities cover the GUI.
            canvas.setSeeThrough(false);
            canvas.setTransformationMatrix(new Matrix4f().scale(0.1f));
            camera = owner;
            cursor = null;
            status = null;
        } else {
            var cameraDisplay = createText(eye);
            camera = handle(cameraDisplay);
            camera.setYRot(yaw);
            camera.setXRot(0);
            TextDisplay backdrop = rectangle(0, 0, 6000, 4000, -2, 0xff101722);
            backdrop.setViewRange(2);
            text("DialogMenu 全屏交互测试", 0, 45, 1.3f, 1);
            text("移动鼠标 · 点击按钮 · Shift / F 退出", 0, -87, 0.72f, 1);
            for (var button : DemoLayout.BUTTONS) {
                buttons.put(
                        button.id(),
                        rectangle(
                                button.x(),
                                button.y(),
                                button.width(),
                                button.height(),
                                0,
                                0xff29415d));
                text(button.label(), button.x(), button.y() - 4, 1, 1);
            }
            status = text("等待客户端确认…", 0, 25, 0.8f, 1);
            cursor = text("+", 0, -4, 1, 3);
            cursor.text(Component.text("+", NamedTextColor.YELLOW));
        }

        // The wide box includes the camera, matching vanilla's inside-box ray handling.
        var capture = new Interaction(EntityTypes.INTERACTION, owner.level());
        capture.setPos(origin.getX(), eye.getY() - 3, origin.getZ());
        capture.setWidth(6);
        capture.setHeight(6);
        capture.setResponse(true);
        entities.add(capture);
        captureId = capture.getId();
    }

    private TextDisplay createText(Location at) {
        var entity =
                new net.minecraft.world.entity.Display.TextDisplay(
                        EntityTypes.TEXT_DISPLAY, owner.level());
        entity.setPos(at.getX(), at.getY(), at.getZ());
        entity.setYRot(0);
        entity.setXRot(0);
        entities.add(entity);
        var text = (TextDisplay) entity.getBukkitEntity();
        text.text(Component.empty());
        text.setBillboard(Display.Billboard.FIXED);
        text.setBrightness(new Display.Brightness(15, 15));
        text.setDefaultBackground(false);
        text.setBackgroundColor(Color.fromARGB(0));
        text.setShadowed(false);
        text.setLineWidth(2000);
        text.setAlignment(TextDisplay.TextAlignment.CENTER);
        text.setInterpolationDuration(0);
        text.setTeleportDuration(0);
        text.setGravity(false);
        text.setViewRange(1);
        return text;
    }

    private Matrix4f at(double x, double y, double depth) {
        return new Matrix4f()
                .m00(right.x)
                .m01(right.y)
                .m02(right.z)
                .m10(0)
                .m11(1)
                .m12(0)
                .m20(normal.x)
                .m21(normal.y)
                .m22(normal.z)
                .scale(1 / pixelsPerBlock)
                .translate((float) x, (float) y, (float) depth);
    }

    private TextDisplay text(String value, double x, double y, float size, double depth) {
        TextDisplay display = createText(origin);
        display.text(Component.text(value, NamedTextColor.WHITE));
        display.setTransformationMatrix(at(x, y, depth).scale(40 * size));
        return display;
    }

    private TextDisplay rectangle(
            double x, double y, double width, double height, double depth, int color) {
        TextDisplay display = createText(origin);
        display.text(Component.space());
        display.setBackgroundColor(Color.fromARGB(color));
        // One-space background calibration from ArcMenu / FluxUI; see THIRD_PARTY_NOTICES.
        // Logical bounds remain independent and require a real-client visual alignment check.
        display.setTransformationMatrix(
                at(x - width / 2, y - height / 2, depth)
                        .scale((float) width, (float) height, 1)
                        .translate(0.4f, 0, 0)
                        .scale(8.08f, 3.66f, 1));
        return display;
    }

    void show() {
        List<Packet<? super ClientGamePacketListener>> packets = new ArrayList<>();
        for (Entity entity : entities) {
            packets.add(
                    new ClientboundAddEntityPacket(
                            entity.getId(),
                            entity.getUUID(),
                            entity.getX(),
                            entity.getY(),
                            entity.getZ(),
                            entity.getXRot(),
                            entity.getYRot(),
                            entity.getType(),
                            0,
                            Vec3.ZERO,
                            entity.getYRot()));
            var data = entity.getEntityData().getNonDefaultValues();
            if (data != null) packets.add(new ClientboundSetEntityDataPacket(entity.getId(), data));
            entity.getEntityData().packDirty();
        }
        owner.connection.send(new ClientboundBundlePacket(packets));
        shown = true;
        dirty = false;
    }

    void pointer(PointerState.Point point) {
        if (local) return;
        if (!point.equals(lastPoint)) {
            cursor.setTransformationMatrix(at(point.x(), point.y() - 4, 3).scale(40));
            lastPoint = point;
            dirty = true;
        }
        var target = DemoLayout.hit(point);
        String id = target == null ? null : target.id();
        if (!java.util.Objects.equals(hover, id)) {
            if (hover != null) buttons.get(hover).setBackgroundColor(Color.fromARGB(0xff29415d));
            if (id != null) buttons.get(id).setBackgroundColor(Color.fromARGB(0xff4278a8));
            hover = id;
            dirty = true;
        }
    }

    void status(String value) {
        if (local) return;
        if (value.equals(statusValue)) return;
        statusValue = value;
        status.text(Component.text(value, NamedTextColor.AQUA));
        dirty = true;
    }

    void flush() {
        if (!dirty) return;
        dirty = false;
        List<Packet<? super ClientGamePacketListener>> packets = new ArrayList<>();
        for (Entity entity : entities) {
            var dirty = entity.getEntityData().packDirty();
            if (dirty != null)
                packets.add(new ClientboundSetEntityDataPacket(entity.getId(), dirty));
        }
        if (!packets.isEmpty()) {
            updates += packets.size();
            owner.connection.send(new ClientboundBundlePacket(packets));
        }
    }

    void remove() {
        if (shown) {
            owner.connection.send(
                    new ClientboundRemoveEntitiesPacket(
                            entities.stream().mapToInt(Entity::getId).toArray()));
            shown = false;
        }
        entities.clear();
        buttons.clear();
    }

    int entityCount() {
        return entities.size();
    }

    long updates() {
        return updates;
    }

    private static Entity handle(TextDisplay display) {
        return ((org.bukkit.craftbukkit.entity.CraftEntity) display).getHandle();
    }
}
