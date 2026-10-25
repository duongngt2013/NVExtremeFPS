package vn.nvextreme.fps;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.particle.ParticlesMode;

public final class NVExtremeFPSClient implements ClientModInitializer {

    private static final int CHECK_INTERVAL_TICKS = 40;
    private static final int LOW_FPS_THRESHOLD = 18;
    private static final int CRITICAL_FPS_THRESHOLD = 10;
    private static final int RECOVERY_FPS_THRESHOLD = 35;

    private static final double HIGH_MEMORY_THRESHOLD = 0.85D;
    private static final double CRITICAL_MEMORY_THRESHOLD = 0.92D;

    private static final double LOW_ENTITY_DISTANCE = 0.50D;

    private int menuTicks;
    private int checkTicks;
    private int lowPerformanceChecks;
    private int recoveryChecks;

    private boolean optimizerStarted;
    private boolean lowModeApplied;

    @Override
    public void onInitializeClient() {
        ClientTickEvents.END_CLIENT_TICK.register(this::onClientTick);
    }

    private void onClientTick(MinecraftClient client) {
        if (!optimizerStarted) {
            if (client.currentScreen == null) {
                return;
            }

            if (++menuTicks < CHECK_INTERVAL_TICKS) {
                return;
            }

            optimizerStarted = true;
            System.out.println("[NVExtremeFPS] Adaptive optimizer started.");
            return;
        }

        if (++checkTicks < CHECK_INTERVAL_TICKS) {
            return;
        }

        checkTicks = 0;
        optimize(client);
    }

    private void optimize(MinecraftClient client) {
        if (client.player == null || client.world == null) {
            lowPerformanceChecks = 0;
            recoveryChecks = 0;
            return;
        }

        int fps = client.getCurrentFps();

        if (fps <= 0) {
            return;
        }

        Runtime runtime = Runtime.getRuntime();

        long maxMemory = runtime.maxMemory();
        long usedMemory = runtime.totalMemory() - runtime.freeMemory();

        double memoryUsage = maxMemory > 0
                ? (double) usedMemory / maxMemory
                : 0.0D;

        boolean memoryHigh = memoryUsage >= HIGH_MEMORY_THRESHOLD;
        boolean memoryCritical = memoryUsage >= CRITICAL_MEMORY_THRESHOLD;

        boolean performanceLow =
                fps < LOW_FPS_THRESHOLD || memoryHigh;

        boolean performanceCritical =
                fps < CRITICAL_FPS_THRESHOLD || memoryCritical;

        if (performanceLow) {
            lowPerformanceChecks++;
            recoveryChecks = 0;
        } else if (fps >= RECOVERY_FPS_THRESHOLD && !memoryHigh) {
            recoveryChecks++;
            lowPerformanceChecks = 0;
        } else {
            lowPerformanceChecks = 0;
            recoveryChecks = 0;
        }

        if (performanceCritical || lowPerformanceChecks >= 2) {
            applyLowPerformanceSettings(client);
            lowModeApplied = true;
            lowPerformanceChecks = 0;
            recoveryChecks = 0;
            return;
        }

        // Không tự tăng Render Distance hoặc Simulation Distance.
        // Chỉ phục hồi những thiết lập mà mod đã thay đổi.
        if (lowModeApplied && recoveryChecks >= 5) {
            recoveryChecks = 0;
            lowModeApplied = false;

            System.out.println(
                    "[NVExtremeFPS] Performance stabilized. "
                    + "Keeping conservative distances."
            );
        }
    }

    private void applyLowPerformanceSettings(MinecraftClient client) {
        // Tôn trọng Render Distance hiện tại của người chơi.
        // Chỉ giảm nếu lớn hơn 2 chunks.
        int viewDistance = client.options.getViewDistance().getValue();

        if (viewDistance > 2) {
            client.options.getViewDistance().setValue(2);
        }

        // Không tự tăng Simulation Distance.
        int simulationDistance =
                client.options.getSimulationDistance().getValue();

        if (simulationDistance > 2) {
            client.options.getSimulationDistance().setValue(2);
        }

        if (client.options.getEntityDistanceScaling().getValue()
                > LOW_ENTITY_DISTANCE) {
            client.options.getEntityDistanceScaling()
                    .setValue(LOW_ENTITY_DISTANCE);
        }

        client.options.getEntityShadows().setValue(false);
        client.options.getParticles().setValue(ParticlesMode.MINIMAL);
    }
}
