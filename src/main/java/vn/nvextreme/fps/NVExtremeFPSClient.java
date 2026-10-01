package vn.nvextreme.fps;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.particle.ParticlesMode;

public class NVExtremeFPSClient implements ClientModInitializer {

    // =========================================================
    // CORE LIMITS
    // =========================================================

    // Không bao giờ tự giảm Render Distance dưới 6 chunks
    private static final int MIN_VIEW_DISTANCE = 6;

    // Mức tối đa NV Extreme FPS tự phục hồi
    private static final int MAX_VIEW_DISTANCE = 8;

    // Simulation Distance
    private static final int MIN_SIMULATION_DISTANCE = 4;
    private static final int MAX_SIMULATION_DISTANCE = 6;

    // Entity rendering
    private static final double LOW_ENTITY_DISTANCE = 0.50D;
    private static final double NORMAL_ENTITY_DISTANCE = 0.75D;

    // =========================================================
    // FAST BOOT
    // =========================================================

    /*
     * NV Extreme FPS không làm xử lý nặng trong lúc Minecraft
     * đang khởi động.
     *
     * Không:
     * - tạo thread riêng
     * - scan toàn bộ mod
     * - kiểm tra RAM liên tục
     * - kiểm tra FPS liên tục
     * - gọi System.gc()
     * - thay đổi graphics settings lúc boot
     *
     * Optimizer chỉ bắt đầu sau khi Minecraft đã tới
     * màn hình chờ / main menu.
     */

    private static final int MENU_DELAY_TICKS = 40;

    private int menuTicks = 0;
    private int optimizationTicks = 0;

    private int lowFpsTicks = 0;
    private int goodFpsTicks = 0;

    private boolean optimizerStarted = false;

    // =========================================================
    // INITIALIZE
    // =========================================================

    @Override
    public void onInitializeClient() {

        /*
         * Chỉ đăng ký một client tick callback.
         *
         * Đây là phần duy nhất được thiết lập khi mod load.
         */
        ClientTickEvents.END_CLIENT_TICK.register(
                this::onClientTick
        );
    }

    // =========================================================
    // CLIENT TICK
    // =========================================================

    private void onClientTick(MinecraftClient client) {

        // =====================================================
        // FAST BOOT PHASE
        // =====================================================

        if (!optimizerStarted) {

            /*
             * Chưa tới màn hình chờ:
             * thoát ngay lập tức.
             */
            if (client.currentScreen == null) {
                return;
            }

            /*
             * Minecraft đã có màn hình.
             *
             * Đợi thêm một khoảng ngắn để quá trình startup
             * ổn định trước khi bắt đầu optimizer.
             */
            menuTicks++;

            if (menuTicks < MENU_DELAY_TICKS) {
                return;
            }

            optimizerStarted = true;

            System.out.println(
                    "[NV Extreme FPS] Fast Boot complete."
            );

            System.out.println(
                    "[NV Extreme FPS] Adaptive optimizer started."
            );

            return;
        }

        // =====================================================
        // OPTIMIZER
        // =====================================================

        optimizationTicks++;

        /*
         * Chỉ kiểm tra 1 lần mỗi 2 giây.
         *
         * Không xử lý nặng ở từng tick.
         */
        if (optimizationTicks < 40) {
            return;
        }

        optimizationTicks = 0;

        optimize(client);
    }

    // =========================================================
    // MAIN OPTIMIZER
    // =========================================================

    private void optimize(MinecraftClient client) {

        /*
         * Nếu đang ở menu thì không cần tối ưu FPS gameplay.
         */
        if (client.player == null) {
            return;
        }

        int fps = client.getCurrentFps();

        if (fps <= 0) {
            return;
        }

        // =====================================================
        // RAM MONITOR
        // =====================================================

        Runtime runtime = Runtime.getRuntime();

        long maxMemory = runtime.maxMemory();
        long totalMemory = runtime.totalMemory();
        long freeMemory = runtime.freeMemory();

        long usedMemory = totalMemory - freeMemory;

        double memoryUsage =
                (double) usedMemory / (double) maxMemory;

        boolean memoryHigh = memoryUsage >= 0.85D;
        boolean memoryCritical = memoryUsage >= 0.92D;

        // =====================================================
        // FPS STATE
        // =====================================================

        boolean fpsLow = fps < 25;
        boolean fpsCritical = fps < 18;

        if (fpsLow || memoryHigh) {

            lowFpsTicks++;
            goodFpsTicks = 0;

        } else if (fps >= 50 && !memoryHigh) {

            goodFpsTicks++;
            lowFpsTicks = 0;

        } else {

            lowFpsTicks = 0;
            goodFpsTicks = 0;
        }

        // =====================================================
        // EMERGENCY MODE
        // =====================================================

        if (memoryCritical || fpsCritical) {

            emergencyMode(client);

            return;
        }

        // =====================================================
        // LOW FPS MODE
        // =====================================================

        if (lowFpsTicks >= 2) {

            lowFpsMode(client);

            lowFpsTicks = 0;

            return;
        }

        // =====================================================
        // RECOVERY MODE
        // =====================================================

        if (goodFpsTicks >= 5) {

            recoveryMode(client);

            goodFpsTicks = 0;
        }
    }

