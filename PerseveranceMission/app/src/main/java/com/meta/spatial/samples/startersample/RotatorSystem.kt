package com.meta.spatial.samples.startersample

import com.meta.spatial.core.Quaternion
import com.meta.spatial.core.Query
import com.meta.spatial.core.SystemBase
import com.meta.spatial.toolkit.Transform
import kotlin.math.cos
import kotlin.math.sin

class RotatorSystem : SystemBase() {
  private var lastTime = System.currentTimeMillis()

  override fun execute() {
    // calculate delta time
    val currentTime = System.currentTimeMillis()
    val dt = ((currentTime - lastTime) / 1000f).coerceAtMost(0.1f)
    lastTime = currentTime

    val query = Query.where { has(Rotator.id, Transform.id) }
    for (entity in query.eval()) {
      val rotator = entity.getComponent<Rotator>()

      val entityTransform = entity.getComponent<Transform>()
      val pose = entityTransform.transform

      // calculate the rotation
      val halfAngle = (rotator.speed * dt * Math.PI / 180f).toFloat() / 2f
      val rotationStep = Quaternion(cos(halfAngle), 0f, sin(halfAngle), 0f)

      pose.q = (rotationStep * pose.q).normalize()
      entityTransform.transform = pose
      entity.setComponent(entityTransform)
    }
  }
}