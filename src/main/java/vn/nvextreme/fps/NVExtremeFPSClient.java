package vn.nvextreme.fps;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.particle.ParticlesMode;

public class NVExtremeFPSClient implements ClientModInitializer {

    private static final int MIN_RENDER_DISTANCE = 6;
    private static final int MAX_RENDER_DISTANCE = 12;

    private int tickCounter = 0;

    @Override
    public void onInitializeClient() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            tickCounter++;

            // Kiểm tra mỗi 2 giây
            if (tickCounter < 40) {
                return;
            }

            tickCounter = 0;
            optimize(client);
        });

        System.out.println("[NV Extreme FPS] Optimizer enabled!");
    }

    private void optimize(MinecraftClient client) {
        if (client.player == null) {
            return;
        }

        int fps = client.getCurrentFps();
        int renderDistance = client.options.getViewDistance().getValue();

        // FPS thấp: giảm render distance nhưng không dưới 6 chunks
        if (fps > 0 && fps < 25) {
            if (renderDistance > MIN_RENDER_DISTANCE) {
                client.options.getViewDistance()
                        .setValue(Math.max(MIN_RENDER_DISTANCE, renderDistance - 1));
            }

            client.options.getEntityShadows().setValue(false);
            client.options.getParticles().setValue(ParticlesMode.MINIMAL);
        }

        // FPS ổn: tăng render distance trở lại
        else if (fps >= 50 && renderDistance < MAX_RENDER_DISTANCE) {
            client.options.getViewDistance().setValue(renderDistance + 1);
        }
    }
}