    // =========================================================
    // EMERGENCY MODE
    // =========================================================

    private void emergencyMode(MinecraftClient client) {

        // Render Distance >= 6
        if (client.options.getViewDistance().getValue()
                > MIN_VIEW_DISTANCE) {

            client.options.getViewDistance()
                    .setValue(MIN_VIEW_DISTANCE);
        }

        // Simulation Distance >= 4
        if (client.options.getSimulationDistance().getValue()
                > MIN_SIMULATION_DISTANCE) {

            client.options.getSimulationDistance()
                    .setValue(MIN_SIMULATION_DISTANCE);
        }

        // Entity rendering
        if (client.options.getEntityDistanceScaling().getValue()
                > LOW_ENTITY_DISTANCE) {

            client.options.getEntityDistanceScaling()
                    .setValue(LOW_ENTITY_DISTANCE);
        }

        // GPU load
        client.options.getEntityShadows()
                .setValue(false);

        // Particle load
        client.options.getParticles()
                .setValue(ParticlesMode.MINIMAL);
    }

    // =========================================================
    // LOW FPS MODE
    // =========================================================

    private void lowFpsMode(MinecraftClient client) {

        int viewDistance =
                client.options.getViewDistance().getValue();

        int simulationDistance =
                client.options.getSimulationDistance().getValue();

        double entityDistance =
                client.options.getEntityDistanceScaling().getValue();

        // -----------------------------------------------------
        // Render Distance
        // -----------------------------------------------------

        if (viewDistance > MIN_VIEW_DISTANCE) {

            client.options.getViewDistance()
                    .setValue(
                            Math.max(
                                    MIN_VIEW_DISTANCE,
                                    viewDistance - 1
                            )
                    );
        }

        // -----------------------------------------------------
        // Simulation Distance
        // -----------------------------------------------------

        if (simulationDistance > MIN_SIMULATION_DISTANCE) {

            client.options.getSimulationDistance()
                    .setValue(
                            Math.max(
                                    MIN_SIMULATION_DISTANCE,
                                    simulationDistance - 1
                            )
                    );
        }

        // -----------------------------------------------------
        // Entity Distance
        // -----------------------------------------------------

        if (entityDistance > LOW_ENTITY_DISTANCE) {

            client.options.getEntityDistanceScaling()
                    .setValue(LOW_ENTITY_DISTANCE);
        }

        // -----------------------------------------------------
        // GPU
        // -----------------------------------------------------

        client.options.getEntityShadows()
                .setValue(false);

        // -----------------------------------------------------
        // Particles
        // -----------------------------------------------------

        client.options.getParticles()
                .setValue(ParticlesMode.MINIMAL);
    }

    // =========================================================
    // RECOVERY MODE
    // =========================================================

    private void recoveryMode(MinecraftClient client) {

        int viewDistance =
                client.options.getViewDistance().getValue();

        int simulationDistance =
                client.options.getSimulationDistance().getValue();

        double entityDistance =
                client.options.getEntityDistanceScaling().getValue();

        // -----------------------------------------------------
        // Render Distance
        // -----------------------------------------------------

        if (viewDistance < MAX_VIEW_DISTANCE) {

            client.options.getViewDistance()
                    .setValue(viewDistance + 1);
        }

        // -----------------------------------------------------
        // Simulation Distance
        // -----------------------------------------------------

        if (simulationDistance < MAX_SIMULATION_DISTANCE) {

            client.options.getSimulationDistance()
                    .setValue(simulationDistance + 1);
        }

        // -----------------------------------------------------
        // Entity Distance
        // -----------------------------------------------------

        if (entityDistance < NORMAL_ENTITY_DISTANCE) {

            client.options.getEntityDistanceScaling()
                    .setValue(NORMAL_ENTITY_DISTANCE);
        }

        /*
         * Shadow vẫn giữ OFF để ưu tiên GPU/FPS.
         */
        client.options.getEntityShadows()
                .setValue(false);

        /*
         * Particle vẫn giữ Minimal để tránh tải GPU/CPU
         * không cần thiết.
         */
        client.options.getParticles()
                .setValue(ParticlesMode.MINIMAL);
    }
}
