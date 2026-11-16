package org.rsmod.content.skills.agility

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ObstacleSuccessTest {
    @Test
    fun `course level starts at the base success chance`() {
        assertEquals(BASE_OBSTACLE_SUCCESS, obstacleSuccessChance(40, 40, 64), 1e-9)
    }

    @Test
    fun `safe level and above never fail`() {
        assertEquals(1.0, obstacleSuccessChance(64, 40, 64))
        assertEquals(1.0, obstacleSuccessChance(99, 40, 64))
    }

    @Test
    fun `chance climbs linearly between course and safe level`() {
        val halfway = BASE_OBSTACLE_SUCCESS + (1.0 - BASE_OBSTACLE_SUCCESS) / 2
        assertEquals(halfway, obstacleSuccessChance(52, 40, 64), 1e-9)
    }

    @Test
    fun `boosted entry below course level keeps the base chance`() {
        assertEquals(BASE_OBSTACLE_SUCCESS, obstacleSuccessChance(85, 90, 95), 1e-9)
    }
}
