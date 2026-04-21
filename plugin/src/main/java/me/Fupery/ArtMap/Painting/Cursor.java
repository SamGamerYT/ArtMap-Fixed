package me.Fupery.ArtMap.Painting;

import me.Fupery.ArtMap.IO.PixelTableManager;
import org.bukkit.entity.Player;

class Cursor {

    private final float[] yawTable;
    private final Object[] pitchTables;
    private final int limit;
    private final int yawOffset;
    private int x, y;
    private float pitch, yaw;
    private float leftBound, rightBound, upBound, downBound;
    private boolean yawOffCanvas;
    private boolean pitchOffCanvas;

    private static final float BEDROCK_PITCH_CORRECTION = 20.0f;

    Cursor(int yawOffset, PixelTableManager pixelTable) {
        yawTable = pixelTable.getYawBounds();
        pitchTables = pixelTable.getPitchBounds();
        this.yawOffset = yawOffset;
        limit = (128 / pixelTable.getResolutionFactor()) - 1;
        yawOffCanvas = false;
        pitchOffCanvas = false;
        int mid = limit / 2;
        x = mid;
        y = mid;

        updateYawBounds();
        updatePitchBounds();
    }

    void setPitch(float pitch, Player player) {
        float finalPitch = pitch;

        if (isBedrockPlayer(player)) {
            finalPitch += BEDROCK_PITCH_CORRECTION;
        }

        if (Math.abs(this.pitch - finalPitch) > .0001) {
            this.pitch = finalPitch;
            updateYPos();
        }
    }

    void setYaw(float yaw) {
        if (Math.abs(this.yaw - yaw) > .0001) {
            this.yaw = yaw;
            updateXPos();
        }
    }

    private boolean isBedrockPlayer(Player player) {
        if (player == null) return false;
        try {
            Class<?> floodgateApi = Class.forName("org.geysermc.floodgate.api.FloodgateApi");
            Object api = floodgateApi.getMethod("getInstance").invoke(null);
            return (boolean) floodgateApi.getMethod("isFloodgatePlayer", java.util.UUID.class)
                    .invoke(api, player.getUniqueId());
        } catch (Exception e) {
            return false;
        }
    }

    private void updateXPos() {
        float yaw = getAdjustedYaw();
        while (yaw < leftBound && x > 0) {
            x--;
            updateYawBounds();
        }
        while (yaw > rightBound && x < limit) {
            x++;
            updateYawBounds();
        }
    }

    private void updateYPos() {
        float pitch = getAdjustedPitch();

        while (pitch < upBound && y > 0) {
            y--;
            updatePitchBounds();
        }
        while (pitch > downBound && y < limit) {
            y++;
            updatePitchBounds();
        }
    }

    private float getAdjustedYaw() {
        float yaw = this.yaw;
        float start = -180;
        float end = 180;

        float width = end - start;
        float offsetValue = yaw - start;

        yaw = (float) (offsetValue - (Math.floor(offsetValue / width) * width)) + start;

        yaw += (yaw > 0) ? -yawOffset : yawOffset;

        yawOffCanvas = (yaw > 45 || yaw < -45);
        return checkBounds(yaw);
    }

    private float getAdjustedPitch() {
        pitchOffCanvas = (pitch > 45 || pitch < -45);
        return checkBounds(pitch);
    }

    private float checkBounds(float value) {
        if (value > 40) return 40;
        if (value < -40) return -40;
        return value;
    }

    private void updateYawBounds() {
        leftBound = yawTable[x];
        rightBound = yawTable[x + 1];
        updatePitchBounds();
    }

    private void updatePitchBounds() {
        upBound = ((float[]) pitchTables[x])[y];
        downBound = ((float[]) pitchTables[x])[y + 1];
    }

    int getX() { return x; }
    int getY() { return y; }

    boolean isOffCanvas() {
        return yawOffCanvas || pitchOffCanvas;
    }
}