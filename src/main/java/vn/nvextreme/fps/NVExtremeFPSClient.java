package vn.nvextreme.fps;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.particle.ParticlesMode;

public class NVExtremeFPSClient implements ClientModInitializer {

    // ===== GIỚI HẠN =====

    // Không bao giờ giảm dưới 6 chunk
    private static final int MIN_VIEW_DISTANCE = 6;

    // Mức tối đa NV Extreme FPS tự nâng lên
    private static final int MAX_VIEW_DISTANCE = 8;

    // Simulation distance ưu tiên CPU
    private static final int MIN_SIMULATION_DISTANCE = 4;
    private static final int MAX_SIMULATION_DISTANCE = 6;

    // Entity distance scaling
    private static final double LOW_ENTITY_DISTANCE = 0.50D;
    private static final double NORMAL_ENTITY_DISTANCE = 0.75D;

    // ===== BỘ ĐẾM =====

    private int tickCounter = 0;

    private int lowFpsTicks = 0;
    private int goodFpsTicks = 0;

    private boolean emergencyMode = false;

    @Override
    public void onInitializeClient() {

        ClientTickEvents.END_CLIENT_TICK.register(client -> {

            tickCounter++;

            // Kiểm tra khoảng mỗi 2 giây
            if (tickCounter < 40) {
                return;
            }

            tickCounter = 0;

            optimize(client);
        });

        System.out.println("[NV Extreme FPS] Extreme optimizer enabled!");
    }

    private void optimize(MinecraftClient client) {

        if (client.player == null) {
            return;
        }

        int fps = client.getCurrentFps();

        if (fps <= 0) {
            return;
        }

        int viewDistance =
                client.options.getViewDistance().getValue();

        int simulationDistance =
                client.options.getSimulationDistance().getValue();

        double entityDistance =
                client.options.getEntityDistanceScaling().getValue();

        // =====================================================
        // RAM
        // =====================================================

        Runtime runtime = Runtime.getRuntime();

        long maxMemory = runtime.maxMemory();
        long totalMemory = runtime.totalMemory();
        long freeMemory = runtime.freeMemory();

        long usedMemory = totalMemory - freeMemory;

        double memoryUsage =
                (double) usedMemory / (double) maxMemory;

        // =====================================================
        // FPS + RAM
        // =====================================================

        boolean memoryHigh = memoryUsage >= 0.85D;
        boolean memoryCritical = memoryUsage >= 0.92D;

        boolean fpsLow = fps < 25;
        boolean fpsCritical = fps < 18;

        // RAM cao hoặc FPS thấp
        if (fpsLow || memoryHigh) {

            lowFpsTicks++;
            goodFpsTicks = 0;

        } else if (fps >= 50 && !memoryHigh) {

            goodFpsTicks++;
            lowFpsTicks = 0;

        } else {

            // Vùng trung gian: không thay đổi cấu hình
            lowFpsTicks = 0;
            goodFpsTicks = 0;
        }

        // =====================================================
        // EMERGENCY MODE
        // =====================================================

        if (memoryCritical || fpsCritical) {

            emergencyMode = true;

            // CPU/GPU:
            // Không cho render distance tụt dưới 6
            if (viewDistance > MIN_VIEW_DISTANCE) {

                client.options.getViewDistance()
                        .setValue(MIN_VIEW_DISTANCE);
            }

            // Giảm lượng tick cần xử lý
            if (simulationDistance > MIN_SIMULATION_DISTANCE) {

                client.options.getSimulationDistance()
                        .setValue(MIN_SIMULATION_DISTANCE);
            }

            // Giảm entity rendering
            if (entityDistance > LOW_ENTITY_DISTANCE) {

                client.options.getEntityDistanceScaling()
                        .setValue(LOW_ENTITY_DISTANCE);
            }

            // Tắt bóng entity
            client.options.getEntityShadows()
                    .setValue(false);

            // Giảm particles
            client.options.getParticles()
                    .setValue(ParticlesMode.MINIMAL);

            System.out.println(
                    "[NV Extreme FPS] EMERGENCY MODE | FPS="
                            + fps
                            + " | RAM="
                            + (int) (memoryUsage * 100)
                            + "%"
            );

            return;
        }

        // =====================================================
        // LOW FPS MODE
        // =====================================================

        if (lowFpsTicks >= 2) {

            emergencyMode = false;

            // Giảm view distance từng bước
            // nhưng KHÔNG BAO GIỜ dưới 6
            if (viewDistance > MIN_VIEW_DISTANCE) {

                client.options.getViewDistance()
                        .setValue(
                                Math.max(
                                        MIN_VIEW_DISTANCE,
                                        viewDistance - 1
                                )
                        );
            }

            // Giảm simulation distance
            if (simulationDistance > MIN_SIMULATION_DISTANCE) {

                client.options.getSimulationDistance()
                        .setValue(
                                Math.max(
                                        MIN_SIMULATION_DISTANCE,
                                        simulationDistance - 1
                                )
                        );
            }

            // Giảm entity rendering
            if (entityDistance > LOW_ENTITY_DISTANCE) {

                client.options.getEntityDistanceScaling()
                        .setValue(LOW_ENTITY_DISTANCE);
            }

            // GPU
            client.options.getEntityShadows()
                    .setValue(false);

            // Particle
            client.options.getParticles()
                    .setValue(ParticlesMode.MINIMAL);

            lowFpsTicks = 0;

            System.out.println(
                    "[NV Extreme FPS] LOW FPS MODE | FPS="
                            + fps
            );

            return;
        }

        // =====================================================
        // NORMAL / HIGH FPS MODE
        // =====================================================

        if (goodFpsTicks >= 5) {

            emergencyMode = false;

            // FPS tốt -> tăng view distance từ từ
            if (viewDistance < MAX_VIEW_DISTANCE) {

                client.options.getViewDistance()
                        .setValue(viewDistance + 1);
            }

            // Tăng simulation distance trở lại
            if (simulationDistance < MAX_SIMULATION_DISTANCE) {

                client.options.getSimulationDistance()
                        .setValue(simulationDistance + 1);
            }

            // Entity rendering vừa phải
            if (entityDistance < NORMAL_ENTITY_DISTANCE) {

                client.options.getEntityDistanceScaling()
                        .setValue(NORMAL_ENTITY_DISTANCE);
            }

            // Vẫn giữ shadow OFF để ưu tiên FPS
            client.options.getEntityShadows()
                    .setValue(false);

            // Giữ particles ở mức thấp
            client.options.getParticles()
                    .setValue(ParticlesMode.MINIMAL);

            goodFpsTicks = 0;

            System.out.println(
                    "[NV Extreme FPS] PERFORMANCE RECOVERY | FPS="
                            + fps
            );
        }

        // =====================================================
        // BẢO VỆ CUỐI
        // =====================================================

        // Không bao giờ cho render distance xuống dưới 6
        int finalViewDistance =
                client.options.getViewDistance().getValue();

        if (finalViewDistance < MIN_VIEW_DISTANCE) {

            client.options.getViewDistance()
                    .setValue(MIN_VIEW_DISTANCE);
        }

        // Không cho simulation distance quá cao
        int finalSimulationDistance =
                client.options.getSimulationDistance().getValue();

        if (finalSimulationDistance > MAX_SIMULATION_DISTANCE) {

            client.options.getSimulationDistance()
                    .setValue(MAX_SIMULATION_DISTANCE);
        }
    }
}
