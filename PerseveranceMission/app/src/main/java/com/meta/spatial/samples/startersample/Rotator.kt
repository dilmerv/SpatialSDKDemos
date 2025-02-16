package com.meta.spatial.samples.startersample

import com.meta.spatial.core.ComponentCompanion
import com.meta.spatial.core.ComponentBase
import com.meta.spatial.core.FloatAttribute

class Rotator(speed: Float = 3f) : ComponentBase() {
  var speed by FloatAttribute("speed", R.string.Rotator_speed, this, speed)

  override fun typeID(): Int {
    return id
  }

  companion object : ComponentCompanion {
    override val id = R.id.Rotator_class
    override val createDefaultInstance = { Rotator() }
  }
}